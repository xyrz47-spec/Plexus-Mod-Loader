package dev.polymodloader.compat.fabric.mixin;

import java.lang.classfile.Annotation;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.Instruction;
import java.lang.classfile.Label;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.LoadInstruction;
import java.lang.classfile.instruction.NewReferenceArrayInstruction;
import java.lang.classfile.instruction.StackInstruction;
import java.lang.classfile.instruction.StoreInstruction;
import java.lang.classfile.instruction.TypeCheckInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Calls whose DataFixerUpper {@code Either} result a host's patch reads differently from the vanilla game: the left
 * side is an {@code Optional} of the value vanilla's call returns. MinecraftForge 26.3-66.1.0 wraps the registry element
 * decoder in {@code ConditionCodec.wrap}, so in {@code ResourceManagerRegistryLoadTask.lambda$load$2} the call {@code
 * RegistryLoadTask$PendingRegistration.loadFromResource} returns an {@code Either} whose left side is an empty {@code
 * Optional} for an element whose conditions are not met. The patch skips such an element, unwraps the others, and passes
 * them to {@code ForgeHooks.onJsonDataParsed}. Fabric API 0.165's {@code ResourceManagerRegistryLoadTaskMixin
 * .modifyLootTable} wraps the call and maps the left side as a {@code LootTable}, which stopped the game with a {@code
 * ClassCastException}.
 *
 * <p>A {@code @WrapOperation} of such a call runs through a bridge ({@link #bridgeMethod}) when its handler takes the
 * call's arguments and its {@code Operation}, and calls that {@code Operation} once with its own arguments: the bridge
 * makes the call; an element the patch skips is returned as it is without the handler, as under Fabric Loader such an
 * element is never decoded; otherwise the handler gets a {@link PrecomputedCall} that returns the result with the value
 * unwrapped, and the left side of the handler's result is wrapped in an {@code Optional} again, so the host's code after
 * the call (its condition check and hooks) sees the handler's value. Other injectors that see or replace the call's
 * result cannot work as written there.
 *
 * <p>Thread: any; no state.
 */
final class HostOptionalResults {

    static final String PREFIX = "poly$hostOptional$";
    static final String EITHER_NAME = "com/mojang/datafixers/util/Either";

    private static final ClassDesc EITHER = ClassDesc.ofInternalName(EITHER_NAME);
    private static final ClassDesc OPTIONAL = ClassDesc.of("java.util.Optional");
    private static final ClassDesc OPERATION =
            ClassDesc.ofDescriptor("Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;");
    private static final ClassDesc PRECOMPUTED_CALL = ClassDesc.of(PrecomputedCall.class.getName());

    private HostOptionalResults() {}

    /**
     * Whether one of {@code methods} reads the left side of a call {@code target} (Mixin's notation {@code
     * Lowner;name(descriptor)}) returning an {@code Either} as an {@code Optional}: right after the call, or from the
     * local it stores the result in, {@code left()}, {@code Optional.get()} and a cast to {@code Optional}.
     */
    static boolean readsLeftAsOptional(List<MethodModel> methods, String target) {
        if (!target.endsWith(")L" + EITHER_NAME + ";")) {
            return false;
        }
        for (MethodModel method : methods) {
            List<Instruction> code = instructions(method);
            for (int index = 0; index < code.size(); index++) {
                if (!HostPatchAdjustments.matches(code.get(index), target)) {
                    continue;
                }
                if (leftAsOptional(code, index + 1)) {
                    return true;
                }
                if (index + 1 < code.size() && code.get(index + 1) instanceof StoreInstruction store) {
                    for (int later = index + 2; later < code.size(); later++) {
                        if (code.get(later) instanceof LoadInstruction load
                                && load.slot() == store.slot()
                                && leftAsOptional(code, later + 1)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Why the bridge cannot carry the {@code @WrapOperation} handler of {@code target}, or empty when it can: the handler
     * takes exactly the call's (reference) arguments and its {@code Operation}, returns the call's type, calls the
     * {@code Operation} exactly once with an array of its own arguments in order, and never stores into them.
     */
    static Optional<String> unsupportedHandler(MethodModel handler, String target) {
        MethodTypeDesc call = MethodTypeDesc.ofDescriptor(target.substring(target.indexOf('(')));
        MethodTypeDesc type = handler.methodTypeSymbol();
        List<ClassDesc> expected = new ArrayList<>(call.parameterList());
        expected.add(OPERATION);
        if (!type.parameterList().equals(expected) || !type.returnType().equals(call.returnType())) {
            return Optional.of("the handler " + type.descriptorString() + " does not take exactly the call's arguments"
                    + " and its Operation");
        }
        if (call.parameterList().stream().anyMatch(ClassDesc::isPrimitive)) {
            return Optional.of("the call takes primitive arguments");
        }
        boolean isStatic = handler.flags().has(AccessFlag.STATIC);
        int[] slots = new int[type.parameterCount()];
        int slot = isStatic ? 0 : 1;
        for (int index = 0; index < slots.length; index++) {
            slots[index] = slot++;
        }
        List<Instruction> code = instructions(handler);
        List<Integer> calls = new ArrayList<>();
        for (int index = 0; index < code.size(); index++) {
            Instruction instruction = code.get(index);
            if (instruction instanceof StoreInstruction store && store.slot() < slot) {
                return Optional.of("the handler changes its parameter in slot " + store.slot());
            }
            if (instruction instanceof InvokeInstruction invoke
                    && invoke.owner().asSymbol().equals(OPERATION)
                    && invoke.name().equalsString("call")) {
                calls.add(index);
            }
        }
        if (calls.size() != 1) {
            return Optional.of("the handler calls its Operation " + calls.size() + " times, not once");
        }
        if (!callsWithOwnArguments(code, calls.getFirst(), slots)) {
            return Optional.of("the handler does not call its Operation with its own arguments");
        }
        return Optional.empty();
    }

    /**
     * Adds the bridge for {@code handler}: a method {@code poly$hostOptional$<handler>} with the handler's descriptor and
     * parameter annotations that carries {@code wrapOperation} (the handler's own, which the handler loses) and runs the
     * handler as the class comment says.
     */
    static void bridgeMethod(ClassBuilder builder, ClassModel mixin, MethodModel handler, Annotation wrapOperation) {
        MethodTypeDesc type = handler.methodTypeSymbol();
        int operation = type.parameterCount() - 1;
        int flags = handler.flags().flagsMask();
        boolean isStatic = (flags & ClassFile.ACC_STATIC) != 0;
        boolean isInterface = mixin.flags().has(AccessFlag.INTERFACE);
        ClassDesc owner = mixin.thisClass().asSymbol();
        String name = handler.methodName().stringValue();
        builder.withMethod(PREFIX + name, type, flags, method -> {
            method.with(RuntimeVisibleAnnotationsAttribute.of(wrapOperation));
            handler.findAttribute(Attributes.runtimeVisibleParameterAnnotations())
                    .ifPresent(method::with);
            handler.findAttribute(Attributes.runtimeInvisibleParameterAnnotations())
                    .ifPresent(method::with);
            method.withCode(code -> {
                int first = isStatic ? 0 : 1;
                int raw = code.allocateLocal(TypeKind.REFERENCE);
                int left = code.allocateLocal(TypeKind.REFERENCE);
                int value = code.allocateLocal(TypeKind.REFERENCE);
                int handed = code.allocateLocal(TypeKind.REFERENCE);
                int result = code.allocateLocal(TypeKind.REFERENCE);
                Label failed = code.newLabel();
                Label decoded = code.newLabel();
                Label callHandler = code.newLabel();
                Label notDecoded = code.newLabel();
                // The patched call, once.
                code.aload(first + operation);
                code.loadConstant(operation);
                code.anewarray(ConstantDescs.CD_Object);
                for (int index = 0; index < operation; index++) {
                    code.dup();
                    code.loadConstant(index);
                    code.aload(first + index);
                    code.aastore();
                }
                code.invokeinterface(
                        OPERATION,
                        "call",
                        MethodTypeDesc.of(ConstantDescs.CD_Object, ConstantDescs.CD_Object.arrayType()));
                code.checkcast(EITHER);
                code.astore(raw);
                code.aload(raw);
                code.invokevirtual(EITHER, "left", MethodTypeDesc.of(OPTIONAL));
                code.astore(left);
                code.aload(left);
                code.invokevirtual(OPTIONAL, "isPresent", MethodTypeDesc.of(ConstantDescs.CD_boolean));
                code.ifeq(failed);
                code.aload(left);
                code.invokevirtual(OPTIONAL, "get", MethodTypeDesc.of(ConstantDescs.CD_Object));
                code.checkcast(OPTIONAL);
                code.astore(value);
                code.aload(value);
                code.invokevirtual(OPTIONAL, "isPresent", MethodTypeDesc.of(ConstantDescs.CD_boolean));
                code.ifne(decoded);
                // The patch skips the element: nothing for the handler.
                code.aload(raw);
                code.areturn();
                code.labelBinding(decoded);
                code.aload(value);
                code.invokevirtual(OPTIONAL, "get", MethodTypeDesc.of(ConstantDescs.CD_Object));
                code.invokestatic(EITHER, "left", MethodTypeDesc.of(EITHER, ConstantDescs.CD_Object));
                code.astore(handed);
                code.goto_(callHandler);
                code.labelBinding(failed);
                code.aload(raw);
                code.astore(handed);
                code.labelBinding(callHandler);
                if (!isStatic) {
                    code.aload(0);
                }
                for (int index = 0; index < operation; index++) {
                    code.aload(first + index);
                }
                code.new_(PRECOMPUTED_CALL);
                code.dup();
                code.aload(handed);
                code.invokespecial(
                        PRECOMPUTED_CALL,
                        ConstantDescs.INIT_NAME,
                        MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_Object));
                if (isStatic) {
                    code.invokestatic(owner, name, type, isInterface);
                } else {
                    code.invokespecial(owner, name, type, isInterface);
                }
                code.astore(result);
                code.aload(result);
                code.invokevirtual(EITHER, "left", MethodTypeDesc.of(OPTIONAL));
                code.astore(left);
                code.aload(left);
                code.invokevirtual(OPTIONAL, "isPresent", MethodTypeDesc.of(ConstantDescs.CD_boolean));
                code.ifeq(notDecoded);
                code.aload(left);
                code.invokevirtual(OPTIONAL, "get", MethodTypeDesc.of(ConstantDescs.CD_Object));
                code.invokestatic(OPTIONAL, "of", MethodTypeDesc.of(OPTIONAL, ConstantDescs.CD_Object));
                code.invokestatic(EITHER, "left", MethodTypeDesc.of(EITHER, ConstantDescs.CD_Object));
                code.areturn();
                code.labelBinding(notDecoded);
                code.aload(result);
                code.areturn();
            });
        });
    }

    /** {@code left()}, {@code Optional.get()} and a cast to {@code Optional}, from {@code index} on. */
    private static boolean leftAsOptional(List<Instruction> code, int index) {
        return index + 2 < code.size()
                && code.get(index) instanceof InvokeInstruction left
                && left.owner().asInternalName().equals(EITHER_NAME)
                && left.name().equalsString("left")
                && left.type().equalsString("()Ljava/util/Optional;")
                && code.get(index + 1) instanceof InvokeInstruction get
                && get.owner().asSymbol().equals(OPTIONAL)
                && get.name().equalsString("get")
                && code.get(index + 2) instanceof TypeCheckInstruction cast
                && cast.opcode() == Opcode.CHECKCAST
                && cast.type().asSymbol().equals(OPTIONAL);
    }

    /**
     * Whether the {@code Operation.call} at {@code call} gets an array built from the handler's own parameters in order:
     * {@code aload <operation>, <size>, anewarray Object}, then for each element {@code dup, <index>, aload <parameter>,
     * aastore}.
     */
    private static boolean callsWithOwnArguments(List<Instruction> code, int call, int[] slots) {
        int arguments = slots.length - 1;
        int start = call - 3 - 4 * arguments;
        if (start < 0
                || !(code.get(start) instanceof LoadInstruction operation)
                || operation.slot() != slots[arguments]
                || !(code.get(start + 1) instanceof ConstantInstruction size)
                || !Integer.valueOf(arguments).equals(size.constantValue())
                || !(code.get(start + 2) instanceof NewReferenceArrayInstruction array)
                || !array.componentType().asSymbol().equals(ConstantDescs.CD_Object)) {
            return false;
        }
        for (int index = 0; index < arguments; index++) {
            int at = start + 3 + 4 * index;
            if (!(code.get(at) instanceof StackInstruction dup)
                    || dup.opcode() != Opcode.DUP
                    || !(code.get(at + 1) instanceof ConstantInstruction position)
                    || !Integer.valueOf(index).equals(position.constantValue())
                    || !(code.get(at + 2) instanceof LoadInstruction load)
                    || load.slot() != slots[index]
                    || code.get(at + 3).opcode() != Opcode.AASTORE) {
                return false;
            }
        }
        return true;
    }

    private static List<Instruction> instructions(MethodModel method) {
        List<Instruction> found = new ArrayList<>();
        method.code().ifPresent(code -> {
            for (CodeElement element : code) {
                if (element instanceof Instruction instruction) {
                    found.add(instruction);
                }
            }
        });
        return found;
    }
}
