package dev.polymodloader.compat.fabric.mixin;

import java.lang.classfile.Annotation;
import java.lang.classfile.AnnotationElement;
import java.lang.classfile.AnnotationValue;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.Instruction;
import java.lang.classfile.Label;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;
import java.lang.classfile.attribute.RuntimeInvisibleParameterAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeVisibleParameterAnnotationsAttribute;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.LoadInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Fabric API's resource conditions in {@code SimpleJsonResourceReloadListener.scanDirectory} on NeoForge (26.2.0.93,
 * checked with {@code javap}). Fabric API's {@code @WrapOperation} of {@code Codec.parse} returns {@code
 * DataResult.success(SKIP_DATA_MARKER)} for a file whose Fabric conditions fail, and its {@code @Inject} at the head of
 * the store lambda {@code lambda$scanDirectory$0(Map, Identifier, Object)} cancels for that marker. NeoForge wraps the
 * codec with {@code ConditionalOps.createConditionalCodec}, so the parse gives an {@code Optional}, and its store lambda
 * {@code (Identifier, Identifier, Map, Optional)} skips an empty one: there the marker would reach a cast to {@code
 * Optional}.
 *
 * <p>The parse handler runs through a bridge with its own parameters (and their MixinExtras sugar) that turns the
 * marker into {@code DataResult.success(Optional.empty())}, NeoForge's skip ({@link #companionBridge}); the head
 * handler, whose only effect is that skip, is dropped. Both handlers' code is checked first ({@link #companion}).
 *
 * <p>Thread: any; no state.
 */
final class HostConditionalSkip {

    /** {@link HostPatchAdjustments}' {@code ReplacedCall} marks: the dropped head handler and the bridged parse one. */
    static final String SKIP = "poly:conditional-skip";

    static final String COMPANION = "poly:conditional-skip-parse";

    private static final String TARGET_CLASS = "net/minecraft/server/packs/resources/SimpleJsonResourceReloadListener";
    private static final String LAMBDA = "lambda$scanDirectory$0";
    private static final String VANILLA_LAMBDA =
            "(Ljava/util/Map;Lnet/minecraft/resources/Identifier;Ljava/lang/Object;)V";
    private static final String HOST_LAMBDA =
            "(Lnet/minecraft/resources/Identifier;Lnet/minecraft/resources/Identifier;"
                    + "Ljava/util/Map;Ljava/util/Optional;)V";
    private static final String PARSE =
            "Lcom/mojang/serialization/Codec;parse(Lcom/mojang/serialization/DynamicOps;Ljava/lang/Object;)"
                    + "Lcom/mojang/serialization/DataResult;";
    private static final String CONDITIONAL = "Lnet/neoforged/neoforge/common/conditions/ConditionalOps;"
            + "createConditionalCodec(Lcom/mojang/serialization/Codec;)Lcom/mojang/serialization/Codec;";
    private static final String INJECT = "Lorg/spongepowered/asm/mixin/injection/Inject;";
    private static final String WRAP_OPERATION = "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;";
    private static final ClassDesc DATA_RESULT = ClassDesc.of("com.mojang.serialization.DataResult");
    private static final ClassDesc OPTIONAL = ClassDesc.of("java.util.Optional");
    private static final String SKIP_HANDLER = "(Ljava/util/Map;Lnet/minecraft/resources/Identifier;Ljava/lang/Object;"
            + "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V";

    private HostConditionalSkip() {}

    /**
     * The parse handler a dropped head handler leans on: its name, descriptor, the marker field both compare to, and
     * whether the patch has NeoForge's {@code scanDirectoryWithModifier} (recipes), which needs a second bridge.
     */
    record Companion(String name, String descriptor, String marker, boolean withModifier) {}

    /** The suffix of a {@code ReplacedCall}'s marker when the patch has {@code scanDirectoryWithModifier}. */
    static final String MODIFIER_FLAG = "+withModifier";

    private static final String WITH_MODIFIER = "scanDirectoryWithModifier";
    private static final String WITH_MODIFIER_TYPE = "(Lnet/minecraft/server/packs/resources/ResourceManager;"
            + "Lnet/minecraft/resources/FileToIdConverter;Lcom/mojang/serialization/DynamicOps;"
            + "Lcom/mojang/serialization/Codec;Ljava/util/Map;Ljava/util/function/Consumer;)V";
    private static final String WITH_MODIFIER_LAMBDA = "lambda$scanDirectoryWithModifier$0";
    private static final String WITH_MODIFIER_LAMBDA_TYPE =
            "(Lnet/minecraft/resources/Identifier;Ljava/util/Map;Ljava/util/Optional;)V";
    private static final String PARSE_HANDLER = "(Lcom/mojang/serialization/Codec;Lcom/mojang/serialization/DynamicOps;"
            + "Ljava/lang/Object;Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;"
            + "Lnet/minecraft/resources/FileToIdConverter;Ljava/util/Map$Entry;)Lcom/mojang/serialization/DataResult;";
    private static final ClassDesc MAP = ClassDesc.of("java.util.Map");
    private static final ClassDesc MAP_ENTRY = ClassDesc.of("java.util.Map$Entry");
    private static final ClassDesc IDENTIFIER = ClassDesc.of("net.minecraft.resources.Identifier");
    private static final ClassDesc FILE_TO_ID = ClassDesc.of("net.minecraft.resources.FileToIdConverter");

    /**
     * The parse handler of {@code mixin} when {@code handler} is the head handler this class drops: a static {@code
     * @Inject} into the store lambda that only cancels when the value is a static field of the mixin; the patch gives the
     * lambda NeoForge's parameters and skips an empty {@code Optional} first; {@code scanDirectory} parses with NeoForge's
     * conditional codec; and the mixin's {@code @WrapOperation} of that parse returns {@code DataResult.success} of the
     * same field ({@link #parseFits}).
     */
    static Optional<Companion> companion(
            ClassModel mixin,
            MethodModel handler,
            String type,
            String targetClass,
            MethodModel vanillaLambda,
            MethodModel patchedLambda,
            ClassModel patched) {
        if (!type.equals(INJECT)
                || !targetClass.equals(TARGET_CLASS)
                || !vanillaLambda.methodName().equalsString(LAMBDA)
                || !vanillaLambda.methodType().equalsString(VANILLA_LAMBDA)
                || !patchedLambda.methodType().equalsString(HOST_LAMBDA)
                || !skipsEmpty(patchedLambda)
                || !handler.flags().has(AccessFlag.STATIC)
                || !handler.methodType().equalsString(SKIP_HANDLER)
                || HostPatchAdjustments.methods(patched, "scanDirectory", null).stream()
                        .noneMatch(found -> HostPatchAdjustments.contains(found, CONDITIONAL)
                                && HostPatchAdjustments.contains(found, PARSE))) {
            return Optional.empty();
        }
        Optional<String> marker = cancelsOnField(handler, mixin.thisClass().asSymbol());
        if (marker.isEmpty()) {
            return Optional.empty();
        }
        List<MethodModel> withModifier = HostPatchAdjustments.methods(patched, WITH_MODIFIER, null);
        boolean modifierFits = withModifier.stream()
                        .allMatch(found -> found.methodType().equalsString(WITH_MODIFIER_TYPE)
                                && HostPatchAdjustments.contains(found, CONDITIONAL)
                                && HostPatchAdjustments.contains(found, PARSE))
                && (withModifier.isEmpty()
                        || HostPatchAdjustments.methods(patched, WITH_MODIFIER_LAMBDA, WITH_MODIFIER_LAMBDA_TYPE)
                                .stream()
                                .anyMatch(lambda -> skipsEmpty(lambda, 2)));
        if (!modifierFits) {
            // Recipes would load past Fabric's conditions: the mod cannot run as written.
            return Optional.empty();
        }
        return mixin.methods().stream()
                .filter(method -> parseFits(method, mixin.thisClass().asSymbol(), marker.get()))
                .filter(method -> withModifier.isEmpty() || entryOnlyForItsKey(method))
                .findFirst()
                .map(method -> new Companion(
                        method.methodName().stringValue(),
                        method.methodType().stringValue(),
                        marker.get(),
                        !withModifier.isEmpty()));
    }

    /**
     * Whether the parse handler has Fabric API's parameters and uses its {@code Map.Entry} (slot 5) only for {@code
     * getKey()}, the file's id it logs: the recipe bridge hands it an entry of that id.
     */
    private static boolean entryOnlyForItsKey(MethodModel handler) {
        if (!handler.methodType().equalsString(PARSE_HANDLER)) {
            return false;
        }
        List<Instruction> code = instructions(handler);
        for (int index = 0; index < code.size(); index++) {
            if (code.get(index) instanceof LoadInstruction load
                    && load.slot() == 5
                    && !(index + 1 < code.size()
                            && code.get(index + 1) instanceof InvokeInstruction getKey
                            && getKey.owner().asSymbol().equals(MAP_ENTRY)
                            && getKey.name().equalsString("getKey"))) {
                return false;
            }
        }
        return true;
    }

    /** Whether NeoForge's store lambda starts by testing its {@code Optional} (slot 3) for empty. */
    private static boolean skipsEmpty(MethodModel lambda) {
        return skipsEmpty(lambda, 3);
    }

    /** Whether a store lambda starts by testing its {@code Optional} (in {@code slot}) for empty. */
    private static boolean skipsEmpty(MethodModel lambda, int slot) {
        List<Instruction> code = instructions(lambda);
        return code.size() > 2
                && code.get(0) instanceof LoadInstruction load
                && load.slot() == slot
                && code.get(1) instanceof InvokeInstruction isEmpty
                && isEmpty.owner().asSymbol().equals(OPTIONAL)
                && isEmpty.name().equalsString("isEmpty");
    }

    /** The static field of {@code owner} the handler compares its value (slot 2) to before it cancels, and nothing else. */
    private static Optional<String> cancelsOnField(MethodModel handler, ClassDesc owner) {
        List<Instruction> code = instructions(handler);
        if (code.size() != 6
                || !(code.get(0) instanceof LoadInstruction value && value.slot() == 2)
                || !(code.get(1) instanceof FieldInstruction field
                        && field.opcode() == Opcode.GETSTATIC
                        && field.owner().asSymbol().equals(owner))
                || code.get(2).opcode() != Opcode.IF_ACMPNE
                || !(code.get(3) instanceof LoadInstruction callback && callback.slot() == 3)
                || !(code.get(4) instanceof InvokeInstruction cancel
                        && cancel.name().equalsString("cancel"))
                || code.get(5).opcode() != Opcode.RETURN) {
            return Optional.empty();
        }
        return Optional.of(field.name().stringValue());
    }

    /**
     * Whether {@code method} is a static {@code @WrapOperation} of {@code Codec.parse} returning {@code DataResult}
     * whose only use of {@code marker} is {@code DataResult.success(marker)}, and that calls its {@code Operation}.
     */
    private static boolean parseFits(MethodModel method, ClassDesc owner, String marker) {
        boolean wraps = HostPatchAdjustments.annotation(method.attributes(), WRAP_OPERATION)
                .map(annotation -> targets(annotation).contains(PARSE))
                .orElse(false);
        if (!wraps
                || !method.flags().has(AccessFlag.STATIC)
                || !method.methodTypeSymbol().returnType().equals(DATA_RESULT)) {
            return false;
        }
        List<Instruction> code = instructions(method);
        boolean success = false;
        boolean calls = false;
        for (int index = 0; index < code.size(); index++) {
            Instruction instruction = code.get(index);
            if (instruction instanceof FieldInstruction field
                    && field.owner().asSymbol().equals(owner)
                    && field.name().equalsString(marker)) {
                if (field.opcode() != Opcode.GETSTATIC
                        || index + 1 >= code.size()
                        || !(code.get(index + 1) instanceof InvokeInstruction wrap
                                && wrap.owner().asSymbol().equals(DATA_RESULT)
                                && wrap.name().equalsString("success"))) {
                    return false;
                }
                success = true;
            }
            if (instruction instanceof InvokeInstruction invoke && invoke.name().equalsString("call")) {
                calls = true;
            }
        }
        return success && calls;
    }

    /**
     * Adds {@code poly$hostCall$<companion>} with the parse handler's parameters and their annotations, carrying its
     * {@code wrapOperation}: it calls the handler and turns {@code DataResult.success(marker)} into {@code
     * DataResult.success(Optional.empty())}.
     */
    static void companionBridge(
            ClassBuilder builder, ClassModel mixin, MethodModel handler, Annotation wrapOperation, String marker) {
        boolean withModifier = marker.endsWith(MODIFIER_FLAG);
        String field = withModifier ? marker.substring(0, marker.length() - MODIFIER_FLAG.length()) : marker;
        parseBridge(builder, mixin, handler, wrapOperation, field);
        if (withModifier) {
            recipeBridge(builder, mixin, handler, wrapOperation, field);
        }
    }

    private static void parseBridge(
            ClassBuilder builder, ClassModel mixin, MethodModel handler, Annotation wrapOperation, String marker) {
        ClassDesc owner = mixin.thisClass().asSymbol();
        boolean isInterface = mixin.flags().has(AccessFlag.INTERFACE);
        MethodTypeDesc type = handler.methodTypeSymbol();
        builder.withMethod(
                HostReplacedCalls.PREFIX + handler.methodName().stringValue(),
                type,
                handler.flags().flagsMask(),
                method -> {
                    method.with(RuntimeVisibleAnnotationsAttribute.of(wrapOperation));
                    handler.findAttribute(Attributes.runtimeVisibleParameterAnnotations())
                            .ifPresent(attribute -> method.with(
                                    RuntimeVisibleParameterAnnotationsAttribute.of(attribute.parameterAnnotations())));
                    handler.findAttribute(Attributes.runtimeInvisibleParameterAnnotations())
                            .ifPresent(attribute -> method.with(RuntimeInvisibleParameterAnnotationsAttribute.of(
                                    attribute.parameterAnnotations())));
                    method.withCode(code -> {
                        int slot = 0;
                        for (ClassDesc parameter : type.parameterList()) {
                            code.loadLocal(TypeKind.from(parameter), slot);
                            slot += TypeKind.from(parameter).slotSize();
                        }
                        code.invokestatic(owner, handler.methodName().stringValue(), type, isInterface);
                        markerToEmpty(code, owner, marker);
                    });
                });
    }

    /**
     * {@code poly$hostCall$<handler>$withModifier}: the same wrap of the parse in NeoForge's {@code
     * scanDirectoryWithModifier}, where each entry maps a resource's id to its JSON; the handler gets an entry of the
     * file's id ({@code converter.idToFile(id)}), the one key it reads.
     */
    private static void recipeBridge(
            ClassBuilder builder, ClassModel mixin, MethodModel handler, Annotation wrapOperation, String marker) {
        ClassDesc owner = mixin.thisClass().asSymbol();
        boolean isInterface = mixin.flags().has(AccessFlag.INTERFACE);
        MethodTypeDesc type = handler.methodTypeSymbol();
        Annotation retargeted = withMethod(wrapOperation, WITH_MODIFIER + WITH_MODIFIER_TYPE);
        builder.withMethod(
                HostReplacedCalls.PREFIX + handler.methodName().stringValue() + "$withModifier",
                type,
                handler.flags().flagsMask(),
                method -> {
                    method.with(RuntimeVisibleAnnotationsAttribute.of(retargeted));
                    handler.findAttribute(Attributes.runtimeVisibleParameterAnnotations())
                            .ifPresent(attribute -> method.with(
                                    RuntimeVisibleParameterAnnotationsAttribute.of(attribute.parameterAnnotations())));
                    handler.findAttribute(Attributes.runtimeInvisibleParameterAnnotations())
                            .ifPresent(attribute -> method.with(RuntimeInvisibleParameterAnnotationsAttribute.of(
                                    attribute.parameterAnnotations())));
                    method.withCode(code -> {
                        code.aload(0);
                        code.aload(1);
                        code.aload(2);
                        code.aload(3);
                        code.aload(4);
                        code.aload(4);
                        code.aload(5);
                        code.invokeinterface(MAP_ENTRY, "getKey", MethodTypeDesc.of(ConstantDescs.CD_Object));
                        code.checkcast(IDENTIFIER);
                        code.invokevirtual(FILE_TO_ID, "idToFile", MethodTypeDesc.of(IDENTIFIER, IDENTIFIER));
                        code.aload(5);
                        code.invokeinterface(MAP_ENTRY, "getValue", MethodTypeDesc.of(ConstantDescs.CD_Object));
                        code.invokestatic(
                                MAP,
                                "entry",
                                MethodTypeDesc.of(MAP_ENTRY, ConstantDescs.CD_Object, ConstantDescs.CD_Object),
                                true);
                        code.invokestatic(owner, handler.methodName().stringValue(), type, isInterface);
                        markerToEmpty(code, owner, marker);
                    });
                });
    }

    /** With the handler's result on the stack: returns it, or {@code success(Optional.empty())} for the marker. */
    private static void markerToEmpty(java.lang.classfile.CodeBuilder code, ClassDesc owner, String marker) {
        Label keep = code.newLabel();
        code.dup();
        code.invokeinterface(DATA_RESULT, "result", MethodTypeDesc.of(OPTIONAL));
        code.aconst_null();
        code.invokevirtual(OPTIONAL, "orElse", MethodTypeDesc.of(ConstantDescs.CD_Object, ConstantDescs.CD_Object));
        code.getstatic(owner, marker, ConstantDescs.CD_Object);
        code.if_acmpne(keep);
        code.pop();
        code.invokestatic(OPTIONAL, "empty", MethodTypeDesc.of(OPTIONAL));
        code.invokestatic(DATA_RESULT, "success", MethodTypeDesc.of(DATA_RESULT, ConstantDescs.CD_Object), true);
        code.labelBinding(keep);
        code.areturn();
    }

    /** {@code annotation} with its {@code method} element set to {@code method}. */
    private static Annotation withMethod(Annotation annotation, String method) {
        List<AnnotationElement> elements = new ArrayList<>();
        for (AnnotationElement element : annotation.elements()) {
            elements.add(
                    element.name().equalsString("method")
                            ? AnnotationElement.of("method", AnnotationValue.ofArray(AnnotationValue.ofString(method)))
                            : element);
        }
        return Annotation.of(annotation.classSymbol(), elements);
    }

    /** The {@code target} strings of {@code annotation}'s {@code @At} values. */
    private static List<String> targets(Annotation annotation) {
        List<String> targets = new ArrayList<>();
        for (AnnotationElement element : annotation.elements()) {
            if (element.name().equalsString("at")) {
                collectTargets(element.value(), targets);
            }
        }
        return targets;
    }

    private static void collectTargets(AnnotationValue value, List<String> targets) {
        if (value instanceof AnnotationValue.OfArray array) {
            array.values().forEach(item -> collectTargets(item, targets));
        } else if (value instanceof AnnotationValue.OfAnnotation at) {
            for (AnnotationElement element : at.annotation().elements()) {
                if (element.name().equalsString("target")
                        && element.value() instanceof AnnotationValue.OfString target) {
                    targets.add(target.stringValue());
                }
            }
        }
    }

    private static List<Instruction> instructions(MethodModel method) {
        List<Instruction> instructions = new ArrayList<>();
        method.code().ifPresent(code -> {
            for (CodeElement element : code) {
                if (element instanceof Instruction instruction) {
                    instructions.add(instruction);
                }
            }
        });
        return instructions;
    }
}
