package dev.plexusmodloader.compat.fabric.mixin;

import java.lang.classfile.Annotation;
import java.lang.classfile.AnnotationElement;
import java.lang.classfile.AnnotationValue;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassModel;
import java.lang.classfile.Label;
import java.lang.classfile.MethodModel;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.util.List;

/**
 * Fabric API's {@code ServerWatchdogMixin.printEntireThreadDump} on NeoForge (26.2.0.93 and 26.3.0.98, checked with
 * {@code javap}). Vanilla's {@code ServerWatchdog.createWatchdogCrashReport(String, long)} appends the stuck thread's
 * {@code ThreadInfo}, whose {@code toString()} keeps eight stack frames; Fabric API's {@code @ModifyArg} turns that
 * argument into the whole stack ({@code ThreadPrinting.fullThreadInfoToString}). NeoForge moves the body into {@code
 * createWatchdogCrashReport(String, long, String)} and appends {@code ThreadInfoUtil.getEntireStacktrace(threadInfo)}
 * there instead.
 *
 * <p>The bridge ({@link #bridgeMethod}) is a {@code @WrapOperation} of NeoForge's call: it gives the handler the
 * {@code ThreadInfo} vanilla appends, and appends the handler's text; should the handler return anything but a
 * {@code String} (it returns its argument for anything that is not a {@code ThreadInfo}), NeoForge's call runs. Only a
 * static handler {@code (Object)Object} whose only call is Fabric API's {@code fullThreadInfoToString} is bridged
 * ({@link #fits}).
 *
 * <p>Thread: any; no state.
 */
final class HostThreadDump {

    static final String REPLACED = "Ljava/lang/StringBuilder;append(Ljava/lang/Object;)Ljava/lang/StringBuilder;";
    static final String REPLACEMENT = "Lnet/neoforged/neoforge/logging/ThreadInfoUtil;getEntireStacktrace("
            + "Ljava/lang/management/ThreadInfo;)Ljava/lang/String;";

    private static final String TARGET_CLASS = "net/minecraft/server/dedicated/ServerWatchdog";
    private static final String TARGET_METHOD = "createWatchdogCrashReport";
    private static final String HOST_METHOD =
            "createWatchdogCrashReport(Ljava/lang/String;JLjava/lang/String;)" + "Lnet/minecraft/CrashReport;";
    private static final String MODIFY_ARG = "Lorg/spongepowered/asm/mixin/injection/ModifyArg;";
    private static final String WRAP_OPERATION = "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;";
    private static final String AT = "Lorg/spongepowered/asm/mixin/injection/At;";
    private static final ClassDesc OPERATION =
            ClassDesc.ofDescriptor("Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;");
    private static final ClassDesc THREAD_INFO = ClassDesc.of("java.lang.management.ThreadInfo");
    private static final ClassDesc THREAD_PRINTING =
            ClassDesc.of("net.fabricmc.fabric.impl.crash.report.info.ThreadPrinting");
    private static final MethodTypeDesc HANDLER = MethodTypeDesc.of(ConstantDescs.CD_Object, ConstantDescs.CD_Object);

    private HostThreadDump() {}

    /**
     * Whether {@code handler}, a {@code type} injection into {@code targetClass.method} at {@code point}, which the
     * patch removed there, is the thread dump this class bridges on {@code patched}.
     */
    static boolean fits(
            MethodModel handler, String type, String targetClass, String method, String point, ClassModel patched) {
        return type.equals(MODIFY_ARG)
                && targetClass.equals(TARGET_CLASS)
                && method.equals(TARGET_METHOD)
                && point.equals(REPLACED)
                && handler.flags().has(AccessFlag.STATIC)
                && handler.methodTypeSymbol().equals(HANDLER)
                && handler.findAttribute(Attributes.runtimeVisibleParameterAnnotations())
                        .isEmpty()
                && handler.findAttribute(Attributes.runtimeInvisibleParameterAnnotations())
                        .isEmpty()
                && onlyFabricThreadPrinting(handler)
                && HostPatchAdjustments.methods(patched, TARGET_METHOD, HOST_METHOD.substring(TARGET_METHOD.length()))
                        .stream()
                        .anyMatch(found -> HostPatchAdjustments.contains(found, REPLACEMENT));
    }

    /** Whether a call {@code replaced} that the host replaced with {@code replacement} is the one this class bridges. */
    static boolean covers(String replaced, String replacement) {
        return replaced.equals(REPLACED) && replacement.equals(REPLACEMENT);
    }

    /** Whether {@code handler}'s only call is Fabric API's {@code ThreadPrinting.fullThreadInfoToString(ThreadInfo)}. */
    private static boolean onlyFabricThreadPrinting(MethodModel handler) {
        List<InvokeInstruction> calls = handler.code().stream()
                .flatMap(code -> code.elementStream())
                .filter(element -> element instanceof InvokeInstruction)
                .map(element -> (InvokeInstruction) element)
                .toList();
        return calls.size() == 1
                && calls.getFirst().owner().asSymbol().equals(THREAD_PRINTING)
                && calls.getFirst().name().equalsString("fullThreadInfoToString");
    }

    /**
     * Adds the bridge {@code poly$hostCall$<handler>(ThreadInfo, Operation)String}: a {@code @WrapOperation} of
     * NeoForge's {@code getEntireStacktrace} in {@code createWatchdogCrashReport(String, long, String)}.
     */
    static void bridgeMethod(ClassBuilder builder, ClassModel mixin, MethodModel handler) {
        String name = handler.methodName().stringValue();
        ClassDesc owner = mixin.thisClass().asSymbol();
        boolean isInterface = mixin.flags().has(AccessFlag.INTERFACE);
        Annotation wrapOperation = Annotation.of(
                ClassDesc.ofDescriptor(WRAP_OPERATION),
                AnnotationElement.of("method", AnnotationValue.ofArray(AnnotationValue.ofString(HOST_METHOD))),
                AnnotationElement.of(
                        "at",
                        AnnotationValue.ofArray(AnnotationValue.ofAnnotation(Annotation.of(
                                ClassDesc.ofDescriptor(AT),
                                AnnotationElement.ofString("value", "INVOKE"),
                                AnnotationElement.ofString("target", REPLACEMENT))))));
        MethodTypeDesc bridgeType = MethodTypeDesc.of(ConstantDescs.CD_String, THREAD_INFO, OPERATION);
        builder.withMethod(
                HostReplacedCalls.PREFIX + name, bridgeType, handler.flags().flagsMask(), method -> {
                    method.with(RuntimeVisibleAnnotationsAttribute.of(wrapOperation));
                    method.withCode(code -> {
                        Label host = code.newLabel();
                        code.aload(0);
                        code.invokestatic(owner, name, HANDLER, isInterface);
                        code.dup();
                        code.instanceOf(ConstantDescs.CD_String);
                        code.ifeq(host);
                        code.checkcast(ConstantDescs.CD_String);
                        code.areturn();
                        code.labelBinding(host);
                        code.pop();
                        code.aload(1);
                        code.iconst_1();
                        code.anewarray(ConstantDescs.CD_Object);
                        code.dup();
                        code.iconst_0();
                        code.aload(0);
                        code.aastore();
                        code.invokeinterface(
                                OPERATION,
                                "call",
                                MethodTypeDesc.of(ConstantDescs.CD_Object, ConstantDescs.CD_Object.arrayType()));
                        code.checkcast(ConstantDescs.CD_String);
                        code.areturn();
                    });
                });
    }
}
