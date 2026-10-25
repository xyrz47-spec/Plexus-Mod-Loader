package dev.polymodloader.compat.fabric.mixin;

import static java.lang.constant.ConstantDescs.CD_String;
import static java.lang.constant.ConstantDescs.CD_boolean;
import static java.lang.constant.ConstantDescs.CD_float;
import static java.lang.constant.ConstantDescs.CD_int;
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
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.attribute.RuntimeInvisibleAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Fabric mixins against a host's patched classes: moved, optional and left-out mixins (ADR-0021 G8). */
class HostPatchAdjustmentsTest {

    private static final ClassDesc TARGET = ClassDesc.of("t.Target");
    private static final String CONFIG = "example.mixins.json";
    private static final String CONFIG_TEXT = "{\"package\":\"m\",\"mixins\":[\"InjectMixin\",\"AccessorMixin\"]}";

    @TempDir
    Path directory;

    @Test
    void injectionsAreMovedOrMadeOptionalAndMixinsUsingRemovedMembersAreLeftOut() throws Exception {
        // Vanilla calls old() and moved() and has the field gone; the host's patch calls replacement() instead of
        // moved(), drops old() and the field.
        byte[] vanilla = target(true);
        byte[] patched = target(false);
        Path jar = jar(Map.of(
                CONFIG, CONFIG_TEXT, "m/InjectMixin.class", injectMixin(), "m/AccessorMixin.class", accessorMixin()));
        HostedLaunch host = new HostedLaunch(
                "Host 1",
                List.of(),
                List.of(new InjectionPointReplacement(
                        "t/Target", "run", "Lt/Target;moved()V", "Lt/Target;replacement()V")),
                directory);

        HostPatchAdjustments adjustments = HostPatchAdjustments.plan(
                host,
                GameSide.DEDICATED_SERVER,
                Map.of(CONFIG, "example"),
                Map.of("example", jar),
                name -> name.equals("t/Target") ? Optional.of(vanilla) : Optional.empty(),
                classes(patched));

        assertEquals(3, adjustments.findings().size(), adjustments.findings().toString());
        assertTrue(adjustments.findings().stream()
                .anyMatch(finding -> finding.leftOut() && finding.mixin().equals("m/AccessorMixin")));
        String config = new String(
                adjustments
                        .config(CONFIG, CONFIG_TEXT.getBytes(StandardCharsets.UTF_8))
                        .orElseThrow(),
                StandardCharsets.UTF_8);
        assertTrue(config.contains("InjectMixin") && !config.contains("AccessorMixin"), config);

        ClassModel applied = ClassFile.of().parse(adjustments.apply("m.InjectMixin", injectMixin()));
        String annotations = applied.methods().stream()
                .flatMap(method ->
                        method.findAttribute(java.lang.classfile.Attributes.runtimeVisibleAnnotations()).stream())
                .flatMap(attribute -> attribute.annotations().stream())
                .map(Annotation::toString)
                .toList()
                .toString();
        assertTrue(annotations.contains("require"), annotations);
        assertTrue(annotations.contains("Lt/Target;replacement()V"), annotations);
        assertFalse(annotations.contains("Lt/Target;moved()V"), annotations);
    }

    /**
     * The host's patch adds a parameter to the method an {@code @Inject} handler was written for (Forge adds a {@code
     * Frustum} to {@code LevelExtractor.extractVisibleBlockEntities}; Sodium injects there). Mixin would reject the
     * handler's descriptor and, with it, every other mixin on that class. The handler stays as a plain method and a
     * bridge with the patched parameters carries the injection, so it still runs.
     */
    @Test
    void anInjectionIntoAMethodThePatchGaveMoreParametersGetsABridge() throws Exception {
        byte[] vanilla = extractTarget(MethodTypeDesc.of(CD_void, CD_String, CD_float));
        byte[] patched = extractTarget(MethodTypeDesc.of(CD_void, CD_String, CD_float, CD_int));
        MethodTypeDesc handlerType = MethodTypeDesc.of(CD_void, CD_String, CD_float, CALLBACK_INFO);
        Path jar = jar(Map.of(EXTRACT_CONFIG, EXTRACT_CONFIG_TEXT, "m/ExtractMixin.class", extractMixin(handlerType)));

        HostPatchAdjustments adjustments = HostPatchAdjustments.plan(
                new HostedLaunch("Host 1", List.of(), List.of(), directory),
                GameSide.CLIENT,
                Map.of(EXTRACT_CONFIG, "example"),
                Map.of("example", jar),
                name -> name.equals("t/Target") ? Optional.of(vanilla) : Optional.empty(),
                classes(patched));

        assertEquals(1, adjustments.findings().size(), adjustments.findings().toString());
        assertFalse(adjustments.findings().getFirst().leftOut());
        assertTrue(
                adjustments.findings().getFirst().reason().contains("runs with the patched parameters"),
                adjustments.findings().toString());
        ClassModel applied = ClassFile.of().parse(adjustments.apply("m.ExtractMixin", extractMixin(handlerType)));
        MethodTypeDesc bridgeType = MethodTypeDesc.of(CD_void, CD_String, CD_float, CD_int, CALLBACK_INFO);
        MethodModel original = method(applied, "onExtract", handlerType);
        MethodModel bridge = applied.methods().stream()
                .filter(method -> method.methodType().equalsString(bridgeType.descriptorString()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no bridge with " + bridgeType.descriptorString()));
        assertFalse(annotationsOf(original).contains("Inject"), annotationsOf(original));
        assertTrue(
                annotationsOf(bridge).contains("Inject")
                        && annotationsOf(bridge).contains("require"),
                annotationsOf(bridge));
        String calls = bridge.code().orElseThrow().elementList().stream()
                .filter(element -> element instanceof InvokeInstruction)
                .map(Object::toString)
                .toList()
                .toString();
        assertTrue(calls.contains("onExtract") && calls.contains(handlerType.descriptorString()), calls);
    }

    /** A parameter the patch changed rather than added: the injection cannot run, so only it is taken out. */
    @Test
    void anInjectionWhoseParametersThePatchChangedIsTakenOut() throws Exception {
        byte[] vanilla = extractTarget(MethodTypeDesc.of(CD_void, CD_String, CD_float));
        byte[] patched = extractTarget(MethodTypeDesc.of(CD_void, CD_String, CD_int));
        MethodTypeDesc handlerType = MethodTypeDesc.of(CD_void, CD_String, CD_float, CALLBACK_INFO);
        Path jar = jar(Map.of(EXTRACT_CONFIG, EXTRACT_CONFIG_TEXT, "m/ExtractMixin.class", extractMixin(handlerType)));

        HostPatchAdjustments adjustments = HostPatchAdjustments.plan(
                new HostedLaunch("Host 1", List.of(), List.of(), directory),
                GameSide.CLIENT,
                Map.of(EXTRACT_CONFIG, "example"),
                Map.of("example", jar),
                name -> name.equals("t/Target") ? Optional.of(vanilla) : Optional.empty(),
                classes(patched));

        assertEquals(1, adjustments.findings().size(), adjustments.findings().toString());
        assertTrue(
                adjustments.findings().getFirst().reason().contains("does not run"),
                adjustments.findings().toString());
        ClassModel applied = ClassFile.of().parse(adjustments.apply("m.ExtractMixin", extractMixin(handlerType)));
        assertEquals(1, applied.methods().size());
        assertFalse(annotationsOf(method(applied, "onExtract", handlerType)).contains("Inject"));
    }

    /**
     * The host's patch added a public method that an {@code @Accessor} or {@code @Invoker} of a Fabric mixin also
     * generates, with the code the accessor would have (Forge adds {@code ServerCommonPacketListenerImpl.getConnection()},
     * which returns the field {@code connection}; Fabric's networking API has an accessor of that name). Mixin would
     * fail the whole mixin; without that one method the rest applies and the host's method implements the interface.
     */
    @Test
    void anAccessorOrInvokerThePatchAddedWithTheSameCodeIsLeftToThePatchedMethod() throws Exception {
        byte[] vanilla = memberTarget(false, CD_String);
        byte[] patched = memberTarget(true, CD_String);
        Path jar = jar(Map.of(MEMBER_CONFIG, MEMBER_CONFIG_TEXT, "m/MemberAccessor.class", accessorInterface()));

        HostPatchAdjustments adjustments = plan(jar, vanilla, patched);

        assertEquals(2, adjustments.findings().size(), adjustments.findings().toString());
        assertTrue(
                adjustments.findings().stream()
                        .allMatch(finding ->
                                !finding.leftOut() && finding.code().equals("POLY-COMPAT-212") && !finding.limited()),
                adjustments.findings().toString());
        assertTrue(adjustments
                .config(MEMBER_CONFIG, MEMBER_CONFIG_TEXT.getBytes(StandardCharsets.UTF_8))
                .isEmpty());
        ClassModel applied = ClassFile.of().parse(adjustments.apply("m.MemberAccessor", accessorInterface()));
        assertEquals(
                List.of("getServer"),
                applied.methods().stream()
                        .map(m -> m.methodName().stringValue())
                        .toList());
    }

    /**
     * The same name and descriptor, but the host's method is not that accessor. With {@code @Overwrite} required by
     * the configuration, Mixin would fail the mixin: it is left out.
     */
    @Test
    void anAccessorThePatchAddedWithOtherCodeLeavesTheMixinOutWhenOverwritesNeedAnnotations() throws Exception {
        byte[] vanilla = memberTarget(false, CD_String);
        byte[] patched = memberTarget(true, CD_String, false);
        String configText =
                "{\"package\":\"m\",\"mixins\":[\"MemberAccessor\"],\"overwrites\":{\"requireAnnotations\":true}}";
        Path jar = jar(Map.of(MEMBER_CONFIG, configText, "m/MemberAccessor.class", accessorInterface()));

        HostPatchAdjustments adjustments = plan(jar, vanilla, patched);

        assertEquals(1, adjustments.findings().size(), adjustments.findings().toString());
        assertTrue(
                adjustments.findings().getFirst().leftOut(),
                adjustments.findings().toString());
        String config = new String(
                adjustments
                        .config(MEMBER_CONFIG, configText.getBytes(StandardCharsets.UTF_8))
                        .orElseThrow(),
                StandardCharsets.UTF_8);
        assertFalse(config.contains("MemberAccessor"), config);
    }

    /**
     * Without that requirement Mixin replaces the host's method with the accessor, as it did before (architectury's
     * {@code LiquidBlockAccessor.getFluid()} over Forge's {@code LiquidBlock.getFluid()}); that is only reported.
     */
    @Test
    void anAccessorThePatchAddedWithOtherCodeIsReportedWhenMixinMayOverwrite() throws Exception {
        byte[] vanilla = memberTarget(false, CD_String);
        byte[] patched = memberTarget(true, CD_String, false);
        Path jar = jar(Map.of(MEMBER_CONFIG, MEMBER_CONFIG_TEXT, "m/MemberAccessor.class", accessorInterface()));

        HostPatchAdjustments adjustments = plan(jar, vanilla, patched);

        HostPatchAdjustments.Finding getConnection = adjustments.findings().stream()
                .filter(finding -> finding.reason().contains("getConnection"))
                .findFirst()
                .orElseThrow(() -> new AssertionError(adjustments.findings().toString()));
        assertEquals("POLY-COMPAT-213", getConnection.code());
        assertTrue(getConnection.limited() && !getConnection.leftOut(), getConnection.toString());
        assertTrue(adjustments
                .config(MEMBER_CONFIG, MEMBER_CONFIG_TEXT.getBytes(StandardCharsets.UTF_8))
                .isEmpty());
        ClassModel applied = ClassFile.of().parse(adjustments.apply("m.MemberAccessor", accessorInterface()));
        assertTrue(applied.methods().stream().anyMatch(m -> m.methodName().equalsString("getConnection")));
    }

    /**
     * The host's patch changed the type of the field an {@code @Accessor} reads (Forge turns {@code
     * AttributeSupplier$Builder.builder} from an {@code ImmutableMap.Builder} into a {@code Map}). No accessor of the
     * old type exists; the rest of the mixin applies, so the target still implements the interface, and a call to that
     * accessor throws an error that names the change instead of a {@code ClassCastException} at the mod's cast.
     */
    @Test
    void anAccessorWhoseFieldTypeThePatchChangedThrowsADiagnosticWhenCalled() throws Exception {
        byte[] vanilla = memberTarget(false, CD_List);
        byte[] patched = memberTarget(false, CD_Map);
        byte[] accessor = fieldAccessorInterface();
        Path jar = jar(Map.of(MEMBER_CONFIG, MEMBER_CONFIG_TEXT, "m/MemberAccessor.class", accessor));

        HostPatchAdjustments adjustments = plan(jar, vanilla, patched);

        assertEquals(1, adjustments.findings().size(), adjustments.findings().toString());
        HostPatchAdjustments.Finding finding = adjustments.findings().getFirst();
        assertFalse(finding.leftOut());
        assertTrue(finding.limited() && finding.code().equals("POLY-COMPAT-138"), finding.toString());
        assertTrue(
                finding.reason().contains("Ljava/util/Map;") && finding.reason().contains("POLY-COMPAT-214"),
                finding.reason());
        ClassModel applied = ClassFile.of().parse(adjustments.apply("m.MemberAccessor", accessor));
        assertEquals(
                List.of("getServer"),
                applied.methods().stream()
                        .map(m -> m.methodName().stringValue())
                        .toList());

        // As the game loads it, the interface answers that accessor with the diagnostic.
        byte[] runtime = adjustments.runtime("m.MemberAccessor", accessor);
        Class<?> type = new ByteClassLoader(Map.of("m.MemberAccessor", runtime, "m.Implementation", implementation()))
                .loadClass("m.Implementation");
        Object instance = type.getConstructor().newInstance();
        var error = org.junit.jupiter.api.Assertions.assertThrows(
                java.lang.reflect.InvocationTargetException.class,
                () -> type.getInterfaces()[0].getMethod("getBuilder").invoke(instance));
        assertTrue(
                error.getCause() instanceof UnsupportedOperationException,
                error.getCause().toString());
        assertTrue(
                error.getCause().getMessage().contains("POLY-COMPAT-214"),
                error.getCause().getMessage());
        assertTrue(
                error.getCause().getMessage().contains("example"),
                error.getCause().getMessage());
        assertEquals(accessor, adjustments.runtime("m.Other", accessor));
    }

    /**
     * A method a Fabric mixin adds that the host's patch added too, with the same code (Fabric's data generation API
     * and Forge both add {@code Advancement$Builder.save(Consumer, Identifier)}): the patched class keeps its own and the
     * rest of the mixin applies.
     */
    @Test
    void aMethodThePatchAddedWithTheSameCodeIsLeftToThePatchedMethod() throws Exception {
        byte[] vanilla = memberTarget(false, CD_String);
        byte[] patched = memberTarget(true, CD_String);
        byte[] mixin = addingMixin(true);
        Path jar = jar(Map.of(MEMBER_CONFIG, MEMBER_CONFIG_TEXT, "m/MemberAccessor.class", mixin));

        HostPatchAdjustments adjustments = plan(jar, vanilla, patched);

        assertEquals(1, adjustments.findings().size(), adjustments.findings().toString());
        assertEquals("POLY-COMPAT-212", adjustments.findings().getFirst().code());
        assertFalse(adjustments.findings().getFirst().limited());
        ClassModel applied = ClassFile.of().parse(adjustments.apply("m.MemberAccessor", mixin));
        assertTrue(
                applied.methods().stream().noneMatch(m -> m.methodName().equalsString("describe")),
                applied.methods().toString());
        assertTrue(applied.methods().stream().anyMatch(m -> m.methodName().equalsString("helper")));
    }

    /**
     * A method a Fabric mixin adds that the host's patch added with other code (Fabric's tag API and Forge both add
     * {@code TagFile.remove()}, each for its own list): the mixin's method gets its own name, and the calls in the mod's
     * mixins follow it, so both the host's and the mod's method keep working.
     */
    @Test
    void aMethodThePatchAddedWithOtherCodeIsRenamedAndTheModsCallsFollowIt() throws Exception {
        byte[] vanilla = memberTarget(false, CD_String);
        byte[] patched = memberTarget(true, CD_String);
        byte[] mixin = addingMixin(false);
        byte[] caller = callerMixin();
        Path jar = jar(Map.of(
                MEMBER_CONFIG,
                "{\"package\":\"m\",\"mixins\":[\"MemberAccessor\",\"Caller\"]}",
                "m/MemberAccessor.class",
                mixin,
                "m/Caller.class",
                caller));

        HostPatchAdjustments adjustments = plan(jar, vanilla, patched);

        assertEquals(1, adjustments.findings().size(), adjustments.findings().toString());
        HostPatchAdjustments.Finding finding = adjustments.findings().getFirst();
        assertEquals("POLY-COMPAT-213", finding.code());
        assertTrue(finding.limited() && !finding.leftOut(), finding.toString());
        String renamed = "poly$MemberAccessor$describe";
        assertTrue(finding.reason().contains(renamed), finding.reason());
        ClassModel applied = ClassFile.of().parse(adjustments.apply("m.MemberAccessor", mixin));
        assertTrue(applied.methods().stream().anyMatch(m -> m.methodName().equalsString(renamed)));
        assertTrue(applied.methods().stream().noneMatch(m -> m.methodName().equalsString("describe")));
        // The mixin's own call and the other mixin's call (on the target class and through a method reference).
        assertEquals(List.of(renamed), calls(applied, "helper"));
        ClassModel appliedCaller = ClassFile.of().parse(adjustments.apply("m.Caller", caller));
        assertEquals(List.of(renamed, renamed), calls(appliedCaller, "call"));
    }

    /**
     * The host's patch renamed a local variable an injection captures by name with MixinExtras' {@code @Local}, and
     * gave it a subtype (Forge turns {@code SequencedSet values} of {@code TagLoader.tryBuildTag} into {@code
     * LinkedHashSet builder}, where Fabric's tag API removes entries). Mixin finds a local by name only when its type
     * is the parameter's, so the injection would silently not run; the {@code @Local} takes the patched name and the
     * parameter the patched type.
     */
    @Test
    void aLocalThePatchRenamedIsCapturedUnderItsPatchedNameAndType() throws Exception {
        byte[] vanilla = localTarget("values", CD_Collection);
        byte[] patched = localTarget("builder", CD_ArrayList);
        byte[] mixin = localMixin();
        Path jar = jar(Map.of(MEMBER_CONFIG, MEMBER_CONFIG_TEXT, "m/MemberAccessor.class", mixin));

        HostPatchAdjustments adjustments = plan(jar, vanilla, patched);

        assertEquals(1, adjustments.findings().size(), adjustments.findings().toString());
        HostPatchAdjustments.Finding finding = adjustments.findings().getFirst();
        assertEquals("POLY-COMPAT-212", finding.code(), finding.toString());
        assertFalse(finding.limited() || finding.leftOut(), finding.toString());
        ClassModel applied = ClassFile.of().parse(adjustments.apply("m.MemberAccessor", mixin));
        MethodModel handler = applied.methods().stream()
                .filter(method -> method.methodName().equalsString("onRun"))
                .findFirst()
                .orElseThrow();
        assertEquals(
                MethodTypeDesc.of(CD_void, CALLBACK_INFO, CD_ArrayList).descriptorString(),
                handler.methodType().stringValue());
        String parameters = handler.findAttribute(java.lang.classfile.Attributes.runtimeInvisibleParameterAnnotations())
                .orElseThrow()
                .parameterAnnotations()
                .toString();
        assertTrue(parameters.contains("constant=builder") && !parameters.contains("constant=values"), parameters);
    }

    /** No local of a usable type where the vanilla one was: the injection cannot run, which is now reported. */
    @Test
    void aLocalThePatchRemovedIsReported() throws Exception {
        byte[] vanilla = localTarget("values", CD_Collection);
        byte[] patched = localTarget("builder", CD_String);
        byte[] mixin = localMixin();
        Path jar = jar(Map.of(MEMBER_CONFIG, MEMBER_CONFIG_TEXT, "m/MemberAccessor.class", mixin));

        HostPatchAdjustments adjustments = plan(jar, vanilla, patched);

        assertEquals(1, adjustments.findings().size(), adjustments.findings().toString());
        HostPatchAdjustments.Finding finding = adjustments.findings().getFirst();
        assertEquals("POLY-COMPAT-138", finding.code());
        assertTrue(finding.limited() && !finding.leftOut() && finding.reason().contains("values"), finding.toString());
        ClassModel applied = ClassFile.of().parse(adjustments.apply("m.MemberAccessor", mixin));
        assertTrue(applied.methods().stream()
                .anyMatch(method -> method.methodType()
                        .equalsString(MethodTypeDesc.of(CD_void, CALLBACK_INFO, CD_Collection)
                                .descriptorString())));
    }

    /** The same, when the injection is already reported as not running for its point: reported once. */
    @Test
    void aLocalOfAnInjectionThatDoesNotRunIsNotReportedAgain() throws Exception {
        byte[] vanilla = localTarget("values", CD_Collection, true);
        byte[] patched = localTarget("builder", CD_String, false);
        byte[] mixin = localMixin("Lt/Target;old()V");
        Path jar = jar(Map.of(MEMBER_CONFIG, MEMBER_CONFIG_TEXT, "m/MemberAccessor.class", mixin));

        HostPatchAdjustments adjustments = plan(jar, vanilla, patched);

        assertEquals(1, adjustments.findings().size(), adjustments.findings().toString());
        assertTrue(
                adjustments.findings().getFirst().reason().contains("removed that invoke"),
                adjustments.findings().toString());
    }

    private static final ClassDesc CD_Collection = ClassDesc.of("java.util.Collection");
    private static final ClassDesc CD_ArrayList = ClassDesc.of("java.util.ArrayList");

    private static byte[] localTarget(String name, ClassDesc type) {
        return localTarget(name, type, false);
    }

    /**
     * {@code void run()} with one local at slot 1, {@code name} of {@code type}, in its local variable table; with
     * {@code call}, it calls {@code old()}.
     */
    private static byte[] localTarget(String name, ClassDesc type, boolean call) {
        return ClassFile.of()
                .build(
                        TARGET,
                        builder -> builder.withMethodBody(
                                "run", MethodTypeDesc.of(CD_void), ClassFile.ACC_PUBLIC, code -> {
                                    var start = code.newLabel();
                                    var end = code.newLabel();
                                    code.aconst_null().astore(1).labelBinding(start);
                                    if (call) {
                                        code.invokestatic(TARGET, "old", MethodTypeDesc.of(CD_void));
                                    } else {
                                        code.nop();
                                    }
                                    code.labelBinding(end).return_();
                                    code.localVariable(1, name, type, start, end);
                                }));
    }

    private static byte[] localMixin() {
        return localMixin(null);
    }

    /**
     * {@code @Inject(method = "run", at = @At("HEAD")) onRun(CallbackInfo, @Local(name = "values") Collection)}, or at
     * {@code INVOKE} {@code invoke}.
     */
    private static byte[] localMixin(String invoke) {
        Annotation at = invoke == null
                ? Annotation.of(
                        ClassDesc.ofDescriptor("Lorg/spongepowered/asm/mixin/injection/At;"),
                        AnnotationElement.ofString("value", "HEAD"))
                : Annotation.of(
                        ClassDesc.ofDescriptor("Lorg/spongepowered/asm/mixin/injection/At;"),
                        AnnotationElement.ofString("value", "INVOKE"),
                        AnnotationElement.ofString("target", invoke));
        Annotation inject = Annotation.of(
                ClassDesc.ofDescriptor("Lorg/spongepowered/asm/mixin/injection/Inject;"),
                AnnotationElement.of("method", AnnotationValue.ofArray(AnnotationValue.ofString("run"))),
                AnnotationElement.of("at", AnnotationValue.ofArray(AnnotationValue.ofAnnotation(at))));
        Annotation local = Annotation.of(
                ClassDesc.ofDescriptor("Lcom/llamalad7/mixinextras/sugar/Local;"),
                AnnotationElement.of("name", AnnotationValue.ofArray(AnnotationValue.ofString("values"))));
        return ClassFile.of().build(ClassDesc.of("m.MemberAccessor"), builder -> {
            builder.with(RuntimeInvisibleAnnotationsAttribute.of(mixinAnnotation()));
            builder.withMethod(
                    "onRun",
                    MethodTypeDesc.of(CD_void, CALLBACK_INFO, CD_Collection),
                    ClassFile.ACC_PRIVATE,
                    method -> {
                        method.with(RuntimeVisibleAnnotationsAttribute.of(inject));
                        method.with(java.lang.classfile.attribute.RuntimeInvisibleParameterAnnotationsAttribute.of(
                                List.of(List.of(), List.of(local))));
                        method.withCode(code -> code.aload(2)
                                .invokeinterface(
                                        CD_Collection,
                                        "isEmpty",
                                        MethodTypeDesc.of(java.lang.constant.ConstantDescs.CD_boolean))
                                .pop()
                                .return_());
                    });
        });
    }

    private static final String MEMBER_CONFIG = "member.mixins.json";
    private static final String MEMBER_CONFIG_TEXT = "{\"package\":\"m\",\"mixins\":[\"MemberAccessor\"]}";
    private static final ClassDesc CD_List = ClassDesc.of("java.util.List");
    private static final ClassDesc CD_Map = ClassDesc.of("java.util.Map");
    private static final MethodTypeDesc DESCRIBE = MethodTypeDesc.of(CD_String, CD_String);

    private HostPatchAdjustments plan(Path jar, byte[] vanilla, byte[] patched) {
        return HostPatchAdjustments.plan(
                new HostedLaunch("Host 1", List.of(), List.of(), directory),
                GameSide.DEDICATED_SERVER,
                Map.of(MEMBER_CONFIG, "example"),
                Map.of("example", jar),
                name -> name.equals("t/Target") ? Optional.of(vanilla) : Optional.empty(),
                classes(patched));
    }

    /**
     * Fields {@code connection} ({@code type}) and {@code server}, a method {@code flush(int)}; with {@code added}, the
     * host's methods {@code getConnection()} (returns {@code connection}), {@code callFlush(int)} (calls {@code
     * flush}) and {@code describe(String)} (calls {@code flush(1)}, returns its argument).
     */
    private static byte[] memberTarget(boolean added, ClassDesc type) {
        return memberTarget(added, type, true);
    }

    /** As above; without {@code accessorCode}, the host's {@code getConnection()} returns a constant instead. */
    private static byte[] memberTarget(boolean added, ClassDesc type, boolean accessorCode) {
        return ClassFile.of().build(TARGET, builder -> {
            builder.withField("connection", type, ClassFile.ACC_PRIVATE);
            builder.withField("server", CD_int, ClassFile.ACC_PRIVATE);
            builder.withField("builder", type, ClassFile.ACC_PRIVATE);
            builder.withMethodBody(
                    "flush", MethodTypeDesc.of(CD_void, CD_int), ClassFile.ACC_PRIVATE, code -> code.return_());
            if (added) {
                builder.withMethodBody("getConnection", MethodTypeDesc.of(type), ClassFile.ACC_PUBLIC, code -> {
                    if (accessorCode) {
                        code.aload(0).getfield(TARGET, "connection", type);
                    } else {
                        code.aconst_null();
                    }
                    code.areturn();
                });
                builder.withMethodBody(
                        "callFlush",
                        MethodTypeDesc.of(CD_void, CD_int),
                        ClassFile.ACC_PUBLIC,
                        code -> code.aload(0)
                                .iload(1)
                                .invokevirtual(TARGET, "flush", MethodTypeDesc.of(CD_void, CD_int))
                                .return_());
                builder.withMethodBody(
                        "describe",
                        DESCRIBE,
                        ClassFile.ACC_PUBLIC,
                        code -> code.aload(0)
                                .iconst_1()
                                .invokevirtual(TARGET, "flush", MethodTypeDesc.of(CD_void, CD_int))
                                .aload(1)
                                .areturn());
            }
        });
    }

    /**
     * The patch reordered a lambda's parameters and added one (Forge's {@code
     * SimpleJsonResourceReloadListener.lambda$scanDirectory$0}: {@code (Map, Identifier, Object)} becomes {@code (Codec,
     * Identifier, Map, Object)}; Fabric API's resource conditions skip data there). Each handler parameter's type is in
     * the patched parameters exactly once, so the bridge passes each on from where it now is.
     */
    @Test
    void anInjectionIntoAMethodWhoseParametersThePatchReorderedGetsABridge() throws Exception {
        byte[] vanilla = extractTarget(MethodTypeDesc.of(CD_void, CD_String, CD_float));
        byte[] patched = extractTarget(MethodTypeDesc.of(CD_void, CD_int, CD_float, CD_String));
        MethodTypeDesc handlerType = MethodTypeDesc.of(CD_void, CD_String, CD_float, CALLBACK_INFO);
        Path jar = jar(Map.of(EXTRACT_CONFIG, EXTRACT_CONFIG_TEXT, "m/ExtractMixin.class", extractMixin(handlerType)));

        HostPatchAdjustments adjustments = HostPatchAdjustments.plan(
                new HostedLaunch("Host 1", List.of(), List.of(), directory),
                GameSide.CLIENT,
                Map.of(EXTRACT_CONFIG, "example"),
                Map.of("example", jar),
                name -> name.equals("t/Target") ? Optional.of(vanilla) : Optional.empty(),
                classes(patched));

        assertEquals(1, adjustments.findings().size(), adjustments.findings().toString());
        assertTrue(
                adjustments.findings().getFirst().reason().contains("runs with the patched parameters"),
                adjustments.findings().toString());
        ClassModel applied = ClassFile.of().parse(adjustments.apply("m.ExtractMixin", extractMixin(handlerType)));
        MethodModel bridge = method(
                applied,
                "poly$hostParameters$onExtract",
                MethodTypeDesc.of(CD_void, CD_int, CD_float, CD_String, CALLBACK_INFO));
        // this, then the String (slot 3) and the float (slot 2) where the patch put them, then the CallbackInfo.
        assertEquals(List.of(0, 3, 2, 4), loadedSlots(bridge));
    }

    /**
     * ADR-0026 39: Fabric API's {@code ReloadableServerRegistriesMixin.modifyLootTable} takes a MixinExtras {@code @Local}
     * after its {@code CallbackInfoReturnable}; NeoForge reordered the lambda's parameters, so the injection runs through
     * a bridge. The bridge lost the {@code @Local}, and Mixin rejected its descriptor ({@code Invalid descriptor on
     * ...poly$hostParameters$modifyLootTable}), and with it every mixin on the class. The bridge keeps each parameter's
     * annotations where the parameter goes.
     */
    @Test
    void aBridgeKeepsTheHandlersParameterAnnotations() throws Exception {
        byte[] vanilla = extractTarget(MethodTypeDesc.of(CD_void, CD_String, CD_float));
        byte[] patched = extractTarget(MethodTypeDesc.of(CD_void, CD_float, CD_String));
        ClassDesc list = ClassDesc.of("java.util.List");
        MethodTypeDesc handlerType = MethodTypeDesc.of(CD_void, CD_String, CD_float, CALLBACK_INFO, list);
        byte[] mixin = extractMixinWithLocal(handlerType);
        Path jar = jar(Map.of(EXTRACT_CONFIG, EXTRACT_CONFIG_TEXT, "m/ExtractMixin.class", mixin));

        HostPatchAdjustments adjustments = HostPatchAdjustments.plan(
                new HostedLaunch("Host 1", List.of(), List.of(), directory),
                GameSide.CLIENT,
                Map.of(EXTRACT_CONFIG, "example"),
                Map.of("example", jar),
                name -> name.equals("t/Target") ? Optional.of(vanilla) : Optional.empty(),
                classes(patched));

        ClassModel applied = ClassFile.of().parse(adjustments.apply("m.ExtractMixin", mixin));
        MethodModel bridge = method(
                applied,
                "poly$hostParameters$onExtract",
                MethodTypeDesc.of(CD_void, CD_float, CD_String, CALLBACK_INFO, list));
        List<List<Annotation>> annotations = bridge.findAttribute(
                        java.lang.classfile.Attributes.runtimeInvisibleParameterAnnotations())
                .orElseThrow(() -> new AssertionError("the bridge has no parameter annotations"))
                .parameterAnnotations();
        assertEquals(4, annotations.size());
        assertEquals(List.of(), annotations.get(0));
        assertEquals(
                "Lcom/llamalad7/mixinextras/sugar/Local;",
                annotations.get(3).getFirst().className().stringValue());
    }

    private static final ClassDesc CD_Object = ClassDesc.of("java.lang.Object");

    private static final ClassDesc CODECS = ClassDesc.of("t.Codecs");
    private static final MethodTypeDesc VANILLA_CODEC = MethodTypeDesc.of(CD_Object, CD_String, CD_int);
    private static final MethodTypeDesc PATCHED_CODEC =
            MethodTypeDesc.of(CD_Object, CD_String, CD_int, ClassDesc.of("java.lang.Long"));

    /**
     * ADR-0026 40: NeoForge's patch calls {@code CustomPacketPayload.codec(fallback, types, protocol, flow)} where vanilla
     * calls {@code codec(fallback, types)}; Fabric API's {@code @WrapOperation}s on that call (which give the payload
     * codec Fabric's payload types) found nothing, so no Fabric payload could be sent. A wrap of a call the patch passes
     * more arguments to, at the end, runs through a bridge on the patched call that hands the handler an {@code
     * Operation} adding them back.
     */
    @Test
    void aWrapOfACallThePatchGaveTrailingArgumentsRunsThroughABridge() throws Exception {
        byte[] vanilla = codecTarget(VANILLA_CODEC);
        byte[] patched = codecTarget(PATCHED_CODEC);
        String configText = "{\"package\":\"m\",\"mixins\":[\"WrapCodecMixin\"]}";
        Path jar = jar(Map.of(CONFIG, configText, "m/WrapCodecMixin.class", wrapCodecMixin()));

        HostPatchAdjustments adjustments = HostPatchAdjustments.plan(
                new HostedLaunch("Host 1", List.of(), List.of(), directory),
                GameSide.DEDICATED_SERVER,
                Map.of(CONFIG, "example"),
                Map.of("example", jar),
                name -> name.equals("t/Target") ? Optional.of(vanilla) : Optional.empty(),
                classes(patched));

        assertEquals(1, adjustments.findings().size(), adjustments.findings().toString());
        String reason = adjustments.findings().getFirst().reason();
        assertTrue(reason.contains("Ljava/lang/Long;") && reason.contains("still runs"), reason);
        ClassModel applied = ClassFile.of().parse(adjustments.apply("m.WrapCodecMixin", wrapCodecMixin()));
        MethodTypeDesc handler = MethodTypeDesc.of(CD_Object, CD_String, CD_int, OPERATION);
        assertFalse(annotationsOf(method(applied, "wrapCodec", handler)).contains("WrapOperation"));
        MethodModel bridge = method(
                applied,
                "poly$hostArguments$wrapCodec",
                MethodTypeDesc.of(CD_Object, CD_String, CD_int, ClassDesc.of("java.lang.Long"), OPERATION));
        String bridgeAnnotations = annotationsOf(bridge);
        assertTrue(
                bridgeAnnotations.contains("WrapOperation")
                        && bridgeAnnotations.contains(
                                "Lt/Codecs;codec(Ljava/lang/String;ILjava/lang/Long;)Ljava/lang/Object;"),
                bridgeAnnotations);
        List<String> invoked = bridge.code().orElseThrow().elementList().stream()
                .filter(element -> element instanceof InvokeInstruction)
                .map(element -> ((InvokeInstruction) element).owner().asInternalName() + "."
                        + ((InvokeInstruction) element).name().stringValue())
                .toList();
        assertTrue(
                invoked.contains("dev/polymodloader/compat/fabric/mixin/AppendedArguments.<init>")
                        && invoked.contains("m/WrapCodecMixin.wrapCodec"),
                invoked.toString());
    }

    @Test
    void appendedArgumentsHandTheWrappedCallItsTrailingArguments() {
        List<Object> seen = new java.util.ArrayList<>();
        com.llamalad7.mixinextras.injector.wrapoperation.Operation<Object> original = arguments -> {
            seen.addAll(List.of(arguments));
            return "result";
        };

        Object result = new AppendedArguments(original, new Object[] {7L}).call("types", 3);

        assertEquals("result", result);
        assertEquals(List.of("types", 3, 7L), seen);
    }

    private static byte[] codecTarget(MethodTypeDesc codec) {
        return ClassFile.of()
                .build(
                        TARGET,
                        builder -> builder.withMethodBody(
                                "build",
                                MethodTypeDesc.of(CD_Object),
                                ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                                code -> {
                                    code.ldc("types");
                                    code.iconst_3();
                                    if (codec.parameterCount() == 3) {
                                        code.ldc(7L);
                                        code.invokestatic(
                                                ClassDesc.of("java.lang.Long"),
                                                "valueOf",
                                                MethodTypeDesc.of(
                                                        ClassDesc.of("java.lang.Long"),
                                                        java.lang.constant.ConstantDescs.CD_long));
                                    }
                                    code.invokestatic(CODECS, "codec", codec);
                                    code.areturn();
                                }));
    }

    private static byte[] wrapCodecMixin() {
        Annotation wrap = Annotation.of(
                ClassDesc.ofDescriptor("Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;"),
                AnnotationElement.of("method", AnnotationValue.ofArray(AnnotationValue.ofString("build"))),
                AnnotationElement.of(
                        "at",
                        AnnotationValue.ofArray(AnnotationValue.ofAnnotation(Annotation.of(
                                ClassDesc.ofDescriptor("Lorg/spongepowered/asm/mixin/injection/At;"),
                                AnnotationElement.ofString("value", "INVOKE"),
                                AnnotationElement.ofString(
                                        "target", "Lt/Codecs;codec(Ljava/lang/String;I)Ljava/lang/Object;"))))));
        return ClassFile.of().build(ClassDesc.of("m.WrapCodecMixin"), builder -> {
            builder.with(RuntimeInvisibleAnnotationsAttribute.of(mixinAnnotation()));
            builder.withMethod(
                    "wrapCodec",
                    MethodTypeDesc.of(CD_Object, CD_String, CD_int, OPERATION),
                    ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC,
                    method -> {
                        method.with(RuntimeVisibleAnnotationsAttribute.of(wrap));
                        method.withCode(code -> {
                            code.aload(2);
                            code.iconst_2();
                            code.anewarray(CD_Object);
                            code.dup();
                            code.iconst_0();
                            code.aload(0);
                            code.aastore();
                            code.dup();
                            code.iconst_1();
                            code.iload(1);
                            code.invokestatic(
                                    ClassDesc.of("java.lang.Integer"),
                                    "valueOf",
                                    MethodTypeDesc.of(ClassDesc.of("java.lang.Integer"), CD_int));
                            code.aastore();
                            code.invokeinterface(
                                    OPERATION, "call", MethodTypeDesc.of(CD_Object, CD_Object.arrayType()));
                            code.areturn();
                        });
                    });
        });
    }

    /** {@link #extractMixin} with a MixinExtras {@code @Local} on the handler's last parameter. */
    private static byte[] extractMixinWithLocal(MethodTypeDesc handlerType) {
        ClassModel plain = ClassFile.of().parse(extractMixin(handlerType));
        List<List<Annotation>> parameters = new java.util.ArrayList<>();
        for (int index = 0; index < handlerType.parameterCount(); index++) {
            parameters.add(
                    index == handlerType.parameterCount() - 1
                            ? List.of(Annotation.of(ClassDesc.ofDescriptor("Lcom/llamalad7/mixinextras/sugar/Local;")))
                            : List.of());
        }
        return ClassFile.of().transformClass(plain, (builder, element) -> {
            if (element instanceof MethodModel method && method.methodName().equalsString("onExtract")) {
                builder.transformMethod(method, (methodBuilder, methodElement) -> {
                    methodBuilder.with(methodElement);
                    if (methodElement instanceof java.lang.classfile.CodeModel) {
                        methodBuilder.with(
                                java.lang.classfile.attribute.RuntimeInvisibleParameterAnnotationsAttribute.of(
                                        parameters));
                    }
                });
            } else {
                builder.with(element);
            }
        });
    }

    /** A handler parameter whose type the patched method has twice cannot be placed: that injection is taken out. */
    @Test
    void anInjectionWhoseReorderedParametersAreAmbiguousIsTakenOut() throws Exception {
        byte[] vanilla = extractTarget(MethodTypeDesc.of(CD_void, CD_String, CD_float));
        byte[] patched = extractTarget(MethodTypeDesc.of(CD_void, CD_float, CD_String, CD_String));
        MethodTypeDesc handlerType = MethodTypeDesc.of(CD_void, CD_String, CD_float, CALLBACK_INFO);
        Path jar = jar(Map.of(EXTRACT_CONFIG, EXTRACT_CONFIG_TEXT, "m/ExtractMixin.class", extractMixin(handlerType)));

        HostPatchAdjustments adjustments = HostPatchAdjustments.plan(
                new HostedLaunch("Host 1", List.of(), List.of(), directory),
                GameSide.CLIENT,
                Map.of(EXTRACT_CONFIG, "example"),
                Map.of("example", jar),
                name -> name.equals("t/Target") ? Optional.of(vanilla) : Optional.empty(),
                classes(patched));

        assertTrue(
                adjustments.findings().getFirst().reason().contains("does not run"),
                adjustments.findings().toString());
    }

    /**
     * The host's replacement call has another shape than the call it replaced: an {@code @Inject} moves there (it only
     * needs the place), a {@code @Redirect} written for the old call's arguments and result does not.
     */
    @Test
    void onlyAnInjectMovesToAReplacementOfAnotherShape() throws Exception {
        byte[] vanilla = target(true);
        byte[] patched = target(false);
        byte[] mixin = mixin(
                "m.InjectMixin",
                List.of(inject("first", "Lt/Target;moved()V"), new Handler("second", redirect("Lt/Target;moved()V"))));
        Path jar =
                jar(Map.of(CONFIG, "{\"package\":\"m\",\"mixins\":[\"InjectMixin\"]}", "m/InjectMixin.class", mixin));
        HostedLaunch host = new HostedLaunch(
                "Host 1",
                List.of(),
                List.of(new InjectionPointReplacement(
                        "t/Target", "run", "Lt/Target;moved()V", "Lt/Target;replacement(I)V")),
                directory);

        HostPatchAdjustments adjustments = HostPatchAdjustments.plan(
                host,
                GameSide.DEDICATED_SERVER,
                Map.of(CONFIG, "example"),
                Map.of("example", jar),
                name -> name.equals("t/Target") ? Optional.of(vanilla) : Optional.empty(),
                classes(patchedWithReplacementTakingAnInt()));

        String findings = adjustments.findings().toString();
        assertTrue(findings.contains("@Inject first") && findings.contains("now applies"), findings);
        assertTrue(findings.contains("@Redirect second") && findings.contains("does not run"), findings);
    }

    /**
     * The host's patch moved the call an {@code @Inject} runs before into a helper of the same class that makes it
     * exactly when it returns true (Forge's {@code ServerPlayerGameMode.removeBlock}, which now calls {@code
     * Block.destroy}; Fabric API's {@code PlayerBlockBreakEvents.AFTER} runs before that call). The handler stays a plain
     * method; a MixinExtras {@code @WrapOperation} bridge on each helper call runs it after a true result, with the
     * target method's parameters and the handler's own {@code @Local} values.
     */
    @Test
    void anInjectionBeforeACallMovedIntoAHelperRunsAfterTheHelperReturnsTrue() throws Exception {
        MethodTypeDesc handlerType = MethodTypeDesc.of(CD_void, CD_String, CALLBACK_INFO_RETURNABLE, CD_int);
        byte[] mixin = helperMixin(handlerType, false);
        Path jar =
                jar(Map.of(CONFIG, "{\"package\":\"m\",\"mixins\":[\"HelperMixin\"]}", "m/HelperMixin.class", mixin));

        HostPatchAdjustments adjustments = HostPatchAdjustments.plan(
                helperHost(),
                GameSide.DEDICATED_SERVER,
                Map.of(CONFIG, "example"),
                Map.of("example", jar),
                name -> name.equals("t/Target") ? Optional.of(helperTarget(true)) : Optional.empty(),
                classes(helperTarget(false)));

        assertEquals(1, adjustments.findings().size(), adjustments.findings().toString());
        assertTrue(
                adjustments.findings().getFirst().reason().contains("runs after each call of")
                        && adjustments.findings().getFirst().reason().contains("Lt/Target;helper(Ljava/lang/String;)Z"),
                adjustments.findings().toString());
        byte[] applied = adjustments.apply("m.HelperMixin", mixin);
        assertEquals(List.of(), ClassFile.of().verify(applied), "the changed mixin class verifies");
        ClassModel model = ClassFile.of().parse(applied);
        assertFalse(annotationsOf(method(model, "onMoved", handlerType)).contains("Inject"));
        MethodModel bridge = method(
                model,
                "poly$hostCall$onMoved",
                MethodTypeDesc.of(CD_boolean, TARGET, CD_String, OPERATION, CD_String, CD_int));
        String annotations = annotationsOf(bridge);
        assertTrue(
                annotations.contains("WrapOperation")
                        && annotations.contains("run(Ljava/lang/String;)Z")
                        && annotations.contains("Lt/Target;helper(Ljava/lang/String;)Z")
                        && annotations.contains("require"),
                annotations);
        String parameterAnnotations = bridge.findAttribute(
                        java.lang.classfile.Attributes.runtimeInvisibleParameterAnnotations())
                .map(attribute -> attribute.parameterAnnotations().toString())
                .orElse("[]");
        assertTrue(parameterAnnotations.contains("Local"), parameterAnnotations);
        String calls = bridge.code().orElseThrow().elementList().stream()
                .filter(element -> element instanceof InvokeInstruction)
                .map(Object::toString)
                .toList()
                .toString();
        assertTrue(
                calls.contains("Operation") && calls.contains("onMoved") && calls.contains("CallbackInfoReturnable"),
                calls);
    }

    /** A cancellable injection cannot run after the helper: cancelling there would not skip the moved call. */
    @Test
    void aCancellableInjectionBeforeACallMovedIntoAHelperDoesNotRun() throws Exception {
        MethodTypeDesc handlerType = MethodTypeDesc.of(CD_void, CD_String, CALLBACK_INFO_RETURNABLE, CD_int);
        byte[] mixin = helperMixin(handlerType, true);
        Path jar =
                jar(Map.of(CONFIG, "{\"package\":\"m\",\"mixins\":[\"HelperMixin\"]}", "m/HelperMixin.class", mixin));

        HostPatchAdjustments adjustments = HostPatchAdjustments.plan(
                helperHost(),
                GameSide.DEDICATED_SERVER,
                Map.of(CONFIG, "example"),
                Map.of("example", jar),
                name -> name.equals("t/Target") ? Optional.of(helperTarget(true)) : Optional.empty(),
                classes(helperTarget(false)));

        assertTrue(
                adjustments.findings().getFirst().reason().contains("does not run"),
                adjustments.findings().toString());
        ClassModel model = ClassFile.of().parse(adjustments.apply("m.HelperMixin", mixin));
        assertEquals(1, model.methods().size());
    }

    /**
     * A mixin adds an interface whose default method the host's patch already gives the class through its own interface
     * (Fabric API's {@code FabricItemStack.getCraftingRemainder()} and Forge's {@code
     * IForgeItemStack.getCraftingRemainder()} on {@code ItemStack}). The JVM rejects every call of such a method
     * ("Conflicting default methods"), so crafting would fail. The class keeps the host's default: the mixin gets a
     * method that calls it, and overrides in subclasses still win.
     */
    @Test
    void aDefaultMethodBothTheHostsAndAModsInterfaceGiveTheClassCallsTheHostOne() throws Exception {
        MethodTypeDesc remainder = MethodTypeDesc.of(CD_String);
        byte[] hostApi = defaultInterface("h.HostApi", "remainder", remainder);
        byte[] modApi = defaultInterface("m.ModApi", "remainder", remainder);
        byte[] patched =
                ClassFile.of().build(TARGET, builder -> builder.withInterfaceSymbols(ClassDesc.of("h.HostApi")));
        byte[] vanilla = ClassFile.of().build(TARGET, builder -> {});
        byte[] mixin = ClassFile.of().build(ClassDesc.of("m.StackMixin"), builder -> {
            builder.with(RuntimeInvisibleAnnotationsAttribute.of(mixinAnnotation()));
            builder.withInterfaceSymbols(ClassDesc.of("m.ModApi"));
        });
        Path jar = jar(Map.of(
                CONFIG,
                "{\"package\":\"m\",\"mixins\":[\"StackMixin\"]}",
                "m/StackMixin.class",
                mixin,
                "m/ModApi.class",
                modApi));

        HostPatchAdjustments adjustments = HostPatchAdjustments.plan(
                new HostedLaunch("Host 1", List.of(), List.of(), directory),
                GameSide.DEDICATED_SERVER,
                Map.of(CONFIG, "example"),
                Map.of("example", jar),
                name -> name.equals("t/Target") ? Optional.of(vanilla) : Optional.empty(),
                classes(Map.of("t/Target", patched, "h/HostApi", hostApi)));

        assertEquals(1, adjustments.findings().size(), adjustments.findings().toString());
        String reason = adjustments.findings().getFirst().reason();
        assertTrue(reason.contains("remainder()Ljava/lang/String;") && reason.contains("h/HostApi"), reason);
        byte[] applied = adjustments.apply("m.StackMixin", mixin);
        MethodModel added = method(ClassFile.of().parse(applied), "remainder", remainder);
        assertTrue((added.flags().flagsMask() & ClassFile.ACC_PUBLIC) != 0);
        String calls = added.code().orElseThrow().elementList().stream()
                .filter(element -> element instanceof InvokeInstruction)
                .map(Object::toString)
                .toList()
                .toString();
        assertTrue(calls.contains("INVOKESPECIAL") && calls.contains("h/HostApi.remainder"), calls);
    }

    /** No conflict when the class itself has the method: nothing is added. */
    @Test
    void aDefaultMethodTheClassDeclaresIsNoConflict() throws Exception {
        MethodTypeDesc remainder = MethodTypeDesc.of(CD_String);
        byte[] hostApi = defaultInterface("h.HostApi", "remainder", remainder);
        byte[] modApi = defaultInterface("m.ModApi", "remainder", remainder);
        byte[] patched = ClassFile.of().build(TARGET, builder -> {
            builder.withInterfaceSymbols(ClassDesc.of("h.HostApi"));
            builder.withMethodBody("remainder", remainder, ClassFile.ACC_PUBLIC, code -> {
                code.aconst_null();
                code.areturn();
            });
        });
        byte[] mixin = ClassFile.of().build(ClassDesc.of("m.StackMixin"), builder -> {
            builder.with(RuntimeInvisibleAnnotationsAttribute.of(mixinAnnotation()));
            builder.withInterfaceSymbols(ClassDesc.of("m.ModApi"));
        });
        Path jar = jar(Map.of(
                CONFIG,
                "{\"package\":\"m\",\"mixins\":[\"StackMixin\"]}",
                "m/StackMixin.class",
                mixin,
                "m/ModApi.class",
                modApi));

        HostPatchAdjustments adjustments = HostPatchAdjustments.plan(
                new HostedLaunch("Host 1", List.of(), List.of(), directory),
                GameSide.DEDICATED_SERVER,
                Map.of(CONFIG, "example"),
                Map.of("example", jar),
                name -> name.equals("t/Target")
                        ? Optional.of(ClassFile.of().build(TARGET, builder -> {}))
                        : Optional.empty(),
                classes(Map.of("t/Target", patched, "h/HostApi", hostApi)));

        assertEquals(List.of(), adjustments.findings());
    }

    private static byte[] defaultInterface(String name, String method, MethodTypeDesc type) {
        return ClassFile.of().build(ClassDesc.of(name), builder -> {
            builder.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_INTERFACE | ClassFile.ACC_ABSTRACT);
            builder.withMethodBody(method, type, ClassFile.ACC_PUBLIC, code -> {
                code.ldc(name);
                code.areturn();
            });
        });
    }

    private static GameClassBytes classes(Map<String, byte[]> patched) {
        return new GameClassBytes() {
            @Override
            public Optional<byte[]> classBytes(String className) {
                return Optional.ofNullable(patched.get(className.replace('.', '/')));
            }

            @Override
            public boolean isDefined(String className) {
                return false;
            }
        };
    }

    private static final ClassDesc CALLBACK_INFO_RETURNABLE =
            ClassDesc.ofDescriptor("Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;");
    private static final ClassDesc OPERATION =
            ClassDesc.ofDescriptor("Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;");

    private HostedLaunch helperHost() {
        return new HostedLaunch(
                "Host 1",
                List.of(),
                List.of(new InjectionPointReplacement(
                        "t/Target",
                        "run",
                        "Lt/Target;moved()V",
                        "Lt/Target;helper(Ljava/lang/String;)Z",
                        InjectionPointReplacement.Placement.AFTER_TRUE_RESULT)),
                directory);
    }

    /**
     * {@code boolean run(String)}: vanilla calls {@code moved()} itself; patched calls {@code helper(String)}, which calls
     * {@code moved()} and returns true.
     */
    private static byte[] helperTarget(boolean vanilla) {
        MethodTypeDesc run = MethodTypeDesc.of(CD_boolean, CD_String);
        MethodTypeDesc helper = MethodTypeDesc.of(CD_boolean, CD_String);
        return ClassFile.of().build(TARGET, builder -> {
            builder.withMethodBody("run", run, ClassFile.ACC_PUBLIC, code -> {
                if (vanilla) {
                    code.invokestatic(TARGET, "moved", MethodTypeDesc.of(CD_void));
                } else {
                    code.aload(0);
                    code.aload(1);
                    code.invokevirtual(TARGET, "helper", helper);
                    code.pop();
                }
                code.iconst_1();
                code.ireturn();
            });
            if (!vanilla) {
                builder.withMethodBody("helper", helper, ClassFile.ACC_PRIVATE, code -> {
                    code.invokestatic(TARGET, "moved", MethodTypeDesc.of(CD_void));
                    code.iconst_1();
                    code.ireturn();
                });
            }
        });
    }

    private static Annotation generator(String type) {
        return Annotation.of(ClassDesc.ofDescriptor("Lorg/spongepowered/asm/mixin/gen/" + type + ";"));
    }

    /** An accessor mixin: {@code @Accessor getConnection()}, {@code @Accessor getServer()}, {@code @Invoker callFlush}. */
    private static byte[] accessorInterface() {
        return ClassFile.of().build(ClassDesc.of("m.MemberAccessor"), builder -> {
            builder.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_INTERFACE | ClassFile.ACC_ABSTRACT);
            builder.with(RuntimeInvisibleAnnotationsAttribute.of(mixinAnnotation()));
            int flags = ClassFile.ACC_PUBLIC | ClassFile.ACC_ABSTRACT;
            builder.withMethod(
                    "getConnection",
                    MethodTypeDesc.of(CD_String),
                    flags,
                    method -> method.with(RuntimeVisibleAnnotationsAttribute.of(generator("Accessor"))));
            builder.withMethod(
                    "getServer",
                    MethodTypeDesc.of(CD_int),
                    flags,
                    method -> method.with(RuntimeVisibleAnnotationsAttribute.of(generator("Accessor"))));
            builder.withMethod(
                    "callFlush",
                    MethodTypeDesc.of(CD_void, CD_int),
                    flags,
                    method -> method.with(RuntimeVisibleAnnotationsAttribute.of(generator("Invoker"))));
        });
    }

    /** An accessor mixin: {@code @Accessor List getBuilder()} and {@code @Accessor getServer()}. */
    private static byte[] fieldAccessorInterface() {
        return ClassFile.of().build(ClassDesc.of("m.MemberAccessor"), builder -> {
            builder.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_INTERFACE | ClassFile.ACC_ABSTRACT);
            builder.with(RuntimeInvisibleAnnotationsAttribute.of(mixinAnnotation()));
            int flags = ClassFile.ACC_PUBLIC | ClassFile.ACC_ABSTRACT;
            builder.withMethod(
                    "getBuilder",
                    MethodTypeDesc.of(CD_List),
                    flags,
                    method -> method.with(RuntimeVisibleAnnotationsAttribute.of(generator("Accessor"))));
            builder.withMethod(
                    "getServer",
                    MethodTypeDesc.of(CD_int),
                    flags,
                    method -> method.with(RuntimeVisibleAnnotationsAttribute.of(generator("Accessor"))));
        });
    }

    /** A class implementing {@code m.MemberAccessor} with {@code getServer} only, as the target is after Mixin. */
    private static byte[] implementation() {
        return ClassFile.of().build(ClassDesc.of("m.Implementation"), builder -> {
            builder.withFlags(ClassFile.ACC_PUBLIC);
            builder.withInterfaceSymbols(ClassDesc.of("m.MemberAccessor"));
            builder.withMethodBody(
                    "<init>",
                    MethodTypeDesc.of(CD_void),
                    ClassFile.ACC_PUBLIC,
                    code -> code.aload(0)
                            .invokespecial(ClassDesc.of("java.lang.Object"), "<init>", MethodTypeDesc.of(CD_void))
                            .return_());
            builder.withMethodBody(
                    "getServer",
                    MethodTypeDesc.of(CD_int),
                    ClassFile.ACC_PUBLIC,
                    code -> code.iconst_0().ireturn());
        });
    }

    /**
     * A mixin that adds {@code describe(String)}: with {@code sameCode} the host's code (through the mixin's own class,
     * as javac writes calls to shadowed methods), else returning the argument without the call; and {@code helper},
     * which calls {@code describe}.
     */
    private static byte[] addingMixin(boolean sameCode) {
        ClassDesc self = ClassDesc.of("m.MemberAccessor");
        return ClassFile.of().build(self, builder -> {
            builder.with(RuntimeInvisibleAnnotationsAttribute.of(mixinAnnotation()));
            builder.withMethod(
                    "flush",
                    MethodTypeDesc.of(CD_void, CD_int),
                    ClassFile.ACC_PRIVATE | ClassFile.ACC_ABSTRACT,
                    method -> method.with(RuntimeVisibleAnnotationsAttribute.of(
                            Annotation.of(ClassDesc.ofDescriptor("Lorg/spongepowered/asm/mixin/Shadow;")))));
            builder.withMethodBody("describe", DESCRIBE, ClassFile.ACC_PUBLIC, code -> {
                if (sameCode) {
                    code.aload(0).iconst_1().invokevirtual(self, "flush", MethodTypeDesc.of(CD_void, CD_int));
                }
                code.aload(1).areturn();
            });
            builder.withMethodBody(
                    "helper",
                    MethodTypeDesc.of(CD_String),
                    ClassFile.ACC_PUBLIC,
                    code -> code.aload(0)
                            .ldc("x")
                            .invokevirtual(self, "describe", DESCRIBE)
                            .areturn());
        });
    }

    /** Another mixin of the mod: calls {@code t.Target.describe} directly and through a method reference. */
    private static byte[] callerMixin() {
        return ClassFile.of().build(ClassDesc.of("m.Caller"), builder -> {
            builder.with(RuntimeInvisibleAnnotationsAttribute.of(Annotation.of(
                    ClassDesc.ofDescriptor("Lorg/spongepowered/asm/mixin/Mixin;"),
                    AnnotationElement.of(
                            "value", AnnotationValue.ofArray(AnnotationValue.ofClass(ClassDesc.of("t.Other")))))));
            builder.withMethodBody(
                    "call",
                    MethodTypeDesc.of(CD_String, TARGET),
                    ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC,
                    code -> code.aload(0)
                            .ldc("x")
                            .invokevirtual(TARGET, "describe", DESCRIBE)
                            .pop()
                            .ldc(java.lang.constant.MethodHandleDesc.ofMethod(
                                    java.lang.constant.DirectMethodHandleDesc.Kind.VIRTUAL,
                                    TARGET,
                                    "describe",
                                    DESCRIBE))
                            .pop()
                            .ldc("done")
                            .areturn());
        });
    }

    /**
     * The names of the target's or mixin's methods {@code method} calls, or refers to through a method handle
     * constant.
     */
    private static List<String> calls(ClassModel model, String method) {
        return model.methods().stream()
                .filter(candidate -> candidate.methodName().equalsString(method))
                .flatMap(candidate -> candidate.code().orElseThrow().elementList().stream())
                .flatMap(element -> {
                    if (element instanceof InvokeInstruction call
                            && List.of("t/Target", "m/MemberAccessor")
                                    .contains(call.owner().asInternalName())) {
                        return java.util.stream.Stream.of(call.name().stringValue());
                    }
                    if (element instanceof java.lang.classfile.instruction.ConstantInstruction constant
                            && constant.constantValue() instanceof java.lang.constant.DirectMethodHandleDesc handle) {
                        return java.util.stream.Stream.of(handle.methodName());
                    }
                    return java.util.stream.Stream.empty();
                })
                .toList();
    }

    /** Defines the given classes from bytes, everything else from the test's own loader. */
    private static final class ByteClassLoader extends ClassLoader {

        private final Map<String, byte[]> classes;

        ByteClassLoader(Map<String, byte[]> classes) {
            super(HostPatchAdjustmentsTest.class.getClassLoader());
            this.classes = classes;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            byte[] bytes = classes.get(name);
            if (bytes == null) {
                throw new ClassNotFoundException(name);
            }
            return defineClass(name, bytes, 0, bytes.length);
        }
    }

    /**
     * {@code @Inject(method = "run", at = @At(value = "INVOKE", target = "Lt/Target;moved()V"))} on {@code onMoved(String,
     * CallbackInfoReturnable, @Local(name = "count") int)}.
     */
    private static byte[] helperMixin(MethodTypeDesc handlerType, boolean cancellable) {
        List<AnnotationElement> elements = new java.util.ArrayList<>(List.of(
                AnnotationElement.of("method", AnnotationValue.ofArray(AnnotationValue.ofString("run"))),
                AnnotationElement.of(
                        "at",
                        AnnotationValue.ofArray(AnnotationValue.ofAnnotation(Annotation.of(
                                ClassDesc.ofDescriptor("Lorg/spongepowered/asm/mixin/injection/At;"),
                                AnnotationElement.ofString("value", "INVOKE"),
                                AnnotationElement.ofString("target", "Lt/Target;moved()V")))))));
        if (cancellable) {
            elements.add(AnnotationElement.ofBoolean("cancellable", true));
        }
        Annotation inject =
                Annotation.of(ClassDesc.ofDescriptor("Lorg/spongepowered/asm/mixin/injection/Inject;"), elements);
        Annotation local = Annotation.of(
                ClassDesc.ofDescriptor("Lcom/llamalad7/mixinextras/sugar/Local;"),
                AnnotationElement.of("name", AnnotationValue.ofArray(AnnotationValue.ofString("count"))));
        return ClassFile.of().build(ClassDesc.of("m.HelperMixin"), builder -> {
            builder.with(RuntimeInvisibleAnnotationsAttribute.of(mixinAnnotation()));
            builder.withMethod("onMoved", handlerType, ClassFile.ACC_PRIVATE, method -> {
                method.with(RuntimeVisibleAnnotationsAttribute.of(inject));
                method.with(java.lang.classfile.attribute.RuntimeInvisibleParameterAnnotationsAttribute.of(
                        List.of(List.of(), List.of(), List.of(local))));
                method.withCode(code -> code.return_());
            });
        });
    }

    private static byte[] patchedWithReplacementTakingAnInt() {
        return ClassFile.of().build(TARGET, builder -> {
            builder.withMethodBody("run", MethodTypeDesc.of(CD_void), 0, code -> {
                code.iconst_1();
                code.invokestatic(TARGET, "replacement", MethodTypeDesc.of(CD_void, CD_int));
                code.return_();
            });
        });
    }

    private static Annotation redirect(String target) {
        return Annotation.of(
                ClassDesc.ofDescriptor("Lorg/spongepowered/asm/mixin/injection/Redirect;"),
                AnnotationElement.of("method", AnnotationValue.ofArray(AnnotationValue.ofString("run"))),
                AnnotationElement.of(
                        "at",
                        AnnotationValue.ofAnnotation(Annotation.of(
                                ClassDesc.ofDescriptor("Lorg/spongepowered/asm/mixin/injection/At;"),
                                AnnotationElement.ofString("value", "INVOKE"),
                                AnnotationElement.ofString("target", target)))));
    }

    private static List<Integer> loadedSlots(MethodModel method) {
        return method.code().orElseThrow().elementList().stream()
                .filter(element -> element instanceof java.lang.classfile.instruction.LoadInstruction)
                .map(element -> ((java.lang.classfile.instruction.LoadInstruction) element).slot())
                .toList();
    }

    @Test
    void withoutAHostNothingChanges() {
        HostPatchAdjustments adjustments = HostPatchAdjustments.plan(
                HostedLaunch.NONE,
                GameSide.DEDICATED_SERVER,
                Map.of(CONFIG, "example"),
                Map.of(),
                name -> Optional.empty(),
                classes(new byte[0]));

        byte[] mixin = injectMixin();
        assertEquals(HostPatchAdjustments.NONE, adjustments);
        assertEquals(mixin, adjustments.apply("m.InjectMixin", mixin));
    }

    private static final String EXTRACT_CONFIG = "extract.mixins.json";
    private static final String EXTRACT_CONFIG_TEXT = "{\"package\":\"m\",\"client\":[\"ExtractMixin\"]}";
    private static final ClassDesc CALLBACK_INFO =
            ClassDesc.ofDescriptor("Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;");

    private static byte[] extractTarget(MethodTypeDesc type) {
        return ClassFile.of()
                .build(
                        TARGET,
                        builder ->
                                builder.withMethodBody("extract", type, ClassFile.ACC_PUBLIC, code -> code.return_()));
    }

    /** {@code @Inject(method = "extract", at = @At("HEAD"))} on an instance handler that reads its parameters. */
    private static byte[] extractMixin(MethodTypeDesc handlerType) {
        Annotation inject = Annotation.of(
                ClassDesc.ofDescriptor("Lorg/spongepowered/asm/mixin/injection/Inject;"),
                AnnotationElement.of("method", AnnotationValue.ofArray(AnnotationValue.ofString("extract"))),
                AnnotationElement.of(
                        "at",
                        AnnotationValue.ofArray(AnnotationValue.ofAnnotation(Annotation.of(
                                ClassDesc.ofDescriptor("Lorg/spongepowered/asm/mixin/injection/At;"),
                                AnnotationElement.ofString("value", "HEAD"))))));
        return ClassFile.of().build(ClassDesc.of("m.ExtractMixin"), builder -> {
            builder.with(RuntimeInvisibleAnnotationsAttribute.of(mixinAnnotation()));
            builder.withMethod("onExtract", handlerType, ClassFile.ACC_PRIVATE, method -> {
                method.with(RuntimeVisibleAnnotationsAttribute.of(inject));
                method.withCode(code -> {
                    code.aload(1);
                    code.pop();
                    code.fload(2);
                    code.pop();
                    code.return_();
                });
            });
        });
    }

    private static MethodModel method(ClassModel model, String name, MethodTypeDesc type) {
        return model.methods().stream()
                .filter(method -> method.methodName().equalsString(name)
                        && method.methodType().equalsString(type.descriptorString()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no method " + name + type.descriptorString()));
    }

    private static final ClassDesc EXTENSIONS = ClassDesc.of("m.Extensions");
    private static final ClassDesc GONE_ACCESSOR = ClassDesc.of("m.GoneAccessor");
    private static final ClassDesc USER_MIXIN = ClassDesc.of("m.UserMixin");
    private static final String COUPLED_CONFIG_TEXT =
            "{\"package\":\"m\",\"client\":[\"ExtensionsMixin\",\"GoneAccessor\",\"UserMixin\"]}";

    /**
     * ADR-0026 39: Fabric API's {@code GuiRendererMixin} adds {@code GuiRendererExtensions} to {@code GuiRenderer} and is
     * left out under NeoForge (its {@code @Shadow} field is not in NeoForge's {@code GuiRenderer}), while {@code
     * GameRendererMixin.guiRendererReady} casts the game's {@code GuiRenderer} to that interface: the client stopped with
     * a {@code ClassCastException} ({@code corpusFabricNeoForgeClient}). An injection that uses an interface a left-out
     * mixin adds, or a left-out accessor mixin, must not run.
     */
    @Test
    void anInjectionUsingTheInterfaceOfALeftOutMixinDoesNotRun() throws Exception {
        byte[] vanilla = target(true);
        byte[] patched = target(false);
        Path jar = jar(Map.of(
                CONFIG,
                COUPLED_CONFIG_TEXT,
                "m/Extensions.class",
                extensionsInterface(),
                "m/ExtensionsMixin.class",
                extensionsMixin(),
                "m/GoneAccessor.class",
                goneAccessor(),
                "m/UserMixin.class",
                userMixin()));

        HostPatchAdjustments adjustments = HostPatchAdjustments.plan(
                new HostedLaunch("Host 1", List.of(), List.of(), directory),
                GameSide.CLIENT,
                Map.of(CONFIG, "example"),
                Map.of("example", jar),
                name -> name.equals("t/Target") ? Optional.of(vanilla) : Optional.empty(),
                classes(patched));

        List<String> userFindings = adjustments.findings().stream()
                .filter(finding -> finding.mixin().equals("m/UserMixin"))
                .map(HostPatchAdjustments.Finding::reason)
                .toList();
        assertEquals(2, userFindings.size(), adjustments.findings().toString());
        assertTrue(
                userFindings.stream()
                        .anyMatch(reason -> reason.contains("ready")
                                && reason.contains("m/Extensions")
                                && reason.contains("m/ExtensionsMixin")
                                && reason.endsWith("this injection does not run")),
                userFindings.toString());
        assertTrue(
                userFindings.stream()
                        .anyMatch(reason -> reason.contains("useAccessor") && reason.contains("m/GoneAccessor")),
                userFindings.toString());

        ClassModel applied = ClassFile.of().parse(adjustments.apply("m.UserMixin", userMixin()));
        MethodTypeDesc handler = MethodTypeDesc.of(CD_void, CALLBACK_INFO);
        assertFalse(annotationsOf(method(applied, "ready", handler)).contains("Inject"));
        assertFalse(annotationsOf(method(applied, "useAccessor", handler)).contains("Inject"));
        assertTrue(annotationsOf(method(applied, "other", handler)).contains("Inject"));
        assertTrue(annotationsOf(method(applied, "guarded", handler)).contains("Inject"));
    }

    private static final ClassDesc SNIPPET = ClassDesc.of("t.Snippet");

    /**
     * ADR-0026 39: Fabric API's {@code RenderPipelineBuilderMixin} wraps {@code new RenderPipeline.Snippet(...)} in
     * {@code buildSnippet} ({@code @WrapOperation} at {@code NEW}, no constructor descriptor); NeoForge's patch gives the
     * constructor one more parameter. Mixin then rejects the handler's signature, and with it every other mixin on the
     * class (Iris's {@code PipelineBuilderMixin}, whose interface Iris's {@code RenderPipeline} mixin casts to). The
     * wrap is taken out instead.
     */
    @Test
    void aWrapOfAConstructorCallThePatchGaveOtherParametersDoesNotRun() throws Exception {
        byte[] vanilla = snippetTarget(MethodTypeDesc.of(CD_void, CD_String));
        byte[] patched = snippetTarget(MethodTypeDesc.of(CD_void, CD_String, CD_int));
        String configText = "{\"package\":\"m\",\"client\":[\"WrapNewMixin\"]}";
        Path jar = jar(Map.of(CONFIG, configText, "m/WrapNewMixin.class", wrapNewMixin()));

        HostPatchAdjustments adjustments = HostPatchAdjustments.plan(
                new HostedLaunch("Host 1", List.of(), List.of(), directory),
                GameSide.CLIENT,
                Map.of(CONFIG, "example"),
                Map.of("example", jar),
                name -> name.equals("t/Target") ? Optional.of(vanilla) : Optional.empty(),
                classes(patched));

        assertEquals(1, adjustments.findings().size(), adjustments.findings().toString());
        String reason = adjustments.findings().getFirst().reason();
        assertTrue(reason.contains("(Ljava/lang/String;I)V") && reason.endsWith("this injection does not run"), reason);
        ClassModel applied = ClassFile.of().parse(adjustments.apply("m.WrapNewMixin", wrapNewMixin()));
        assertFalse(annotationsOf(method(applied, "wrapSnippet", MethodTypeDesc.of(SNIPPET, CD_String, OPERATION)))
                .contains("WrapOperation"));
    }

    private static byte[] snippetTarget(MethodTypeDesc constructor) {
        return ClassFile.of()
                .build(
                        TARGET,
                        builder -> builder.withMethodBody(
                                "buildSnippet", MethodTypeDesc.of(SNIPPET), ClassFile.ACC_PUBLIC, code -> {
                                    code.new_(SNIPPET);
                                    code.dup();
                                    code.ldc("name");
                                    if (constructor.parameterCount() == 2) {
                                        code.iconst_1();
                                    }
                                    code.invokespecial(SNIPPET, "<init>", constructor);
                                    code.areturn();
                                }));
    }

    private static byte[] wrapNewMixin() {
        Annotation wrap = Annotation.of(
                ClassDesc.ofDescriptor("Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;"),
                AnnotationElement.of("method", AnnotationValue.ofArray(AnnotationValue.ofString("buildSnippet"))),
                AnnotationElement.of(
                        "at",
                        AnnotationValue.ofArray(AnnotationValue.ofAnnotation(Annotation.of(
                                ClassDesc.ofDescriptor("Lorg/spongepowered/asm/mixin/injection/At;"),
                                AnnotationElement.ofString("value", "NEW"),
                                AnnotationElement.ofString("target", "Lt/Snippet;"))))));
        return ClassFile.of().build(ClassDesc.of("m.WrapNewMixin"), builder -> {
            builder.with(RuntimeInvisibleAnnotationsAttribute.of(mixinAnnotation()));
            builder.withMethod(
                    "wrapSnippet", MethodTypeDesc.of(SNIPPET, CD_String, OPERATION), ClassFile.ACC_PRIVATE, method -> {
                        method.with(RuntimeVisibleAnnotationsAttribute.of(wrap));
                        method.withCode(code -> {
                            code.aconst_null();
                            code.areturn();
                        });
                    });
        });
    }

    private static byte[] extensionsInterface() {
        return ClassFile.of().build(EXTENSIONS, builder -> {
            builder.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_INTERFACE | ClassFile.ACC_ABSTRACT);
            builder.withMethod(
                    "onReady", MethodTypeDesc.of(CD_void), ClassFile.ACC_PUBLIC | ClassFile.ACC_ABSTRACT, method -> {});
        });
    }

    /** Adds {@code m.Extensions} to the target and shadows its field {@code gone}, which the host's patch removed. */
    private static byte[] extensionsMixin() {
        return ClassFile.of().build(ClassDesc.of("m.ExtensionsMixin"), builder -> {
            builder.withInterfaceSymbols(EXTENSIONS);
            builder.with(RuntimeInvisibleAnnotationsAttribute.of(mixinAnnotation()));
            builder.withField(
                    "gone",
                    CD_int,
                    field -> field.withFlags(ClassFile.ACC_PRIVATE)
                            .with(RuntimeVisibleAnnotationsAttribute.of(
                                    Annotation.of(ClassDesc.ofDescriptor("Lorg/spongepowered/asm/mixin/Shadow;")))));
            builder.withMethodBody("onReady", MethodTypeDesc.of(CD_void), ClassFile.ACC_PUBLIC, code -> code.return_());
        });
    }

    /** An accessor interface mixin for the field {@code gone}, which the host's patch removed. */
    private static byte[] goneAccessor() {
        return ClassFile.of().build(GONE_ACCESSOR, builder -> {
            builder.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_INTERFACE | ClassFile.ACC_ABSTRACT);
            builder.with(RuntimeInvisibleAnnotationsAttribute.of(mixinAnnotation()));
            builder.withMethod(
                    "getGone",
                    MethodTypeDesc.of(CD_int),
                    ClassFile.ACC_PUBLIC | ClassFile.ACC_ABSTRACT,
                    method -> method.with(RuntimeVisibleAnnotationsAttribute.of(Annotation.of(
                            ClassDesc.ofDescriptor("Lorg/spongepowered/asm/mixin/gen/Accessor;"),
                            AnnotationElement.ofString("value", "gone")))));
        });
    }

    /**
     * Three {@code @Inject(method = "run", at = @At("HEAD"))} handlers: {@code ready} casts to {@code m.Extensions}
     * through a helper method, {@code useAccessor} calls the accessor, {@code other} uses neither.
     */
    private static byte[] userMixin() {
        Annotation inject = Annotation.of(
                ClassDesc.ofDescriptor("Lorg/spongepowered/asm/mixin/injection/Inject;"),
                AnnotationElement.of("method", AnnotationValue.ofArray(AnnotationValue.ofString("run"))),
                AnnotationElement.of(
                        "at",
                        AnnotationValue.ofArray(AnnotationValue.ofAnnotation(Annotation.of(
                                ClassDesc.ofDescriptor("Lorg/spongepowered/asm/mixin/injection/At;"),
                                AnnotationElement.ofString("value", "HEAD"))))));
        MethodTypeDesc handler = MethodTypeDesc.of(CD_void, CALLBACK_INFO);
        MethodTypeDesc helper = MethodTypeDesc.of(CD_void, ClassDesc.of("java.lang.Object"));
        return ClassFile.of().build(USER_MIXIN, builder -> {
            builder.with(RuntimeInvisibleAnnotationsAttribute.of(mixinAnnotation()));
            builder.withMethod("ready", handler, ClassFile.ACC_PRIVATE, method -> {
                method.with(RuntimeVisibleAnnotationsAttribute.of(inject));
                method.withCode(code -> {
                    code.aload(0);
                    code.aload(0);
                    code.invokevirtual(USER_MIXIN, "notify", helper);
                    code.return_();
                });
            });
            builder.withMethodBody("notify", helper, ClassFile.ACC_PRIVATE, code -> {
                code.aload(1);
                code.checkcast(EXTENSIONS);
                code.invokeinterface(EXTENSIONS, "onReady", MethodTypeDesc.of(CD_void));
                code.return_();
            });
            builder.withMethod("useAccessor", handler, ClassFile.ACC_PRIVATE, method -> {
                method.with(RuntimeVisibleAnnotationsAttribute.of(inject));
                method.withCode(code -> {
                    code.aload(0);
                    code.checkcast(GONE_ACCESSOR);
                    code.invokeinterface(GONE_ACCESSOR, "getGone", MethodTypeDesc.of(CD_int));
                    code.pop();
                    code.return_();
                });
            });
            // Checked with instanceof first: does not throw where the object lacks the interface.
            builder.withMethod("guarded", handler, ClassFile.ACC_PRIVATE, method -> {
                method.with(RuntimeVisibleAnnotationsAttribute.of(inject));
                method.withCode(code -> {
                    code.aload(0);
                    code.instanceOf(EXTENSIONS);
                    code.ifThen(then -> {
                        then.aload(0);
                        then.checkcast(EXTENSIONS);
                        then.invokeinterface(EXTENSIONS, "onReady", MethodTypeDesc.of(CD_void));
                    });
                    code.return_();
                });
            });
            builder.withMethod("other", handler, ClassFile.ACC_PRIVATE, method -> {
                method.with(RuntimeVisibleAnnotationsAttribute.of(inject));
                method.withCode(code -> code.return_());
            });
        });
    }

    private static final ClassDesc COMMON = ClassDesc.of("t.Common");
    private static final ClassDesc GAME = ClassDesc.of("t.Game");
    private static final MethodTypeDesc HANDLE = MethodTypeDesc.of(CD_void, CD_String);
    private static final MethodTypeDesc ON_HANDLE = MethodTypeDesc.of(CD_void, CD_String, CALLBACK_INFO);

    /**
     * ADR-0026 41: Fabric API's {@code ServerCommonPacketListenerImplMixin} injects at the head of {@code
     * ServerCommonPacketListenerImpl.handleCustomPayload} and expects the configuration listener there: vanilla's {@code
     * ServerGamePacketListenerImpl} overrides the method with an empty one. NeoForge's override calls {@code super}, so a
     * play-phase payload Fabric API did not take reached the handler with the play listener ({@code Unknown addon}). An
     * {@code @Inject} into a method a subclass's override reaches only in the patched game keeps to vanilla's receivers.
     */
    @Test
    void anInjectionKeepsToTheReceiversItHasInVanillaWhenAPatchedOverrideCallsSuper() throws Exception {
        Map<String, byte[]> vanilla = Map.of("t/Common", common(), "t/Game", game(false));
        byte[] patchedGame = game(true);
        String configText = "{\"package\":\"m\",\"mixins\":[\"CommonMixin\",\"GameMixin\"]}";
        Path jar = jar(Map.of(
                CONFIG,
                configText,
                "m/CommonMixin.class",
                handleMixin("m.CommonMixin", COMMON),
                "m/GameMixin.class",
                handleMixin("m.GameMixin", GAME)));

        HostPatchAdjustments adjustments = HostPatchAdjustments.plan(
                new HostedLaunch("Host 1", List.of(), List.of(), directory),
                GameSide.CLIENT,
                Map.of(CONFIG, "example"),
                Map.of("example", jar),
                name -> Optional.ofNullable(vanilla.get(name)),
                classes(Map.of("t/Common", common(), "t/Game", patchedGame)));

        assertEquals(1, adjustments.findings().size(), adjustments.findings().toString());
        HostPatchAdjustments.Finding finding = adjustments.findings().getFirst();
        assertEquals("POLY-COMPAT-222", finding.code());
        assertFalse(finding.limited());
        assertEquals("m/CommonMixin", finding.mixin());
        assertTrue(finding.reason().contains("t/Game") && finding.reason().contains("onHandle"), finding.reason());

        ClassModel applied =
                ClassFile.of().parse(adjustments.apply("m.CommonMixin", handleMixin("m.CommonMixin", COMMON)));
        assertFalse(annotationsOf(method(applied, "onHandle", ON_HANDLE)).contains("Inject"));
        MethodModel guard = method(applied, "poly$receivers$onHandle", ON_HANDLE);
        assertTrue(annotationsOf(guard).contains("Inject"));
        assertTrue(
                guard.code()
                        .orElseThrow()
                        .elementStream()
                        .anyMatch(
                                element -> element instanceof java.lang.classfile.instruction.TypeCheckInstruction check
                                        && check.type().asInternalName().equals("t/Game")),
                "the guard checks for t/Game");
        assertEquals(
                List.of("m/CommonMixin.onHandle"),
                guard.code()
                        .orElseThrow()
                        .elementStream()
                        .filter(element -> element instanceof InvokeInstruction)
                        .map(element -> ((InvokeInstruction) element).owner().asInternalName() + "."
                                + ((InvokeInstruction) element).name().stringValue())
                        .toList());

        // The other mixin's injection, and both where vanilla's override already calls super, stay as they are.
        assertTrue(annotationsOf(method(
                        ClassFile.of().parse(adjustments.apply("m.GameMixin", handleMixin("m.GameMixin", GAME))),
                        "onHandle",
                        ON_HANDLE))
                .contains("Inject"));
        HostPatchAdjustments unchanged = HostPatchAdjustments.plan(
                new HostedLaunch("Host 1", List.of(), List.of(), directory),
                GameSide.CLIENT,
                Map.of(CONFIG, "example"),
                Map.of("example", jar),
                name -> Optional.ofNullable(
                        Map.of("t/Common", common(), "t/Game", patchedGame).get(name)),
                classes(Map.of("t/Common", common(), "t/Game", patchedGame)));
        assertEquals(List.of(), unchanged.findings());
    }

    private static byte[] common() {
        return ClassFile.of()
                .build(
                        COMMON,
                        builder ->
                                builder.withMethodBody("handle", HANDLE, ClassFile.ACC_PUBLIC, code -> code.return_()));
    }

    /** {@code t.Game extends t.Common}: its {@code handle} is empty, or calls {@code super.handle} ({@code callsSuper}). */
    private static byte[] game(boolean callsSuper) {
        return ClassFile.of().build(GAME, builder -> {
            builder.withSuperclass(COMMON);
            builder.withMethodBody("handle", HANDLE, ClassFile.ACC_PUBLIC, code -> {
                if (callsSuper) {
                    code.aload(0);
                    code.aload(1);
                    code.invokespecial(COMMON, "handle", HANDLE);
                }
                code.return_();
            });
        });
    }

    /** A mixin of {@code target} with {@code @Inject(method = "handle", at = @At("HEAD")) onHandle(String, CallbackInfo)}. */
    private static byte[] handleMixin(String name, ClassDesc target) {
        Annotation inject = Annotation.of(
                ClassDesc.ofDescriptor("Lorg/spongepowered/asm/mixin/injection/Inject;"),
                AnnotationElement.of("method", AnnotationValue.ofArray(AnnotationValue.ofString("handle"))),
                AnnotationElement.of(
                        "at",
                        AnnotationValue.ofArray(AnnotationValue.ofAnnotation(Annotation.of(
                                ClassDesc.ofDescriptor("Lorg/spongepowered/asm/mixin/injection/At;"),
                                AnnotationElement.ofString("value", "HEAD"))))));
        return ClassFile.of().build(ClassDesc.of(name), builder -> {
            builder.with(RuntimeInvisibleAnnotationsAttribute.of(Annotation.of(
                    ClassDesc.ofDescriptor("Lorg/spongepowered/asm/mixin/Mixin;"),
                    AnnotationElement.of("value", AnnotationValue.ofArray(AnnotationValue.ofClass(target))))));
            builder.withMethod("onHandle", ON_HANDLE, ClassFile.ACC_PRIVATE, method -> {
                method.with(RuntimeVisibleAnnotationsAttribute.of(inject));
                method.withCode(code -> code.return_());
            });
        });
    }

    private static String annotationsOf(MethodModel method) {
        return method.findAttribute(java.lang.classfile.Attributes.runtimeVisibleAnnotations())
                .map(attribute -> attribute.annotations().toString())
                .orElse("[]");
    }

    private static byte[] target(boolean vanilla) {
        return ClassFile.of().build(TARGET, builder -> {
            if (vanilla) {
                builder.withField("gone", CD_int, 0);
            }
            builder.withMethodBody("run", MethodTypeDesc.of(CD_void), 0, code -> {
                if (vanilla) {
                    code.invokestatic(TARGET, "old", MethodTypeDesc.of(CD_void));
                    code.invokestatic(TARGET, "moved", MethodTypeDesc.of(CD_void));
                } else {
                    code.invokestatic(TARGET, "replacement", MethodTypeDesc.of(CD_void));
                }
                code.return_();
            });
        });
    }

    private static byte[] injectMixin() {
        return mixin(
                "m.InjectMixin", List.of(inject("first", "Lt/Target;old()V"), inject("second", "Lt/Target;moved()V")));
    }

    private static byte[] accessorMixin() {
        return ClassFile.of().build(ClassDesc.of("m.AccessorMixin"), builder -> {
            builder.with(RuntimeInvisibleAnnotationsAttribute.of(mixinAnnotation()));
            builder.withMethod(
                    "getGone",
                    MethodTypeDesc.of(CD_int),
                    ClassFile.ACC_PUBLIC | ClassFile.ACC_ABSTRACT,
                    method -> method.with(RuntimeVisibleAnnotationsAttribute.of(Annotation.of(
                            ClassDesc.ofDescriptor("Lorg/spongepowered/asm/mixin/gen/Accessor;"),
                            AnnotationElement.ofString("value", "gone")))));
        });
    }

    private record Handler(String name, Annotation annotation) {}

    private static Handler inject(String name, String target) {
        return new Handler(
                name,
                Annotation.of(
                        ClassDesc.ofDescriptor("Lorg/spongepowered/asm/mixin/injection/Inject;"),
                        AnnotationElement.of("method", AnnotationValue.ofArray(AnnotationValue.ofString("run"))),
                        AnnotationElement.of(
                                "at",
                                AnnotationValue.ofArray(AnnotationValue.ofAnnotation(Annotation.of(
                                        ClassDesc.ofDescriptor("Lorg/spongepowered/asm/mixin/injection/At;"),
                                        AnnotationElement.ofString("value", "INVOKE"),
                                        AnnotationElement.ofString("target", target)))))));
    }

    private static byte[] mixin(String name, List<Handler> handlers) {
        return ClassFile.of().build(ClassDesc.of(name), builder -> {
            builder.with(RuntimeInvisibleAnnotationsAttribute.of(mixinAnnotation()));
            for (Handler handler : handlers) {
                builder.withMethod(handler.name(), MethodTypeDesc.of(CD_void), ClassFile.ACC_PRIVATE, method -> {
                    method.with(RuntimeVisibleAnnotationsAttribute.of(handler.annotation()));
                    method.withCode(code -> code.return_());
                });
            }
        });
    }

    private static Annotation mixinAnnotation() {
        return Annotation.of(
                ClassDesc.ofDescriptor("Lorg/spongepowered/asm/mixin/Mixin;"),
                AnnotationElement.of("value", AnnotationValue.ofArray(AnnotationValue.ofClass(TARGET))));
    }

    private static GameClassBytes classes(byte[] patched) {
        return new GameClassBytes() {
            @Override
            public Optional<byte[]> classBytes(String className) {
                return className.replace('.', '/').equals("t/Target") ? Optional.of(patched) : Optional.empty();
            }

            @Override
            public boolean isDefined(String className) {
                return false;
            }
        };
    }

    private Path jar(Map<String, Object> entries) throws IOException {
        Path jar = directory.resolve("mod.jar");
        try (OutputStream file = Files.newOutputStream(jar);
                JarOutputStream out = new JarOutputStream(file)) {
            for (Map.Entry<String, Object> entry : entries.entrySet()) {
                out.putNextEntry(new JarEntry(entry.getKey()));
                out.write(
                        entry.getValue() instanceof byte[] bytes
                                ? bytes
                                : entry.getValue().toString().getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        return jar;
    }
}
