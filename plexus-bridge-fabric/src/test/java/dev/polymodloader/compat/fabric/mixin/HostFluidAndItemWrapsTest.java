package dev.polymodloader.compat.fabric.mixin;

import static java.lang.constant.ConstantDescs.CD_Boolean;
import static java.lang.constant.ConstantDescs.CD_Object;
import static java.lang.constant.ConstantDescs.CD_String;
import static java.lang.constant.ConstantDescs.CD_boolean;
import static java.lang.constant.ConstantDescs.CD_int;
import static java.lang.constant.ConstantDescs.CD_long;
import static java.lang.constant.ConstantDescs.CD_void;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.polymodloader.api.runtime.GameSide;
import dev.polymodloader.compat.bridge.GameClassBytes;
import dev.polymodloader.compat.bridge.HostedLaunch;
import dev.polymodloader.compat.bridge.InjectionPointReplacement;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.classfile.Annotation;
import java.lang.classfile.AnnotationElement;
import java.lang.classfile.AnnotationValue;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.MethodModel;
import java.lang.classfile.attribute.RuntimeInvisibleAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeInvisibleParameterAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Forge bridges of Fabric API's fluid and item mixins ({@code HostPatchAdjustments.FluidWrap}, {@code ItemWrap},
 * ADR-0030): a bridge runs the Fabric handler on Forge's call, or the handler is dropped only where it makes Forge's
 * own call; a point no bridge can carry is {@code PLEXUS-COMPAT-224}.
 */
class HostFluidAndItemWrapsTest {

    private static final String CONFIG = "fluid.mixins.json";
    private static final String MOD = "fabric-test-module";
    private static final ClassDesc OPERATION =
            ClassDesc.ofDescriptor("Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;");
    private static final ClassDesc LOCAL_REF = ClassDesc.of("com.llamalad7.mixinextras.sugar.ref.LocalRef");
    private static final ClassDesc FLUID_STATE = ClassDesc.of("net.minecraft.world.level.material.FluidState");
    private static final ClassDesc TAG_KEY = ClassDesc.of("net.minecraft.tags.TagKey");
    private static final ClassDesc BOAT = ClassDesc.of("net.minecraft.world.entity.vehicle.boat.AbstractBoat");
    private static final ClassDesc ENTITY = ClassDesc.of("net.minecraft.world.entity.Entity");
    private static final ClassDesc LIVING_ENTITY = ClassDesc.of("net.minecraft.world.entity.LivingEntity");
    private static final ClassDesc ITEM = ClassDesc.of("net.minecraft.world.item.Item");
    private static final ClassDesc ITEM_STACK = ClassDesc.of("net.minecraft.world.item.ItemStack");
    private static final ClassDesc TEMPLATE = ClassDesc.of("net.minecraft.world.item.ItemStackTemplate");
    private static final ClassDesc HOLDER = ClassDesc.of("net.minecraft.core.Holder");
    private static final ClassDesc ENCHANTMENT = ClassDesc.of("net.minecraft.world.item.enchantment.Enchantment");
    private static final ClassDesc ENCHANTING_CONTEXT =
            ClassDesc.of("net.fabricmc.fabric.api.item.v1.EnchantingContext");
    private static final String FLUID_IS =
            "Lnet/minecraft/world/level/material/FluidState;is(Lnet/minecraft/tags/TagKey;)Z";
    private static final String CAN_BOAT = "Lnet/minecraft/world/entity/vehicle/boat/AbstractBoat;canBoatInFluid("
            + "Lnet/minecraft/world/level/material/FluidState;)Z";
    private static final String CAN_START_SWIMMING = "Lnet/minecraft/world/entity/Entity;canStartSwimming()Z";
    private static final String ITEM_REMAINDER =
            "Lnet/minecraft/world/item/Item;getCraftingRemainder()Lnet/minecraft/world/item/ItemStackTemplate;";
    private static final String STACK_REMAINDER =
            "Lnet/minecraft/world/item/ItemStack;getCraftingRemainder()Lnet/minecraft/world/item/ItemStackTemplate;";
    private static final String IS_PRIMARY =
            "Lnet/minecraft/world/item/enchantment/Enchantment;isPrimaryItem(Lnet/minecraft/world/item/ItemStack;)Z";
    private static final String CAN_APPLY =
            "Lnet/minecraft/world/item/ItemStack;canApplyAtEnchantingTable(Lnet/minecraft/core/Holder;)Z";
    private static final String WRAP_OPERATION = "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;";
    private static final String REDIRECT = "Lorg/spongepowered/asm/mixin/injection/Redirect;";
    private static final String MODIFY_EXPRESSION_VALUE = "Lcom/llamalad7/mixinextras/injector/ModifyExpressionValue;";

    @TempDir
    Path directory;

    @Test
    void boatSupportBridgeRunsTheHandlerOnForgesCall() throws IOException {
        byte[] mixin = mixin(
                "m/BoatMixin",
                BOAT,
                builder -> builder.withMethod(
                        "customFluidSupport",
                        MethodTypeDesc.of(CD_boolean, FLUID_STATE, TAG_KEY, OPERATION),
                        ClassFile.ACC_PRIVATE,
                        method -> {
                            method.with(RuntimeVisibleAnnotationsAttribute.of(
                                    injector(WRAP_OPERATION, "checkInWater", FLUID_IS)));
                            method.withCode(code -> callOperation(code, 1, 3, 2));
                        }));
        HostPatchAdjustments adjustments = plan(
                BOAT,
                "checkInWater",
                MethodTypeDesc.of(CD_boolean),
                code -> call(code, FLUID_IS, false),
                code -> call(code, CAN_BOAT, false),
                "m/BoatMixin",
                mixin,
                new InjectionPointReplacement(
                        "net/minecraft/world/entity/vehicle/boat/AbstractBoat", "checkInWater", FLUID_IS, CAN_BOAT));

        HostPatchAdjustments.Finding finding = single(adjustments);
        assertEquals("PLEXUS-COMPAT-226", finding.code(), finding.reason());
        ClassModel applied = ClassFile.of().parse(adjustments.apply("m.BoatMixin", mixin));
        MethodModel bridge = method(applied, "poly$hostCall$customFluidSupport");
        assertEquals(
                "(Lnet/minecraft/world/entity/vehicle/boat/AbstractBoat;Lnet/minecraft/world/level/material/FluidState;"
                        + "Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;)Z",
                bridge.methodType().stringValue());
        List<String> calls = calls(bridge);
        assertTrue(calls.contains("com/llamalad7/mixinextras/injector/wrapoperation/Operation.call"), calls.toString());
        assertTrue(calls.contains(PrecomputedCall.class.getName().replace('.', '/') + ".<init>"), calls.toString());
        assertTrue(calls.contains("m/BoatMixin.customFluidSupport"), calls.toString());
        assertFalse(hasAnnotation(method(applied, "customFluidSupport"), WRAP_OPERATION));
    }

    @Test
    void fluidHandlerThatDoesNotMakeTheWrappedCallIsNotBridged() throws IOException {
        byte[] mixin = mixin(
                "m/BoatMixin",
                BOAT,
                builder -> builder.withMethod(
                        "customFluidSupport",
                        MethodTypeDesc.of(CD_boolean, FLUID_STATE, TAG_KEY, OPERATION),
                        ClassFile.ACC_PRIVATE,
                        method -> {
                            method.with(RuntimeVisibleAnnotationsAttribute.of(
                                    injector(WRAP_OPERATION, "checkInWater", FLUID_IS)));
                            method.withCode(code -> code.iconst_1().ireturn());
                        }));
        HostPatchAdjustments adjustments = plan(
                BOAT,
                "checkInWater",
                MethodTypeDesc.of(CD_boolean),
                code -> call(code, FLUID_IS, false),
                code -> call(code, CAN_BOAT, false),
                "m/BoatMixin",
                mixin,
                new InjectionPointReplacement(
                        "net/minecraft/world/entity/vehicle/boat/AbstractBoat", "checkInWater", FLUID_IS, CAN_BOAT));

        HostPatchAdjustments.Finding finding = single(adjustments);
        assertEquals("PLEXUS-COMPAT-224", finding.code(), finding.reason());
        assertTrue(finding.incompatible());
    }

    @Test
    void swimmingBridgeReadsVanillasFluidStateAndKeepsTheSharedLocal() throws IOException {
        Annotation share = Annotation.of(
                ClassDesc.ofDescriptor("Lcom/llamalad7/mixinextras/sugar/Share;"),
                AnnotationElement.ofString("value", "successfulFluids"));
        byte[] mixin = mixin(
                "m/EntityMixin",
                ENTITY,
                builder -> builder.withMethod(
                        "checkIfStandingInSwimmableFluid",
                        MethodTypeDesc.of(CD_boolean, FLUID_STATE, TAG_KEY, OPERATION, LOCAL_REF),
                        ClassFile.ACC_PRIVATE,
                        method -> {
                            method.with(RuntimeVisibleAnnotationsAttribute.of(
                                    injector(WRAP_OPERATION, "updateSwimming", FLUID_IS)));
                            method.with(RuntimeInvisibleParameterAnnotationsAttribute.of(
                                    List.of(List.of(), List.of(), List.of(), List.of(share))));
                            method.withCode(code -> callOperation(code, 1, 3, 2));
                        }));
        HostPatchAdjustments adjustments = plan(
                ENTITY,
                "updateSwimming",
                MethodTypeDesc.of(CD_void),
                code -> call(code, FLUID_IS, true),
                code -> call(code, CAN_START_SWIMMING, true),
                "m/EntityMixin",
                mixin,
                new InjectionPointReplacement(
                        "net/minecraft/world/entity/Entity", "updateSwimming", FLUID_IS, CAN_START_SWIMMING));

        HostPatchAdjustments.Finding finding = single(adjustments);
        assertEquals("PLEXUS-COMPAT-226", finding.code(), finding.reason());
        ClassModel applied = ClassFile.of().parse(adjustments.apply("m.EntityMixin", mixin));
        MethodModel bridge = method(applied, "poly$hostCall$checkIfStandingInSwimmableFluid");
        assertEquals(
                "(Lnet/minecraft/world/entity/Entity;Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;"
                        + "Lcom/llamalad7/mixinextras/sugar/ref/LocalRef;)Z",
                bridge.methodType().stringValue());
        List<String> calls = calls(bridge);
        assertTrue(calls.contains("net/minecraft/world/entity/Entity.blockPosition"), calls.toString());
        assertTrue(calls.contains("net/minecraft/world/level/Level.getFluidState"), calls.toString());
        assertTrue(calls.contains("m/EntityMixin.checkIfStandingInSwimmableFluid"), calls.toString());
        List<List<Annotation>> parameters = bridge.findAttribute(Attributes.runtimeInvisibleParameterAnnotations())
                .orElseThrow()
                .parameterAnnotations();
        assertEquals(List.of(List.of(), List.of()), parameters.subList(0, 2));
        assertEquals(
                "Lcom/llamalad7/mixinextras/sugar/Share;",
                parameters.get(2).getFirst().className().stringValue());
    }

    @Test
    void drowningCheckThatForgeMovedIntoItsBreatheHookDoesNotRun() throws IOException {
        String eyeInFluid = "Lnet/minecraft/world/entity/LivingEntity;isEyeInFluid(Lnet/minecraft/tags/TagKey;)Z";
        String breathe =
                "Lnet/minecraftforge/common/ForgeHooks;onLivingBreathe(Lnet/minecraft/world/entity/LivingEntity;II)V";
        byte[] mixin = mixin(
                "m/LivingMixin",
                LIVING_ENTITY,
                builder -> builder.withMethod(
                        "customFluidDrowning",
                        MethodTypeDesc.of(CD_boolean, CD_boolean),
                        ClassFile.ACC_PRIVATE,
                        method -> {
                            method.with(RuntimeVisibleAnnotationsAttribute.of(
                                    injector(MODIFY_EXPRESSION_VALUE, "baseTick", eyeInFluid)));
                            method.withCode(code -> code.iload(1).ireturn());
                        }));
        HostPatchAdjustments adjustments = plan(
                LIVING_ENTITY,
                "baseTick",
                MethodTypeDesc.of(CD_void),
                code -> call(code, eyeInFluid, true),
                code -> code.aconst_null()
                        .iconst_0()
                        .iconst_0()
                        .invokestatic(
                                ClassDesc.of("net.minecraftforge.common.ForgeHooks"),
                                "onLivingBreathe",
                                MethodTypeDesc.of(CD_void, LIVING_ENTITY, CD_int, CD_int)),
                "m/LivingMixin",
                mixin,
                new InjectionPointReplacement(
                        "net/minecraft/world/entity/LivingEntity", "baseTick", eyeInFluid, breathe));

        HostPatchAdjustments.Finding finding = single(adjustments);
        assertEquals("PLEXUS-COMPAT-224", finding.code(), finding.reason());
        assertTrue(finding.reason().endsWith("this injection does not run"), finding.reason());
    }

    @Test
    void stackRemainderRedirectIsDroppedWhereForgeMakesTheSameCall() throws IOException {
        byte[] mixin = remainderMixin(code -> code.aload(1)
                .invokeinterface(LOCAL_REF, "get", MethodTypeDesc.of(CD_Object))
                .checkcast(ITEM_STACK)
                .invokevirtual(ITEM_STACK, "getCraftingRemainder", MethodTypeDesc.of(TEMPLATE))
                .areturn());
        HostPatchAdjustments adjustments = remainderPlan(mixin);

        HostPatchAdjustments.Finding finding = single(adjustments);
        assertEquals("PLEXUS-COMPAT-226", finding.code(), finding.reason());
        ClassModel applied = ClassFile.of().parse(adjustments.apply("m.RecipeMixin", mixin));
        assertFalse(hasAnnotation(method(applied, "getStackRemainder"), REDIRECT));
        assertTrue(
                applied.methods().stream()
                        .noneMatch(method -> method.methodName().stringValue().startsWith("poly$")),
                "no bridge is needed");
    }

    @Test
    void remainderRedirectThatDoesMoreThanForgesCallDoesNotRun() throws IOException {
        // The handler asks the item, not the stack: Forge's stack call would give another answer.
        byte[] mixin = remainderMixin(code -> code.aload(0)
                .invokevirtual(ITEM, "getCraftingRemainder", MethodTypeDesc.of(TEMPLATE))
                .areturn());
        HostPatchAdjustments.Finding finding = single(remainderPlan(mixin));
        assertEquals("PLEXUS-COMPAT-224", finding.code(), finding.reason());
    }

    @Test
    void enchantingCheckBridgeLetsFabricDecideOnlyWhereItChangesVanillasAnswer() throws IOException {
        ClassDesc helper = ClassDesc.of("net.minecraft.world.item.enchantment.EnchantmentHelper");
        String lambda = "lambda$getAvailableEnchantmentResults$0";
        MethodTypeDesc handlerType =
                MethodTypeDesc.of(CD_boolean, ENCHANTMENT, ITEM_STACK, ITEM_STACK, CD_boolean, HOLDER);
        byte[] mixin = mixin(
                "m/HelperMixin",
                helper,
                builder -> builder.withMethod(
                        "useCustomEnchantingChecks",
                        handlerType,
                        ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC,
                        method -> {
                            method.with(RuntimeVisibleAnnotationsAttribute.of(redirect(lambda, IS_PRIMARY)));
                            method.withCode(code -> code.aload(1)
                                    .aload(4)
                                    .getstatic(ENCHANTING_CONTEXT, "PRIMARY", ENCHANTING_CONTEXT)
                                    .invokevirtual(
                                            ITEM_STACK,
                                            "canBeEnchantedWith",
                                            MethodTypeDesc.of(CD_boolean, HOLDER, ENCHANTING_CONTEXT))
                                    .ireturn());
                        }));
        MethodTypeDesc lambdaType = MethodTypeDesc.of(CD_boolean, ITEM_STACK, CD_boolean, HOLDER);
        HostPatchAdjustments adjustments = plan(
                helper,
                lambda,
                lambdaType,
                true,
                code -> code.aconst_null()
                        .aconst_null()
                        .invokevirtual(ENCHANTMENT, "isPrimaryItem", MethodTypeDesc.of(CD_boolean, ITEM_STACK))
                        .ireturn(),
                code -> code.aconst_null()
                        .aconst_null()
                        .invokevirtual(ITEM_STACK, "canApplyAtEnchantingTable", MethodTypeDesc.of(CD_boolean, HOLDER))
                        .ireturn(),
                "m/HelperMixin",
                mixin,
                new InjectionPointReplacement(
                        "net/minecraft/world/item/enchantment/EnchantmentHelper", lambda, IS_PRIMARY, CAN_APPLY));

        HostPatchAdjustments.Finding finding = single(adjustments);
        assertEquals("PLEXUS-COMPAT-226", finding.code(), finding.reason());
        ClassModel applied = ClassFile.of().parse(adjustments.apply("m.HelperMixin", mixin));
        List<String> calls = calls(method(applied, "poly$hostCall$useCustomEnchantingChecks"));
        assertEquals(
                List.of(
                        "net/minecraft/world/item/ItemStack.canBeEnchantedWith",
                        "net/minecraft/core/Holder.value",
                        "net/minecraft/world/item/enchantment/Enchantment.isPrimaryItem",
                        "com/llamalad7/mixinextras/injector/wrapoperation/Operation.call",
                        "java/lang/Boolean.booleanValue"),
                calls);
    }

    private static final ClassDesc WATCHDOG = ClassDesc.of("net.minecraft.server.dedicated.ServerWatchdog");
    private static final ClassDesc CRASH_REPORT = ClassDesc.of("net.minecraft.CrashReport");
    private static final ClassDesc STRING_BUILDER = ClassDesc.of("java.lang.StringBuilder");
    private static final ClassDesc THREAD_INFO = ClassDesc.of("java.lang.management.ThreadInfo");
    private static final MethodTypeDesc REPORT = MethodTypeDesc.of(CRASH_REPORT, CD_String, CD_long);
    private static final MethodTypeDesc HOST_REPORT = MethodTypeDesc.of(CRASH_REPORT, CD_String, CD_long, CD_String);

    @Test
    void watchdogThreadDumpRunsOnNeoForgesWholeStackCall() throws IOException {
        HostPatchAdjustments adjustments =
                watchdogPlan(watchdogMixin("net.fabricmc.fabric.impl.crash.report.info.ThreadPrinting"));

        HostPatchAdjustments.Finding finding = single(adjustments);
        assertEquals("PLEXUS-COMPAT-226", finding.code(), finding.reason());
        byte[] mixin = watchdogMixin("net.fabricmc.fabric.impl.crash.report.info.ThreadPrinting");
        ClassModel applied = ClassFile.of().parse(adjustments.apply("m.WatchdogMixin", mixin));
        MethodModel bridge = method(applied, "poly$hostCall$printEntireThreadDump");
        assertEquals(
                "(Ljava/lang/management/ThreadInfo;Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;)"
                        + "Ljava/lang/String;",
                bridge.methodType().stringValue());
        assertEquals(
                List.of(
                        "m/WatchdogMixin.printEntireThreadDump",
                        "com/llamalad7/mixinextras/injector/wrapoperation/Operation.call"),
                calls(bridge));
        assertTrue(hasAnnotation(bridge, WRAP_OPERATION));
        assertFalse(hasAnnotation(
                method(applied, "printEntireThreadDump"), "Lorg/spongepowered/asm/mixin/injection/ModifyArg;"));
    }

    @Test
    void watchdogHandlerThatCallsSomethingElseDoesNotRun() throws IOException {
        HostPatchAdjustments.Finding finding = single(watchdogPlan(watchdogMixin("m.OtherPrinting")));
        assertEquals("PLEXUS-COMPAT-224", finding.code(), finding.reason());
    }

    /** Fabric API's handler shape: a ThreadInfo becomes {@code printing.fullThreadInfoToString(info)}. */
    private static byte[] watchdogMixin(String printing) {
        Annotation modifyArg = Annotation.of(
                ClassDesc.ofDescriptor("Lorg/spongepowered/asm/mixin/injection/ModifyArg;"),
                AnnotationElement.of(
                        "method",
                        AnnotationValue.ofArray(AnnotationValue.ofString(
                                "createWatchdogCrashReport(Ljava/lang/String;J)Lnet/minecraft/CrashReport;"))),
                AnnotationElement.of(
                        "at",
                        AnnotationValue.ofAnnotation(at(
                                "Ljava/lang/StringBuilder;append(Ljava/lang/Object;)" + "Ljava/lang/StringBuilder;"))));
        return mixin(
                "m/WatchdogMixin",
                WATCHDOG,
                builder -> builder.withMethod(
                        "printEntireThreadDump",
                        MethodTypeDesc.of(CD_Object, CD_Object),
                        ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC,
                        method -> {
                            method.with(RuntimeVisibleAnnotationsAttribute.of(modifyArg));
                            method.withCode(code -> code.aload(0)
                                    .checkcast(THREAD_INFO)
                                    .invokestatic(
                                            ClassDesc.of(printing),
                                            "fullThreadInfoToString",
                                            MethodTypeDesc.of(CD_String, THREAD_INFO))
                                    .areturn());
                        }));
    }

    /** Vanilla appends the ThreadInfo; NeoForge delegates to a three-argument overload that appends the whole stack. */
    private HostPatchAdjustments watchdogPlan(byte[] mixin) throws IOException {
        int flags = ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC;
        byte[] vanilla = ClassFile.of()
                .build(
                        WATCHDOG,
                        builder -> builder.withMethodBody(
                                "createWatchdogCrashReport",
                                REPORT,
                                flags,
                                code -> code.new_(STRING_BUILDER)
                                        .dup()
                                        .invokespecial(STRING_BUILDER, "<init>", MethodTypeDesc.of(CD_void))
                                        .aconst_null()
                                        .invokevirtual(
                                                STRING_BUILDER, "append", MethodTypeDesc.of(STRING_BUILDER, CD_Object))
                                        .pop()
                                        .aconst_null()
                                        .areturn()));
        byte[] patched = ClassFile.of().build(WATCHDOG, builder -> {
            builder.withMethodBody(
                    "createWatchdogCrashReport",
                    REPORT,
                    flags,
                    code -> code.aload(0)
                            .lload(1)
                            .aconst_null()
                            .invokestatic(WATCHDOG, "createWatchdogCrashReport", HOST_REPORT)
                            .areturn());
            builder.withMethodBody(
                    "createWatchdogCrashReport",
                    HOST_REPORT,
                    flags,
                    code -> code.aconst_null()
                            .invokestatic(
                                    ClassDesc.of("net.neoforged.neoforge.logging.ThreadInfoUtil"),
                                    "getEntireStacktrace",
                                    MethodTypeDesc.of(CD_String, THREAD_INFO))
                            .pop()
                            .aconst_null()
                            .areturn());
        });
        return plan(WATCHDOG, vanilla, patched, "m/WatchdogMixin", mixin, List.of());
    }

    private static final ClassDesc RELOAD_LISTENER =
            ClassDesc.of("net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener");
    private static final ClassDesc CODEC = ClassDesc.of("com.mojang.serialization.Codec");
    private static final ClassDesc DYNAMIC_OPS = ClassDesc.of("com.mojang.serialization.DynamicOps");
    private static final ClassDesc DATA_RESULT = ClassDesc.of("com.mojang.serialization.DataResult");
    private static final ClassDesc IDENTIFIER = ClassDesc.of("net.minecraft.resources.Identifier");
    private static final ClassDesc CALLBACK_INFO =
            ClassDesc.of("org.spongepowered.asm.mixin.injection.callback.CallbackInfo");
    private static final ClassDesc OPTIONAL = ClassDesc.of("java.util.Optional");
    private static final ClassDesc MAP = ClassDesc.of("java.util.Map");
    private static final String CODEC_PARSE =
            "Lcom/mojang/serialization/Codec;parse(Lcom/mojang/serialization/DynamicOps;"
                    + "Ljava/lang/Object;)Lcom/mojang/serialization/DataResult;";

    @Test
    void resourceConditionSkipBecomesNeoForgesEmptyOptional() throws IOException {
        byte[] mixin = conditionsMixin(true);
        HostPatchAdjustments adjustments = conditionsPlan(mixin);

        HostPatchAdjustments.Finding finding = single(adjustments);
        assertEquals("PLEXUS-COMPAT-226", finding.code(), finding.reason());
        ClassModel applied = ClassFile.of().parse(adjustments.apply("m.ConditionsMixin", mixin));
        assertFalse(hasAnnotation(method(applied, "skipData"), "Lorg/spongepowered/asm/mixin/injection/Inject;"));
        assertFalse(hasAnnotation(method(applied, "applyResourceConditions"), WRAP_OPERATION));
        MethodModel bridge = method(applied, "poly$hostCall$applyResourceConditions");
        assertTrue(hasAnnotation(bridge, WRAP_OPERATION));
        assertEquals(
                method(applied, "applyResourceConditions").methodType().stringValue(),
                bridge.methodType().stringValue());
        assertEquals(
                List.of(
                        "m/ConditionsMixin.applyResourceConditions",
                        "com/mojang/serialization/DataResult.result",
                        "java/util/Optional.orElse",
                        "java/util/Optional.empty",
                        "com/mojang/serialization/DataResult.success"),
                calls(bridge));
    }

    @Test
    void resourceConditionHeadHandlerThatDoesMoreThanSkipDoesNotRun() throws IOException {
        HostPatchAdjustments.Finding finding = single(conditionsPlan(conditionsMixin(false)));
        assertEquals("PLEXUS-COMPAT-224", finding.code(), finding.reason());
    }

    /** Fabric API's two handlers; {@code onlySkip} false gives the head handler a second call. */
    private static byte[] conditionsMixin(boolean onlySkip) {
        ClassDesc self = ClassDesc.of("m.ConditionsMixin");
        Annotation inject = Annotation.of(
                ClassDesc.ofDescriptor("Lorg/spongepowered/asm/mixin/injection/Inject;"),
                AnnotationElement.of(
                        "method", AnnotationValue.ofArray(AnnotationValue.ofString("lambda$scanDirectory$0"))),
                AnnotationElement.of(
                        "at",
                        AnnotationValue.ofArray(AnnotationValue.ofAnnotation(Annotation.of(
                                ClassDesc.ofDescriptor("Lorg/spongepowered/asm/mixin/injection/At;"),
                                AnnotationElement.ofString("value", "HEAD"))))));
        return mixin("m/ConditionsMixin", RELOAD_LISTENER, builder -> {
            builder.withField(
                    "SKIP_DATA_MARKER", CD_Object, ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC | ClassFile.ACC_FINAL);
            builder.withMethod(
                    "applyResourceConditions",
                    MethodTypeDesc.of(DATA_RESULT, CODEC, DYNAMIC_OPS, CD_Object, OPERATION),
                    ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC,
                    method -> {
                        method.with(RuntimeVisibleAnnotationsAttribute.of(
                                injector(WRAP_OPERATION, "scanDirectory", CODEC_PARSE)));
                        method.withCode(code -> {
                            java.lang.classfile.Label parse = code.newLabel();
                            code.aload(2).ifnonnull(parse);
                            code.getstatic(self, "SKIP_DATA_MARKER", CD_Object)
                                    .invokestatic(
                                            DATA_RESULT, "success", MethodTypeDesc.of(DATA_RESULT, CD_Object), true)
                                    .areturn();
                            code.labelBinding(parse);
                            code.aload(3)
                                    .iconst_1()
                                    .anewarray(CD_Object)
                                    .invokeinterface(
                                            OPERATION, "call", MethodTypeDesc.of(CD_Object, CD_Object.arrayType()))
                                    .checkcast(DATA_RESULT)
                                    .areturn();
                        });
                    });
            builder.withMethod(
                    "skipData",
                    MethodTypeDesc.of(CD_void, MAP, IDENTIFIER, CD_Object, CALLBACK_INFO),
                    ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC,
                    method -> {
                        method.with(RuntimeVisibleAnnotationsAttribute.of(inject));
                        method.withCode(code -> {
                            java.lang.classfile.Label end = code.newLabel();
                            if (!onlySkip) {
                                code.aload(0).invokeinterface(MAP, "clear", MethodTypeDesc.of(CD_void));
                            }
                            code.aload(2)
                                    .getstatic(self, "SKIP_DATA_MARKER", CD_Object)
                                    .if_acmpne(end)
                                    .aload(3)
                                    .invokevirtual(CALLBACK_INFO, "cancel", MethodTypeDesc.of(CD_void));
                            code.labelBinding(end);
                            code.return_();
                        });
                    });
        });
    }

    /** Vanilla's store lambda and parse; NeoForge's conditional codec and Optional lambda. */
    private HostPatchAdjustments conditionsPlan(byte[] mixin) throws IOException {
        int flags = ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC;
        MethodTypeDesc scan = MethodTypeDesc.of(CD_void, CODEC);
        byte[] vanilla = ClassFile.of().build(RELOAD_LISTENER, builder -> {
            builder.withMethodBody(
                    "scanDirectory",
                    scan,
                    flags,
                    code -> code.aload(0)
                            .aconst_null()
                            .aconst_null()
                            .invokeinterface(CODEC, "parse", MethodTypeDesc.of(DATA_RESULT, DYNAMIC_OPS, CD_Object))
                            .pop()
                            .return_());
            builder.withMethodBody(
                    "lambda$scanDirectory$0",
                    MethodTypeDesc.of(CD_void, MAP, IDENTIFIER, CD_Object),
                    ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC,
                    code -> code.return_());
        });
        byte[] patched = ClassFile.of().build(RELOAD_LISTENER, builder -> {
            builder.withMethodBody(
                    "scanDirectory",
                    scan,
                    flags,
                    code -> code.aload(0)
                            .invokestatic(
                                    ClassDesc.of("net.neoforged.neoforge.common.conditions.ConditionalOps"),
                                    "createConditionalCodec",
                                    MethodTypeDesc.of(CODEC, CODEC))
                            .aconst_null()
                            .aconst_null()
                            .invokeinterface(CODEC, "parse", MethodTypeDesc.of(DATA_RESULT, DYNAMIC_OPS, CD_Object))
                            .pop()
                            .return_());
            builder.withMethodBody(
                    "lambda$scanDirectory$0",
                    MethodTypeDesc.of(CD_void, IDENTIFIER, IDENTIFIER, MAP, OPTIONAL),
                    ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC,
                    code -> code.aload(3)
                            .invokevirtual(OPTIONAL, "isEmpty", MethodTypeDesc.of(CD_boolean))
                            .pop()
                            .return_());
        });
        return plan(RELOAD_LISTENER, vanilla, patched, "m/ConditionsMixin", mixin, List.of());
    }

    private byte[] remainderMixin(Consumer<CodeBuilder> body) {
        ClassDesc recipe = ClassDesc.of("net.minecraft.world.item.crafting.CraftingRecipe");
        return mixin(
                "m/RecipeMixin",
                recipe,
                builder -> builder.withMethod(
                        "getStackRemainder",
                        MethodTypeDesc.of(TEMPLATE, ITEM, LOCAL_REF),
                        ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC,
                        method -> {
                            method.with(RuntimeVisibleAnnotationsAttribute.of(
                                    redirect("defaultCraftingReminder", ITEM_REMAINDER)));
                            method.withCode(body);
                        }));
    }

    private HostPatchAdjustments remainderPlan(byte[] mixin) throws IOException {
        ClassDesc recipe = ClassDesc.of("net.minecraft.world.item.crafting.CraftingRecipe");
        ClassDesc input = ClassDesc.of("net.minecraft.world.item.crafting.CraftingInput");
        ClassDesc list = ClassDesc.of("net.minecraft.core.NonNullList");
        return plan(
                recipe,
                "defaultCraftingReminder",
                MethodTypeDesc.of(list, input),
                true,
                code -> code.aconst_null()
                        .invokevirtual(ITEM, "getCraftingRemainder", MethodTypeDesc.of(TEMPLATE))
                        .pop()
                        .aconst_null()
                        .areturn(),
                code -> code.aconst_null()
                        .invokevirtual(ITEM_STACK, "getCraftingRemainder", MethodTypeDesc.of(TEMPLATE))
                        .pop()
                        .aconst_null()
                        .areturn(),
                "m/RecipeMixin",
                mixin,
                new InjectionPointReplacement(
                        "net/minecraft/world/item/crafting/CraftingRecipe",
                        "defaultCraftingReminder",
                        ITEM_REMAINDER,
                        STACK_REMAINDER));
    }

    /** {@code operation.call(first, second)} unboxed to {@code boolean} and returned; the handler's shape. */
    private static void callOperation(CodeBuilder code, int first, int operation, int second) {
        code.aload(operation)
                .iconst_2()
                .anewarray(CD_Object)
                .dup()
                .iconst_0()
                .aload(first)
                .aastore()
                .dup()
                .iconst_1()
                .aload(second)
                .aastore()
                .invokeinterface(OPERATION, "call", MethodTypeDesc.of(CD_Object, CD_Object.arrayType()))
                .checkcast(CD_Boolean)
                .invokevirtual(CD_Boolean, "booleanValue", MethodTypeDesc.of(CD_boolean))
                .ireturn();
    }

    /** A call of {@code target} on null receivers and arguments; the method returns its result, or pops it. */
    private static void call(CodeBuilder code, String target, boolean voidMethod) {
        HostReplacedCalls.Call call = HostReplacedCalls.Call.parse(target).orElseThrow();
        code.aconst_null();
        for (ClassDesc parameter : call.type().parameterList()) {
            if (parameter.isPrimitive()) {
                code.iconst_0();
            } else {
                code.aconst_null();
            }
        }
        code.invokevirtual(ClassDesc.ofDescriptor(call.owner()), call.name(), call.type());
        if (voidMethod) {
            code.pop().return_();
        } else {
            code.ireturn();
        }
    }

    private static Annotation at(String target) {
        return Annotation.of(
                ClassDesc.ofDescriptor("Lorg/spongepowered/asm/mixin/injection/At;"),
                AnnotationElement.ofString("value", "INVOKE"),
                AnnotationElement.ofString("target", target));
    }

    private static Annotation injector(String type, String method, String target) {
        return Annotation.of(
                ClassDesc.ofDescriptor(type),
                AnnotationElement.of("method", AnnotationValue.ofArray(AnnotationValue.ofString(method))),
                AnnotationElement.of("at", AnnotationValue.ofArray(AnnotationValue.ofAnnotation(at(target)))));
    }

    private static Annotation redirect(String method, String target) {
        return Annotation.of(
                ClassDesc.ofDescriptor(REDIRECT),
                AnnotationElement.of("method", AnnotationValue.ofArray(AnnotationValue.ofString(method))),
                AnnotationElement.of("at", AnnotationValue.ofAnnotation(at(target))));
    }

    private static byte[] mixin(String name, ClassDesc target, Consumer<java.lang.classfile.ClassBuilder> methods) {
        return ClassFile.of().build(ClassDesc.ofInternalName(name), builder -> {
            builder.with(RuntimeInvisibleAnnotationsAttribute.of(Annotation.of(
                    ClassDesc.ofDescriptor("Lorg/spongepowered/asm/mixin/Mixin;"),
                    AnnotationElement.of("value", AnnotationValue.ofArray(AnnotationValue.ofClass(target))))));
            methods.accept(builder);
        });
    }

    private HostPatchAdjustments plan(
            ClassDesc target,
            String method,
            MethodTypeDesc type,
            Consumer<CodeBuilder> vanilla,
            Consumer<CodeBuilder> patched,
            String mixinName,
            byte[] mixin,
            InjectionPointReplacement replacement)
            throws IOException {
        return plan(target, method, type, false, vanilla, patched, mixinName, mixin, replacement);
    }

    private HostPatchAdjustments plan(
            ClassDesc target,
            String method,
            MethodTypeDesc type,
            boolean isStatic,
            Consumer<CodeBuilder> vanilla,
            Consumer<CodeBuilder> patched,
            String mixinName,
            byte[] mixin,
            InjectionPointReplacement replacement)
            throws IOException {
        int flags = ClassFile.ACC_PUBLIC | (isStatic ? ClassFile.ACC_STATIC : 0);
        byte[] vanillaClass = ClassFile.of()
                .build(target, builder -> builder.withMethod(method, type, flags, body -> body.withCode(vanilla)));
        byte[] patchedClass = ClassFile.of()
                .build(target, builder -> builder.withMethod(method, type, flags, body -> body.withCode(patched)));
        return plan(target, vanillaClass, patchedClass, mixinName, mixin, List.of(replacement));
    }

    private HostPatchAdjustments plan(
            ClassDesc target,
            byte[] vanillaClass,
            byte[] patchedClass,
            String mixinName,
            byte[] mixin,
            List<InjectionPointReplacement> replacements)
            throws IOException {
        String targetName =
                target.descriptorString().substring(1, target.descriptorString().length() - 1);
        String simpleName = mixinName.substring(mixinName.lastIndexOf('/') + 1);
        String config = "{\"required\": true, \"package\": \"m\", \"mixins\": [\"" + simpleName + "\"]}";
        Path jar = directory.resolve(simpleName + ".jar");
        try (OutputStream file = Files.newOutputStream(jar);
                JarOutputStream out = new JarOutputStream(file)) {
            out.putNextEntry(new JarEntry(CONFIG));
            out.write(config.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
            out.putNextEntry(new JarEntry(mixinName + ".class"));
            out.write(mixin);
            out.closeEntry();
        }
        HostedLaunch host = new HostedLaunch("MinecraftForge 26.2-65.1.5", List.of(), replacements, directory);
        return HostPatchAdjustments.plan(
                host,
                GameSide.DEDICATED_SERVER,
                java.util.Map.of(CONFIG, MOD),
                java.util.Map.of(MOD, jar),
                className -> className.equals(targetName) ? Optional.of(vanillaClass) : Optional.empty(),
                new GameClassBytes() {
                    @Override
                    public Optional<byte[]> classBytes(String className) {
                        return className.replace('.', '/').equals(targetName)
                                ? Optional.of(patchedClass)
                                : Optional.empty();
                    }

                    @Override
                    public boolean isDefined(String className) {
                        return className.replace('.', '/').equals(targetName);
                    }
                });
    }

    private static HostPatchAdjustments.Finding single(HostPatchAdjustments adjustments) {
        assertEquals(1, adjustments.findings().size(), adjustments.findings().toString());
        return adjustments.findings().getFirst();
    }

    private static MethodModel method(ClassModel model, String name) {
        return model.methods().stream()
                .filter(method -> method.methodName().equalsString(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError(name + " not in "
                        + model.methods().stream()
                                .map(method -> method.methodName().stringValue())
                                .toList()));
    }

    private static boolean hasAnnotation(MethodModel method, String type) {
        return method.findAttribute(Attributes.runtimeVisibleAnnotations()).stream()
                .flatMap(attribute -> attribute.annotations().stream())
                .anyMatch(annotation -> annotation.className().equalsString(type));
    }

    /** {@code owner.name} of each call {@code method} makes, in order. */
    private static List<String> calls(MethodModel method) {
        List<String> calls = new ArrayList<>();
        for (CodeElement element : method.code().orElseThrow()) {
            if (element instanceof InvokeInstruction invoke) {
                calls.add(invoke.owner().asInternalName() + "." + invoke.name().stringValue());
            }
        }
        return calls;
    }
}
