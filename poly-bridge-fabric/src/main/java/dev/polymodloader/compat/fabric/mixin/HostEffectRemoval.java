package dev.polymodloader.compat.fabric.mixin;

import java.lang.classfile.Annotation;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;

/**
 * A {@code @WrapOperation} of vanilla's {@code activeEffects.clear()} in {@code LivingEntity.removeAllEffects} on
 * NeoForge (26.2.0.93 and 26.3.0.98, checked with {@code javap}). NeoForge asks its {@code MobEffectEvent.Remove} about
 * each effect, collects the ones it lets go and removes them with {@code removed.keySet().forEach(activeEffects::remove)}
 * where vanilla clears the map. Fabric API's {@code allowRemoveAllEffects} wraps the clear: it makes it, then puts back
 * the effects that {@code ServerMobEffectEvents.ALLOW_EARLY_REMOVE} keeps.
 *
 * <p>The bridge ({@link #bridgeMethod}) wraps NeoForge's {@code forEach} and calls the handler with the entity's effect
 * map ({@code getActiveEffectsMap()}, the map vanilla clears) and a {@link SubstituteCall} that makes NeoForge's
 * removal. The handler thus asks Fabric's event after NeoForge's, and puts back what Fabric's listeners keep; it also
 * asks about the effects NeoForge's listeners kept, which stay. {@code onEffectsRemoved} then gets NeoForge's list, as
 * it gets vanilla's copy of all effects with Fabric API on vanilla. Only an instance handler that takes exactly the map
 * and its {@code Operation} and makes the clear once with that map ({@link HostReplacedCalls#callsOnceWithOwnArguments})
 * is bridged.
 *
 * <p>Thread: any; no state.
 */
final class HostEffectRemoval {

    static final String REPLACED = "Ljava/util/Map;clear()V";
    static final String REPLACEMENT = "Ljava/util/Set;forEach(Ljava/util/function/Consumer;)V";

    private static final String TARGET_CLASS = "net/minecraft/world/entity/LivingEntity";
    private static final String TARGET_METHOD = "removeAllEffects";
    private static final String WRAP_OPERATION = "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;";
    private static final ClassDesc OPERATION =
            ClassDesc.ofDescriptor("Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;");
    private static final ClassDesc MAP = ClassDesc.of("java.util.Map");
    private static final ClassDesc SET = ClassDesc.of("java.util.Set");
    private static final ClassDesc CONSUMER = ClassDesc.of("java.util.function.Consumer");
    private static final ClassDesc LIVING_ENTITY = ClassDesc.ofInternalName(TARGET_CLASS);
    private static final ClassDesc SUBSTITUTE_CALL = ClassDesc.of(SubstituteCall.class.getName());
    private static final MethodTypeDesc HANDLER = MethodTypeDesc.of(ConstantDescs.CD_void, MAP, OPERATION);

    private HostEffectRemoval() {}

    /**
     * Whether {@code handler}, a {@code type} injection into {@code targetClass.method} at {@code replaced}, which the
     * host replaced with {@code replacement}, is the wrap this class bridges.
     */
    static boolean fits(
            MethodModel handler, String type, String targetClass, String method, String replaced, String replacement) {
        return type.equals(WRAP_OPERATION)
                && targetClass.equals(TARGET_CLASS)
                && method.equals(TARGET_METHOD)
                && covers(replaced, replacement)
                && !handler.flags().has(AccessFlag.STATIC)
                && handler.methodTypeSymbol().equals(HANDLER)
                && handler.findAttribute(Attributes.runtimeVisibleParameterAnnotations())
                        .isEmpty()
                && handler.findAttribute(Attributes.runtimeInvisibleParameterAnnotations())
                        .isEmpty()
                && HostReplacedCalls.callsOnceWithOwnArguments(handler, 1);
    }

    /** Whether a call {@code replaced} that the host replaced with {@code replacement} is the one this class bridges. */
    static boolean covers(String replaced, String replacement) {
        return replaced.equals(REPLACED) && replacement.equals(REPLACEMENT);
    }

    /**
     * Adds the bridge {@code poly$hostCall$<handler>(Set, Consumer, Operation)} carrying {@code wrapOperation} (the
     * handler's, which the handler loses) retargeted at NeoForge's {@code forEach}.
     */
    static void bridgeMethod(ClassBuilder builder, ClassModel mixin, MethodModel handler, Annotation wrapOperation) {
        String name = handler.methodName().stringValue();
        ClassDesc owner = mixin.thisClass().asSymbol();
        boolean isInterface = mixin.flags().has(AccessFlag.INTERFACE);
        MethodTypeDesc bridgeType = MethodTypeDesc.of(ConstantDescs.CD_void, SET, CONSUMER, OPERATION);
        builder.withMethod(
                HostReplacedCalls.PREFIX + name, bridgeType, handler.flags().flagsMask(), method -> {
                    method.with(RuntimeVisibleAnnotationsAttribute.of(
                            HostReplacedCalls.retargeted(wrapOperation, REPLACED, REPLACEMENT)));
                    method.withCode(code -> {
                        code.aload(0);
                        code.aload(0);
                        code.checkcast(LIVING_ENTITY);
                        code.invokevirtual(LIVING_ENTITY, "getActiveEffectsMap", MethodTypeDesc.of(MAP));
                        code.new_(SUBSTITUTE_CALL);
                        code.dup();
                        code.aload(3);
                        code.iconst_2();
                        code.anewarray(ConstantDescs.CD_Object);
                        code.dup();
                        code.iconst_0();
                        code.aload(1);
                        code.aastore();
                        code.dup();
                        code.iconst_1();
                        code.aload(2);
                        code.aastore();
                        code.invokespecial(
                                SUBSTITUTE_CALL,
                                ConstantDescs.INIT_NAME,
                                MethodTypeDesc.of(
                                        ConstantDescs.CD_void, OPERATION, ConstantDescs.CD_Object.arrayType()));
                        code.invokespecial(owner, name, HANDLER, isInterface);
                        code.return_();
                    });
                });
    }
}
