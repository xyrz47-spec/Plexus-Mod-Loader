package dev.plexusmodloader.compat.fabric.mixin;

import java.lang.classfile.Annotation;
import java.lang.classfile.AnnotationElement;
import java.lang.classfile.AnnotationValue;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.Instruction;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;
import java.lang.classfile.attribute.RuntimeInvisibleParameterAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeVisibleParameterAnnotationsAttribute;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.LoadInstruction;
import java.lang.classfile.instruction.NewReferenceArrayInstruction;
import java.lang.classfile.instruction.StackInstruction;
import java.lang.classfile.instruction.StoreInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A {@code @WrapOperation} of an instance call that the host's patch replaced, at the same place, with another call on
 * the same receiver type with the same result, whose arguments take the vanilla ones' places ({@link #mapping}) ({@link
 * dev.plexusmodloader.compat.bridge.InjectionPointReplacement.Placement#SAME_PLACE}): MinecraftForge's {@code
 * ItemStack.canPerformAction(ToolActions.FISHING_ROD_CAST)} for vanilla's {@code ItemStack.is(Items.FISHING_ROD)} in
 * {@code FishingHook.shouldStopFishing}, which Fabric API's {@code allowModdedRods} wraps to let fishing rod items of
 * other mods keep the hook; NeoForge's {@code BlockState.getCloneItemStack(BlockPos, LevelReader, boolean, Player)} for
 * vanilla's {@code getCloneItemStack(LevelReader, BlockPos, boolean)} in {@code
 * ServerGamePacketListenerImpl.handlePickItemFromBlock}, which Fabric API's {@code onPickItemFromBlock} wraps.
 *
 * <p>The bridge ({@link #bridgeMethod}) wraps the host's call: it makes it, then calls the handler with the receiver,
 * the host's arguments in place of vanilla's (each fits the handler's parameter type) and a {@link PrecomputedCall}
 * that returns the host's result, and returns what the handler returns. The handler thus decides as written, with the
 * host's answer where it asked the game's. Parameters the handler takes after its {@code Operation} (the target
 * method's) pass through with their annotations (MixinExtras sugar such as {@code @Local(argsOnly = true)}); a handler
 * with annotations on its other parameters is not bridged ({@link #fits}).
 *
 * <p>Thread: any; no state.
 */
final class HostReplacedCalls {

    static final String PREFIX = "plexus$hostCall$";

    private static final String WRAP_OPERATION = "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;";
    private static final String AT = "Lorg/spongepowered/asm/mixin/injection/At;";
    private static final ClassDesc OPERATION =
            ClassDesc.ofDescriptor("Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;");
    private static final ClassDesc PRECOMPUTED_CALL = ClassDesc.of(PrecomputedCall.class.getName());

    private HostReplacedCalls() {}

    /** A call in Mixin's notation {@code Lowner;name(arguments)result}. */
    record Call(String owner, String name, MethodTypeDesc type) {
        static Optional<Call> parse(String target) {
            int end = target.indexOf(';');
            int open = target.indexOf('(');
            if (!target.startsWith("L") || end < 0 || open < end) {
                return Optional.empty();
            }
            try {
                return Optional.of(new Call(
                        target.substring(0, end + 1),
                        target.substring(end + 1, open),
                        MethodTypeDesc.ofDescriptor(target.substring(open))));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }
    }

    /**
     * Whether a {@code @WrapOperation} handler {@code handler} of the instance call {@code replaced} can run through
     * the bridge at {@code replacement}: both on the same owner with the same result, and the handler taking the
     * receiver, one parameter per vanilla argument that takes one of the host's arguments ({@link #mapping}), the
     * {@code Operation}, and maybe the target method's parameters after it; and the handler making the wrapped call once,
     * as given ({@link #callsOnceWithOwnArguments}).
     */
    static boolean fits(MethodModel handler, String type, String replaced, String replacement) {
        if (!type.equals(WRAP_OPERATION)) {
            return false;
        }
        Optional<Call> vanilla = Call.parse(replaced);
        Optional<Call> host = Call.parse(replacement);
        if (vanilla.isEmpty()
                || host.isEmpty()
                || !vanilla.get().owner().equals(host.get().owner())
                || !vanilla.get().type().returnType().equals(host.get().type().returnType())) {
            return false;
        }
        MethodTypeDesc handlerType = handler.methodTypeSymbol();
        int arguments = vanilla.get().type().parameterCount();
        if (annotatedBefore(handler, arguments + 2)) {
            // The bridge moves the receiver and the call's arguments; only the parameters after the Operation (the
            // target method's, maybe with MixinExtras sugar such as @Local(argsOnly = true)) keep their annotations.
            return false;
        }
        if (handlerType.parameterCount() < arguments + 2
                || !handlerType.returnType().equals(host.get().type().returnType())
                || !handlerType
                        .parameterType(0)
                        .descriptorString()
                        .equals(vanilla.get().owner())
                || !handlerType.parameterType(arguments + 1).equals(OPERATION)) {
            return false;
        }
        return mapping(handlerType, vanilla.get().type(), host.get().type()).isPresent()
                && callsOnceWithOwnArguments(handler, arguments + 1);
    }

    /**
     * Whether {@code handler} calls its {@code Operation} (the parameter after its first {@code callArguments}: the
     * receiver and the vanilla arguments) at one place only, with an array of those parameters in order (each boxed if
     * primitive), and never stores into them. The bridge makes the host's call before the handler runs, so it carries
     * only a handler that makes the call it wraps as given, as {@link HostOptionalResults} does.
     */
    static boolean callsOnceWithOwnArguments(MethodModel handler, int callArguments) {
        MethodTypeDesc type = handler.methodTypeSymbol();
        boolean isStatic = handler.flags().has(AccessFlag.STATIC);
        int[] slots = new int[callArguments + 1];
        int slot = isStatic ? 0 : 1;
        for (int index = 0; index <= callArguments; index++) {
            slots[index] = slot;
            slot += TypeKind.from(type.parameterType(index)).slotSize();
        }
        List<Instruction> code = new ArrayList<>();
        handler.code().ifPresent(body -> {
            for (CodeElement element : body) {
                if (element instanceof Instruction instruction) {
                    code.add(instruction);
                }
            }
        });
        int call = -1;
        for (int index = 0; index < code.size(); index++) {
            Instruction instruction = code.get(index);
            if (instruction instanceof StoreInstruction store && store.slot() < slot) {
                return false;
            }
            if (instruction instanceof InvokeInstruction invoke
                    && invoke.owner().asSymbol().equals(OPERATION)
                    && invoke.name().equalsString("call")) {
                if (call >= 0) {
                    return false;
                }
                call = index;
            }
        }
        if (call < 0) {
            return false;
        }
        // Backwards from the call: per argument "dup, <index>, load <slot>, [box,] aastore", after "aload <operation>,
        // <count>, anewarray Object".
        int at = call - 1;
        for (int index = callArguments - 1; index >= 0; index--) {
            if (at < 0 || code.get(at).opcode() != Opcode.AASTORE) {
                return false;
            }
            at--;
            if (type.parameterType(index).isPrimitive()) {
                if (!(code.get(at) instanceof InvokeInstruction box)
                        || !box.name().equalsString("valueOf")) {
                    return false;
                }
                at--;
            }
            if (at < 2
                    || !(code.get(at) instanceof LoadInstruction load)
                    || load.slot() != slots[index]
                    || !(code.get(at - 1) instanceof ConstantInstruction position)
                    || !Integer.valueOf(index).equals(position.constantValue())
                    || !(code.get(at - 2) instanceof StackInstruction dup)
                    || dup.opcode() != Opcode.DUP) {
                return false;
            }
            at -= 3;
        }
        return at >= 2
                && code.get(at) instanceof NewReferenceArrayInstruction array
                && array.componentType().asSymbol().equals(ConstantDescs.CD_Object)
                && code.get(at - 1) instanceof ConstantInstruction size
                && Integer.valueOf(callArguments).equals(size.constantValue())
                && code.get(at - 2) instanceof LoadInstruction operation
                && operation.slot() == slots[callArguments];
    }

    /** Whether one of {@code handler}'s first {@code count} parameters has an annotation. */
    static boolean annotatedBefore(MethodModel handler, int count) {
        List<List<Annotation>> visible = handler.findAttribute(Attributes.runtimeVisibleParameterAnnotations())
                .map(attribute -> attribute.parameterAnnotations())
                .orElse(List.of());
        List<List<Annotation>> invisible = handler.findAttribute(Attributes.runtimeInvisibleParameterAnnotations())
                .map(attribute -> attribute.parameterAnnotations())
                .orElse(List.of());
        for (int index = 0; index < count; index++) {
            if ((index < visible.size() && !visible.get(index).isEmpty())
                    || (index < invisible.size() && !invisible.get(index).isEmpty())) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@code annotations} of a handler with {@code vanillaArguments} call arguments, placed for a bridge of {@code
     * count} parameters: the receiver, the host's arguments and the {@code Operation} have none, and the parameters after
     * the {@code Operation} keep theirs.
     */
    private static List<List<Annotation>> placed(
            List<List<Annotation>> annotations, int vanillaArguments, int count, int trailing) {
        List<List<Annotation>> placed = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            placed.add(List.of());
        }
        for (int index = 0; index < trailing; index++) {
            int from = vanillaArguments + 2 + index;
            if (from < annotations.size()) {
                placed.set(count - trailing + index, annotations.get(from));
            }
        }
        return placed;
    }

    /**
     * For each vanilla argument, the index of the host's argument the handler gets in its place: the same position when
     * both calls have as many arguments, all references, and each handler parameter is {@code Object} or the host's
     * type ({@code canPerformAction(ToolAction)} for {@code is(Object)}); otherwise the one host argument of exactly the
     * handler parameter's type ({@code getCloneItemStack(BlockPos, LevelReader, boolean, Player)} for {@code
     * getCloneItemStack(LevelReader, BlockPos, boolean)}). Empty when neither fits.
     */
    static Optional<int[]> mapping(MethodTypeDesc handlerType, MethodTypeDesc vanillaType, MethodTypeDesc hostType) {
        int arguments = vanillaType.parameterCount();
        int[] positions = new int[arguments];
        if (hostType.parameterCount() == arguments) {
            boolean positional = true;
            for (int index = 0; index < arguments; index++) {
                ClassDesc hostArgument = hostType.parameterType(index);
                ClassDesc parameter = handlerType.parameterType(index + 1);
                positions[index] = index;
                if (hostArgument.isPrimitive()
                        || vanillaType.parameterType(index).isPrimitive()
                        || !(parameter.equals(ConstantDescs.CD_Object) || parameter.equals(hostArgument))) {
                    positional = false;
                }
            }
            if (positional) {
                return Optional.of(positions);
            }
        }
        boolean[] used = new boolean[hostType.parameterCount()];
        for (int index = 0; index < arguments; index++) {
            ClassDesc parameter = handlerType.parameterType(index + 1);
            int found = -1;
            for (int candidate = 0; candidate < hostType.parameterCount(); candidate++) {
                if (hostType.parameterType(candidate).equals(parameter)) {
                    if (found >= 0) {
                        return Optional.empty();
                    }
                    found = candidate;
                }
            }
            if (found < 0 || used[found] || !vanillaType.parameterType(index).equals(parameter)) {
                return Optional.empty();
            }
            used[found] = true;
            positions[index] = found;
        }
        return Optional.of(positions);
    }

    /**
     * Adds the bridge of {@code handler} to {@code mixin}: {@code wrapOperation} (the handler's) at {@code replacement}
     * in place of {@code replaced}, taking the receiver, the host's arguments, the {@code Operation} and the handler's
     * parameters after its {@code Operation}. The handler stays as a plain method.
     */
    static void bridgeMethod(
            ClassBuilder builder,
            ClassModel mixin,
            MethodModel handler,
            Annotation wrapOperation,
            String replaced,
            String replacement) {
        MethodTypeDesc vanillaType = Call.parse(replaced).orElseThrow().type();
        MethodTypeDesc hostType = Call.parse(replacement).orElseThrow().type();
        MethodTypeDesc handlerType = handler.methodTypeSymbol();
        int[] positions = mapping(handlerType, vanillaType, hostType).orElseThrow();
        int vanillaArguments = vanillaType.parameterCount();
        int hostArguments = hostType.parameterCount();
        List<ClassDesc> trailing =
                handlerType.parameterList().subList(vanillaArguments + 2, handlerType.parameterCount());
        List<ClassDesc> parameters = new ArrayList<>();
        parameters.add(handlerType.parameterType(0));
        parameters.addAll(hostType.parameterList());
        parameters.add(OPERATION);
        parameters.addAll(trailing);
        MethodTypeDesc bridgeType = MethodTypeDesc.of(handlerType.returnType(), parameters);
        int flags = handler.flags().flagsMask();
        boolean isStatic = (flags & ClassFile.ACC_STATIC) != 0;
        boolean isInterface = mixin.flags().has(AccessFlag.INTERFACE);
        ClassDesc owner = mixin.thisClass().asSymbol();
        String name = handler.methodName().stringValue();
        // The bridge's slots: receiver, host arguments, Operation, trailing parameters.
        int[] slots = new int[parameters.size()];
        int next = isStatic ? 0 : 1;
        for (int index = 0; index < parameters.size(); index++) {
            slots[index] = next;
            next += TypeKind.from(parameters.get(index)).slotSize();
        }
        int operationSlot = slots[1 + hostArguments];
        builder.withMethod(PREFIX + name, bridgeType, flags, method -> {
            method.with(RuntimeVisibleAnnotationsAttribute.of(retargeted(wrapOperation, replaced, replacement)));
            handler.findAttribute(Attributes.runtimeVisibleParameterAnnotations())
                    .ifPresent(attribute -> method.with(RuntimeVisibleParameterAnnotationsAttribute.of(placed(
                            attribute.parameterAnnotations(), vanillaArguments, parameters.size(), trailing.size()))));
            handler.findAttribute(Attributes.runtimeInvisibleParameterAnnotations())
                    .ifPresent(attribute -> method.with(RuntimeInvisibleParameterAnnotationsAttribute.of(placed(
                            attribute.parameterAnnotations(), vanillaArguments, parameters.size(), trailing.size()))));
            method.withCode(code -> {
                int result = code.allocateLocal(TypeKind.REFERENCE);
                // The host's call, once: the receiver and the host's arguments.
                code.aload(operationSlot);
                code.loadConstant(hostArguments + 1);
                code.anewarray(ConstantDescs.CD_Object);
                for (int index = 0; index <= hostArguments; index++) {
                    code.dup();
                    code.loadConstant(index);
                    loadBoxed(code, parameters.get(index), slots[index]);
                    code.aastore();
                }
                code.invokeinterface(
                        OPERATION,
                        "call",
                        MethodTypeDesc.of(ConstantDescs.CD_Object, ConstantDescs.CD_Object.arrayType()));
                code.astore(result);
                // The handler: the receiver, the host's arguments in the vanilla ones' places, the result, the rest.
                if (!isStatic) {
                    code.aload(0);
                }
                code.aload(slots[0]);
                for (int index = 0; index < vanillaArguments; index++) {
                    int host = 1 + positions[index];
                    code.loadLocal(TypeKind.from(parameters.get(host)), slots[host]);
                }
                code.new_(PRECOMPUTED_CALL);
                code.dup();
                code.aload(result);
                code.invokespecial(
                        PRECOMPUTED_CALL,
                        ConstantDescs.INIT_NAME,
                        MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_Object));
                for (int index = 0; index < trailing.size(); index++) {
                    int at = 2 + hostArguments + index;
                    code.loadLocal(TypeKind.from(parameters.get(at)), slots[at]);
                }
                if (isStatic) {
                    code.invokestatic(owner, name, handlerType, isInterface);
                } else {
                    code.invokespecial(owner, name, handlerType, isInterface);
                }
                code.return_(TypeKind.from(handlerType.returnType()));
            });
        });
    }

    /** Loads the local {@code slot} of {@code type} as an {@code Object}, boxing a primitive. */
    private static void loadBoxed(java.lang.classfile.CodeBuilder code, ClassDesc type, int slot) {
        code.loadLocal(TypeKind.from(type), slot);
        if (type.isPrimitive()) {
            ClassDesc box = switch (type.descriptorString()) {
                case "Z" -> ConstantDescs.CD_Boolean;
                case "B" -> ConstantDescs.CD_Byte;
                case "C" -> ConstantDescs.CD_Character;
                case "S" -> ConstantDescs.CD_Short;
                case "I" -> ConstantDescs.CD_Integer;
                case "J" -> ConstantDescs.CD_Long;
                case "F" -> ConstantDescs.CD_Float;
                default -> ConstantDescs.CD_Double;
            };
            code.invokestatic(box, "valueOf", MethodTypeDesc.of(box, type));
        }
    }

    /** {@code annotation} with its {@code @At} targets {@code replaced} naming {@code replacement}. */
    static Annotation retargeted(Annotation annotation, String replaced, String replacement) {
        List<AnnotationElement> elements = new ArrayList<>();
        for (AnnotationElement element : annotation.elements()) {
            elements.add(AnnotationElement.of(
                    element.name().stringValue(), retargeted(element.value(), replaced, replacement)));
        }
        return Annotation.of(annotation.classSymbol(), elements);
    }

    private static AnnotationValue retargeted(AnnotationValue value, String replaced, String replacement) {
        if (value instanceof AnnotationValue.OfArray array) {
            return AnnotationValue.ofArray(array.values().stream()
                    .map(item -> retargeted(item, replaced, replacement))
                    .toList());
        }
        if (value instanceof AnnotationValue.OfAnnotation nested
                && nested.annotation().className().equalsString(AT)) {
            List<AnnotationElement> elements = new ArrayList<>();
            for (AnnotationElement element : nested.annotation().elements()) {
                if (element.name().equalsString("target")
                        && element.value() instanceof AnnotationValue.OfString target
                        && target.stringValue().equals(replaced)) {
                    elements.add(AnnotationElement.ofString("target", replacement));
                } else {
                    elements.add(element);
                }
            }
            return AnnotationValue.ofAnnotation(
                    Annotation.of(nested.annotation().classSymbol(), elements));
        }
        return value;
    }
}
