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
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Fabric mixins against a host's patched classes (ADR-0021 G8, ADR-0030): mixins moved to the patch, which work as
 * written, and mixins that cannot work as written, which make their mod incompatible ({@code POLY-COMPAT-224}).
 */
class HostPatchAdjustmentsTest {

    private static final ClassDesc TARGET = ClassDesc.of("t.Target");
    private static final String CONFIG = "example.mixins.json";
    private static final String CONFIG_TEXT = "{\"package\":\"m\",\"mixins\":[\"InjectMixin\",\"AccessorMixin\"]}";

    @TempDir
    Path directory;

    /** The one-class vanilla lookup most tests use: exactly one class exists, under {@code name}, with {@code bytes}. */
    private static Function<String, Optional<byte[]>> vanillaOf(String name, byte[] bytes) {
        return className -> className.equals(name) ? Optional.of(bytes) : Optional.empty();
    }

    /** plan() with the default hosted launch: "Host 1", no bridges, no injection point replacements. */
    private HostPatchAdjustments plan(
            GameSide side,
            Map<String, String> configs,
            Map<String, Path> jars,
            Function<String, Optional<byte[]>> vanilla,
            GameClassBytes patched) {
        return HostPatchAdjustments.plan(
                new HostedLaunch("Host 1", List.of(), List.of(), directory), side, configs, jars, vanilla, patched);
    }

    @Test
    void injectionsAreMovedAndMixinsThatCannotWorkAsWrittenAreIncompatible() throws Exception {
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
                vanillaOf("t/Target", vanilla),
                classes(patched));

        assertEquals(3, adjustments.findings().size(), adjustments.findings().toString());
        assertTrue(adjustments.hasIncompatible());
        assertTrue(adjustments.findings().stream()
                .anyMatch(finding -> finding.leftOut()
                        && finding.incompatible()
                        && finding.code().equals("POLY-COMPAT-224")
                        && finding.mixin().equals("m/AccessorMixin")));
        assertTrue(
                adjustments.findings().stream()
                        .anyMatch(finding -> finding.incompatible()
                                && finding.reason().contains("old()V")
                                && finding.reason().endsWith("this injection does not run")),
                adjustments.findings().toString());
        assertTrue(
                adjustments.findings().stream()
                        .anyMatch(finding -> !finding.incompatible()
                                && finding.code().equals("POLY-COMPAT-226")
                                && finding.reason().contains("replacement()V")),
                adjustments.findings().toString());

        ClassModel applied = ClassFile.of().parse(adjustments.apply("m.InjectMixin", injectMixin()));
        String annotations = applied.methods().stream()
                .flatMap(method ->
                        method.findAttribute(java.lang.classfile.Attributes.runtimeVisibleAnnotations()).stream())
                .flatMap(attribute -> attribute.annotations().stream())
                .map(Annotation::toString)
                .toList()
                .toString();
        // The injectors keep the mod's own settings: Poly Mod Loader no longer makes them optional.
        assertFalse(annotations.contains("require"), annotations);
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

        HostPatchAdjustments adjustments = plan(
                GameSide.CLIENT,
                Map.of(EXTRACT_CONFIG, "example"),
                Map.of("example", jar),
                vanillaOf("t/Target", vanilla),
                classes(patched));

        assertEquals(1, adjustments.findings().size(), adjustments.findings().toString());
        assertFalse(adjustments.findings().getFirst().leftOut());
        assertFalse(adjustments.hasIncompatible(), adjustments.findings().toString());
        assertEquals("POLY-COMPAT-226", adjustments.findings().getFirst().code());
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
                        && !annotationsOf(bridge).contains("require"),
                annotationsOf(bridge));
        String calls = bridge.code().orElseThrow().elementList().stream()
                .filter(element -> element instanceof InvokeInstruction)
                .map(Object::toString)
                .toList()
                .toString();
        assertTrue(calls.contains("onExtract") && calls.contains(handlerType.descriptorString()), calls);
    }

    /** A parameter the patch changed rather than added: the injection cannot run, so the mod is incompatible. */
    @Test
    void anInjectionWhoseParametersThePatchChangedMakesTheModIncompatible() throws Exception {
        byte[] vanilla = extractTarget(MethodTypeDesc.of(CD_void, CD_String, CD_float));
        byte[] patched = extractTarget(MethodTypeDesc.of(CD_void, CD_String, CD_int));
        MethodTypeDesc handlerType = MethodTypeDesc.of(CD_void, CD_String, CD_float, CALLBACK_INFO);
        Path jar = jar(Map.of(EXTRACT_CONFIG, EXTRACT_CONFIG_TEXT, "m/ExtractMixin.class", extractMixin(handlerType)));

        HostPatchAdjustments adjustments = plan(
                GameSide.CLIENT,
                Map.of(EXTRACT_CONFIG, "example"),
                Map.of("example", jar),
                vanillaOf("t/Target", vanilla),
                classes(patched));

        assertEquals(1, adjustments.findings().size(), adjustments.findings().toString());
        HostPatchAdjustments.Finding finding = adjustments.findings().getFirst();
        assertTrue(finding.incompatible() && finding.code().equals("POLY-COMPAT-224"), finding.toString());
        assertTrue(finding.reason().contains("does not run"), finding.toString());
        // Nothing is taken out of the mixin: the mod is not loaded at all.
        ClassModel applied = ClassFile.of().parse(adjustments.apply("m.ExtractMixin", extractMixin(handlerType)));
        assertTrue(annotationsOf(method(applied, "onExtract", handlerType)).contains("Inject"));
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
                        .allMatch(finding -> !finding.leftOut()
                                && finding.code().equals("POLY-COMPAT-212")
                                && !finding.incompatible()),
                adjustments.findings().toString());
        ClassModel applied = ClassFile.of().parse(adjustments.apply("m.MemberAccessor", accessorInterface()));
        assertEquals(
                List.of("getServer"),
                applied.methods().stream()
                        .map(m -> m.methodName().stringValue())
                        .toList());
    }

    /**
     * The same name and descriptor, but the host's method is not that accessor. With {@code @Overwrite} required by
     * the configuration, Mixin would fail the whole mixin: the mod is incompatible.
     */
    @Test
    void anAccessorThePatchAddedWithOtherCodeFailsTheMixinWhenOverwritesNeedAnnotations() throws Exception {
        byte[] vanilla = memberTarget(false, CD_String);
        byte[] patched = memberTarget(true, CD_String, false);
        String configText =
                "{\"package\":\"m\",\"mixins\":[\"MemberAccessor\"],\"overwrites\":{\"requireAnnotations\":true}}";
        Path jar = jar(Map.of(MEMBER_CONFIG, configText, "m/MemberAccessor.class", accessorInterface()));

        HostPatchAdjustments adjustments = plan(jar, vanilla, patched);

        assertEquals(1, adjustments.findings().size(), adjustments.findings().toString());
        assertTrue(
                adjustments.findings().getFirst().leftOut()
                        && adjustments.findings().getFirst().incompatible(),
                adjustments.findings().toString());
    }

    /**
     * Without that requirement Mixin would replace the host's method with the accessor (architectury's {@code
     * LiquidBlockAccessor.getFluid()} over Forge's {@code LiquidBlock.getFluid()}), and the host's code calling it would
     * get the accessor's result: the mod is incompatible, and Poly Mod Loader does not run it that way.
     */
    @Test
    void anAccessorThePatchAddedWithOtherCodeMakesTheModIncompatible() throws Exception {
        byte[] vanilla = memberTarget(false, CD_String);
        byte[] patched = memberTarget(true, CD_String, false);
        Path jar = jar(Map.of(MEMBER_CONFIG, MEMBER_CONFIG_TEXT, "m/MemberAccessor.class", accessorInterface()));

        HostPatchAdjustments adjustments = plan(jar, vanilla, patched);

        HostPatchAdjustments.Finding getConnection = adjustments.findings().stream()
                .filter(finding -> finding.reason().contains("getConnection"))
                .findFirst()
                .orElseThrow(() -> new AssertionError(adjustments.findings().toString()));
        assertEquals("POLY-COMPAT-224", getConnection.code());
        assertTrue(getConnection.incompatible() && !getConnection.leftOut(), getConnection.toString());
    }

    /**
     * The host's patch changed the type of the field an {@code @Accessor} reads (Forge turns {@code
     * AttributeSupplier$Builder.builder} from an {@code ImmutableMap.Builder} into a {@code Map}). No accessor of the
     * old type can work on that game: the mod is incompatible.
     */
    @Test
    void anAccessorWhoseFieldTypeThePatchChangedMakesTheModIncompatible() throws Exception {
        byte[] vanilla = memberTarget(false, CD_List);
        byte[] patched = memberTarget(false, CD_Map);
        Path jar = jar(Map.of(MEMBER_CONFIG, MEMBER_CONFIG_TEXT, "m/MemberAccessor.class", fieldAccessorInterface()));

        HostPatchAdjustments adjustments = plan(jar, vanilla, patched);

        assertEquals(1, adjustments.findings().size(), adjustments.findings().toString());
        HostPatchAdjustments.Finding finding = adjustments.findings().getFirst();
        assertFalse(finding.leftOut());
        assertTrue(finding.incompatible() && finding.code().equals("POLY-COMPAT-224"), finding.toString());
        assertTrue(finding.reason().contains("Ljava/util/Map;"), finding.reason());
    }

    /**
     * The host's patch changed the type of a field a mixin shadows (Forge's {@code RangedBowAttackGoal.mob} is a {@code
     * Mob} where vanilla's is a {@code Monster}; Dwarfs and Giants shadows the {@code Monster}). Mixin finds a shadow
     * field by name and type, so it fails the whole mixin: the mod is incompatible, found before the launch.
     */
    @Test
    void aShadowFieldWhoseTypeThePatchChangedMakesTheModIncompatible() throws Exception {
        ClassDesc monster = ClassDesc.of("t.Monster");
        ClassDesc mob = ClassDesc.of("t.Mob");
        Path jar = jar(Map.of(
                CONFIG,
                "{\"package\":\"m\",\"mixins\":[\"ShadowMixin\"]}",
                "m/ShadowMixin.class",
                shadowFieldMixin("mob", monster, List.of())));

        HostPatchAdjustments adjustments = plan(
                GameSide.DEDICATED_SERVER,
                Map.of(CONFIG, "example"),
                Map.of("example", jar),
                vanillaOf("t/Target", fieldTarget("mob", monster)),
                classes(fieldTarget("mob", mob)));

        assertEquals(1, adjustments.findings().size(), adjustments.findings().toString());
        HostPatchAdjustments.Finding finding = adjustments.findings().getFirst();
        assertTrue(
                finding.leftOut() && finding.incompatible() && finding.code().equals("POLY-COMPAT-224"),
                finding.toString());
        assertTrue(
                finding.reason().contains("@Shadow field mob:Lt/Monster;")
                        && finding.reason().contains("Lt/Mob;"),
                finding.reason());
        // The same type in both, or an alias Mixin finds in the patched class: nothing to report.
        assertTrue(plan(
                        GameSide.DEDICATED_SERVER,
                        Map.of(CONFIG, "example"),
                        Map.of("example", jar),
                        vanillaOf("t/Target", fieldTarget("mob", monster)),
                        classes(fieldTarget("mob", monster)))
                .findings()
                .isEmpty());
        Path aliased = jar(Map.of(
                CONFIG,
                "{\"package\":\"m\",\"mixins\":[\"ShadowMixin\"]}",
                "m/ShadowMixin.class",
                shadowFieldMixin("mob", monster, List.of("monster"))));
        assertTrue(plan(
                        GameSide.DEDICATED_SERVER,
                        Map.of(CONFIG, "example"),
                        Map.of("example", aliased),
                        vanillaOf("t/Target", fieldTarget("mob", monster)),
                        classes(fieldTarget("monster", monster)))
                .findings()
                .isEmpty());
    }

    private static byte[] fieldTarget(String name, ClassDesc type) {
        return ClassFile.of()
                .build(TARGET, builder -> builder.withField(name, type, ClassFile.ACC_PRIVATE | ClassFile.ACC_FINAL));
    }

    private static byte[] shadowFieldMixin(String name, ClassDesc type, List<String> aliases) {
        Annotation shadow = aliases.isEmpty()
                ? Annotation.of(ClassDesc.ofDescriptor("Lorg/spongepowered/asm/mixin/Shadow;"))
                : Annotation.of(
                        ClassDesc.ofDescriptor("Lorg/spongepowered/asm/mixin/Shadow;"),
                        AnnotationElement.of(
                                "aliases",
                                AnnotationValue.ofArray(aliases.stream()
                                        .map(alias -> (AnnotationValue) AnnotationValue.ofString(alias))
                                        .toList())));
        return ClassFile.of().build(ClassDesc.of("m.ShadowMixin"), builder -> {
            builder.with(RuntimeInvisibleAnnotationsAttribute.of(mixinAnnotation()));
            builder.withField(
                    name,
                    type,
                    field ->
                            field.withFlags(ClassFile.ACC_PRIVATE).with(RuntimeVisibleAnnotationsAttribute.of(shadow)));
        });
    }

    /**
     * The host's patch makes a static call an instance call (Forge turns {@code AbstractFurnaceBlockEntity.consumeFuel}
     * from {@code private static} into {@code protected}, so {@code serverTick} calls it with {@code invokevirtual};
     * All Stacks Infinite redirects it). A handler that takes the call's receiver ({@code @Redirect}, {@code
     * @WrapOperation}, {@code @WrapWithCondition}) is written without one, and Mixin rejects its signature: the mod is
     * incompatible. Injectors that only need the place ({@code @Inject}) or the arguments ({@code @ModifyArg}) still
     * work. The reverse (an instance call made static) is the same.
     */
    @Test
    void aCallBoundInjectionAtACallThePatchMadeAnInstanceCallMakesTheModIncompatible() throws Exception {
        String call = "Lt/Target;fuel(I)V";
        Annotation wrap = Annotation.of(
                ClassDesc.ofDescriptor("Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;"),
                AnnotationElement.of("method", AnnotationValue.ofArray(AnnotationValue.ofString("run"))),
                AnnotationElement.of(
                        "at",
                        AnnotationValue.ofArray(AnnotationValue.ofAnnotation(Annotation.of(
                                ClassDesc.ofDescriptor("Lorg/spongepowered/asm/mixin/injection/At;"),
                                AnnotationElement.ofString("value", "INVOKE"),
                                AnnotationElement.ofString("target", call))))));
        Annotation modifyArg = Annotation.of(
                ClassDesc.ofDescriptor("Lorg/spongepowered/asm/mixin/injection/ModifyArg;"),
                AnnotationElement.of("method", AnnotationValue.ofArray(AnnotationValue.ofString("run"))),
                AnnotationElement.of(
                        "at",
                        AnnotationValue.ofAnnotation(Annotation.of(
                                ClassDesc.ofDescriptor("Lorg/spongepowered/asm/mixin/injection/At;"),
                                AnnotationElement.ofString("value", "INVOKE"),
                                AnnotationElement.ofString("target", call)))));
        byte[] mixin = mixin(
                "m.CallMixin",
                List.of(
                        new Handler("redirectFuel", redirect(call)),
                        new Handler("wrapFuel", wrap),
                        inject("beforeFuel", call),
                        new Handler("modifyFuel", modifyArg)));
        Path jar = jar(Map.of(CONFIG, "{\"package\":\"m\",\"mixins\":[\"CallMixin\"]}", "m/CallMixin.class", mixin));

        HostPatchAdjustments adjustments = plan(
                GameSide.DEDICATED_SERVER,
                Map.of(CONFIG, "example"),
                Map.of("example", jar),
                vanillaOf("t/Target", callKindTarget(false)),
                classes(callKindTarget(true)));

        assertEquals(2, adjustments.findings().size(), adjustments.findings().toString());
        assertTrue(adjustments.hasIncompatible());
        for (String handler : List.of("@Redirect redirectFuel", "@WrapOperation wrapFuel")) {
            assertTrue(
                    adjustments.findings().stream()
                            .anyMatch(finding -> finding.incompatible()
                                    && finding.code().equals("POLY-COMPAT-224")
                                    && finding.reason().startsWith(handler + " into t/Target.run")
                                    && finding.reason().contains("invokevirtual")
                                    && finding.reason().endsWith("this injection does not run")),
                    adjustments.findings().toString());
        }

        // An instance call the patch made static.
        HostPatchAdjustments reverse = plan(
                GameSide.DEDICATED_SERVER,
                Map.of(CONFIG, "example"),
                Map.of("example", jar),
                vanillaOf("t/Target", callKindTarget(true)),
                classes(callKindTarget(false)));
        assertEquals(2, reverse.findings().size(), reverse.findings().toString());
        assertTrue(
                reverse.findings().stream()
                        .allMatch(finding ->
                                finding.incompatible() && finding.reason().contains("invokestatic")),
                reverse.findings().toString());

        // The same call in both: nothing to report.
        assertTrue(plan(
                        GameSide.DEDICATED_SERVER,
                        Map.of(CONFIG, "example"),
                        Map.of("example", jar),
                        vanillaOf("t/Target", callKindTarget(false)),
                        classes(callKindTarget(false)))
                .findings()
                .isEmpty());
    }

    /** {@code run()} calls {@code fuel(int)}: on {@code this} when {@code instanceCall}, else statically. */
    private static byte[] callKindTarget(boolean instanceCall) {
        MethodTypeDesc fuel = MethodTypeDesc.of(CD_void, CD_int);
        return ClassFile.of().build(TARGET, builder -> {
            builder.withMethodBody(
                    "fuel",
                    fuel,
                    instanceCall ? ClassFile.ACC_PROTECTED : ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC,
                    code -> code.return_());
            builder.withMethodBody("run", MethodTypeDesc.of(CD_void), 0, code -> {
                if (instanceCall) {
                    code.aload(0);
                    code.iconst_1();
                    code.invokevirtual(TARGET, "fuel", fuel);
                } else {
                    code.iconst_1();
                    code.invokestatic(TARGET, "fuel", fuel);
                }
                code.return_();
            });
        });
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
        assertFalse(adjustments.findings().getFirst().incompatible());
        ClassModel applied = ClassFile.of().parse(adjustments.apply("m.MemberAccessor", mixin));
        assertTrue(
                applied.methods().stream().noneMatch(m -> m.methodName().equalsString("describe")),
                applied.methods().toString());
        assertTrue(applied.methods().stream().anyMatch(m -> m.methodName().equalsString("helper")));
    }

    @Test
    void anUnmodifiableCollectionGetterThePatchAddedIsLeftToThePatchedMethod() throws Exception {
        byte[] vanilla = memberTarget(false, CD_List);
        byte[] patched = memberTarget(true, CD_List);
        byte[] mixin = unmodifiableGetterMixin();
        Path jar = jar(Map.of(MEMBER_CONFIG, MEMBER_CONFIG_TEXT, "m/MemberAccessor.class", mixin));

        HostPatchAdjustments adjustments = plan(jar, vanilla, patched);

        assertEquals(1, adjustments.findings().size(), adjustments.findings().toString());
        assertEquals("POLY-COMPAT-212", adjustments.findings().getFirst().code());
        assertFalse(adjustments.findings().getFirst().incompatible());
        ClassModel applied = ClassFile.of().parse(adjustments.apply("m.MemberAccessor", mixin));
        assertTrue(
                applied.methods().stream().noneMatch(m -> m.methodName().equalsString("getConnection")),
                applied.methods().toString());
    }

    /**
     * A method a Fabric mixin adds that the host's patch added with other code (Fabric's tag API and Forge both add
     * {@code TagFile.remove()}, each for its own list): one class cannot hold both, so the mod is incompatible. Poly Mod
     * Loader does not rename the mixin's method, which would leave other callers reaching the host's.
     */
    @Test
    void aMethodThePatchAddedWithOtherCodeMakesTheModIncompatible() throws Exception {
        byte[] vanilla = memberTarget(false, CD_String);
        byte[] patched = memberTarget(true, CD_String);
        byte[] mixin = addingMixin(false);
        Path jar = jar(Map.of(MEMBER_CONFIG, MEMBER_CONFIG_TEXT, "m/MemberAccessor.class", mixin));

        HostPatchAdjustments adjustments = plan(jar, vanilla, patched);

        assertEquals(1, adjustments.findings().size(), adjustments.findings().toString());
        HostPatchAdjustments.Finding finding = adjustments.findings().getFirst();
        assertEquals("POLY-COMPAT-224", finding.code());
        assertTrue(finding.incompatible() && !finding.leftOut(), finding.toString());
        assertTrue(finding.reason().contains("describe"), finding.reason());
        org.junit.jupiter.api.Assertions.assertArrayEquals(mixin, adjustments.apply("m.MemberAccessor", mixin));
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
        assertFalse(finding.incompatible() || finding.leftOut(), finding.toString());
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

    /** No local of a usable type where the vanilla one was: the injection cannot run, so the mod is incompatible. */
    @Test
    void aLocalThePatchRemovedIsReported() throws Exception {
        byte[] vanilla = localTarget("values", CD_Collection);
        byte[] patched = localTarget("builder", CD_String);
        byte[] mixin = localMixin();
        Path jar = jar(Map.of(MEMBER_CONFIG, MEMBER_CONFIG_TEXT, "m/MemberAccessor.class", mixin));

        HostPatchAdjustments adjustments = plan(jar, vanilla, patched);

        assertEquals(1, adjustments.findings().size(), adjustments.findings().toString());
        HostPatchAdjustments.Finding finding = adjustments.findings().getFirst();
        assertEquals("POLY-COMPAT-224", finding.code());
        assertTrue(
                finding.incompatible() && !finding.leftOut() && finding.reason().contains("values"),
                finding.toString());
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

    /**
     * An {@code @Inject} that captures a local by type alone (MixinExtras' {@code @Local} without name, ordinal or
     * index: the one local of that type) moves to the call the host's patch put in place of its point, where the
     * patch also has another local of that type (Forge's {@code ServerPlayer.drop(boolean)}: {@code selected} beside
     * vanilla's {@code removed} at {@code ForgeHooks.onPlayerTossEvent}, where Puzzles Lib's toss event runs). Mixin
     * would find two candidates and fail; the {@code @Local} captures the vanilla local by its name.
     */
    @Test
    void anImplicitLocalTheMovedPointMakesAmbiguousIsCapturedByItsVanillaName() throws Exception {
        byte[] mixin = implicitLocalMixin();
        HostPatchAdjustments adjustments =
                implicitLocalPlan(mixin, implicitLocalTarget(false, "removed"), implicitLocalTarget(true, "removed"));

        assertFalse(adjustments.hasIncompatible(), adjustments.findings().toString());
        assertTrue(
                adjustments.findings().stream()
                        .anyMatch(finding -> finding.code().equals("POLY-COMPAT-212")
                                && finding.reason().contains("removed")),
                adjustments.findings().toString());
        ClassModel applied = ClassFile.of().parse(adjustments.apply("m.MemberAccessor", mixin));
        MethodModel handler = applied.methods().stream()
                .filter(method -> method.methodName().equalsString("onRun"))
                .findFirst()
                .orElseThrow();
        assertEquals(
                MethodTypeDesc.of(CD_void, CALLBACK_INFO, CD_Collection).descriptorString(),
                handler.methodType().stringValue());
        String parameters = handler.findAttribute(java.lang.classfile.Attributes.runtimeInvisibleParameterAnnotations())
                .orElseThrow()
                .parameterAnnotations()
                .toString();
        assertTrue(parameters.contains("name") && parameters.contains("constant=removed"), parameters);
        String annotations = handler.findAttribute(java.lang.classfile.Attributes.runtimeVisibleAnnotations())
                .orElseThrow()
                .annotations()
                .toString();
        assertTrue(annotations.contains("replacement()V"), annotations);
    }

    /** The same, when no local of that type at the moved point has the vanilla one's name: the mod is incompatible. */
    @Test
    void anImplicitLocalTheMovedPointMakesAmbiguousWithoutTheVanillaNameIsReported() throws Exception {
        byte[] mixin = implicitLocalMixin();
        HostPatchAdjustments adjustments =
                implicitLocalPlan(mixin, implicitLocalTarget(false, "removed"), implicitLocalTarget(true, "dropped"));

        assertTrue(
                adjustments.findings().stream()
                        .anyMatch(finding -> finding.incompatible()
                                && finding.code().equals("POLY-COMPAT-224")
                                && finding.reason().contains("@Local")),
                adjustments.findings().toString());
    }

    /**
     * A local captured by name in a method whose body, with the injection point, the host's patch moved into another
     * method ({@link InjectionPointReplacement.Placement#IN_METHOD}, Forge's {@code TagsProvider.lambda$run$5} to
     * {@code lambda$run$6}, where Fabric's data generation API captures {@code builder}): the local is looked up where
     * the injection moves, and is there.
     */
    @Test
    void aNamedLocalIsLookedUpInTheMethodTheInjectionMovesTo() throws Exception {
        byte[] vanilla = localTarget("values", CD_Collection, true);
        byte[] patched = ClassFile.of().build(TARGET, builder -> {
            builder.withMethodBody("run", MethodTypeDesc.of(CD_void), ClassFile.ACC_PUBLIC, code -> code.return_());
            builder.withMethodBody("moved", MethodTypeDesc.of(CD_void), ClassFile.ACC_PUBLIC, code -> {
                var start = code.newLabel();
                var end = code.newLabel();
                code.aconst_null().astore(1).labelBinding(start);
                code.invokestatic(TARGET, "old", MethodTypeDesc.of(CD_void));
                code.labelBinding(end).return_();
                code.localVariable(1, "values", CD_Collection, start, end);
            });
        });
        byte[] mixin = localMixin("Lt/Target;old()V");
        Path jar = jar(Map.of(MEMBER_CONFIG, MEMBER_CONFIG_TEXT, "m/MemberAccessor.class", mixin));
        HostedLaunch host = new HostedLaunch(
                "Host 1",
                List.of(),
                List.of(new InjectionPointReplacement(
                        "t/Target",
                        "run",
                        "Lt/Target;old()V",
                        "moved()V",
                        InjectionPointReplacement.Placement.IN_METHOD)),
                directory);

        HostPatchAdjustments adjustments = HostPatchAdjustments.plan(
                host,
                GameSide.DEDICATED_SERVER,
                Map.of(MEMBER_CONFIG, "example"),
                Map.of("example", jar),
                vanillaOf("t/Target", vanilla),
                classes(patched));

        assertFalse(adjustments.hasIncompatible(), adjustments.findings().toString());
        assertEquals(1, adjustments.findings().size(), adjustments.findings().toString());
        assertTrue(
                adjustments.findings().getFirst().reason().contains("moved()V"),
                adjustments.findings().toString());
    }

    private HostPatchAdjustments implicitLocalPlan(byte[] mixin, byte[] vanilla, byte[] patched) throws IOException {
        Path jar = jar(Map.of(MEMBER_CONFIG, MEMBER_CONFIG_TEXT, "m/MemberAccessor.class", mixin));
        HostedLaunch host = new HostedLaunch(
                "Host 1",
                List.of(),
                List.of(new InjectionPointReplacement(
                        "t/Target", "run", "Lt/Target;old()V", "Lt/Target;replacement()V")),
                directory);
        return HostPatchAdjustments.plan(
                host,
                GameSide.DEDICATED_SERVER,
                Map.of(MEMBER_CONFIG, "example"),
                Map.of("example", jar),
                vanillaOf("t/Target", vanilla),
                classes(patched));
    }

    /**
     * {@code void run()} with a {@code Collection} local {@code name} at slot 2, in scope at a call of {@code old()}
     * (vanilla) or, with {@code patched}, of {@code replacement()}, where another {@code Collection} local, {@code
     * selected} at slot 1, is in scope too.
     */
    private static byte[] implicitLocalTarget(boolean patched, String name) {
        return ClassFile.of()
                .build(
                        TARGET,
                        builder -> builder.withMethodBody(
                                "run", MethodTypeDesc.of(CD_void), ClassFile.ACC_PUBLIC, code -> {
                                    var selected = code.newLabel();
                                    var start = code.newLabel();
                                    var end = code.newLabel();
                                    code.aconst_null().astore(1).labelBinding(selected);
                                    code.aconst_null().astore(2).labelBinding(start);
                                    code.invokestatic(
                                            TARGET, patched ? "replacement" : "old", MethodTypeDesc.of(CD_void));
                                    code.labelBinding(end).return_();
                                    if (patched) {
                                        code.localVariable(1, "selected", CD_Collection, selected, end);
                                    }
                                    code.localVariable(2, name, CD_Collection, start, end);
                                }));
    }

    /** {@code @Inject(method = "run", at = @At(value = "INVOKE", target = "old()")) onRun(CallbackInfo, @Local Collection)}. */
    private static byte[] implicitLocalMixin() {
        Annotation at = Annotation.of(
                ClassDesc.ofDescriptor("Lorg/spongepowered/asm/mixin/injection/At;"),
                AnnotationElement.ofString("value", "INVOKE"),
                AnnotationElement.ofString("target", "Lt/Target;old()V"));
        Annotation inject = Annotation.of(
                ClassDesc.ofDescriptor("Lorg/spongepowered/asm/mixin/injection/Inject;"),
                AnnotationElement.of("method", AnnotationValue.ofArray(AnnotationValue.ofString("run"))),
                AnnotationElement.of("at", AnnotationValue.ofArray(AnnotationValue.ofAnnotation(at))));
        Annotation local = Annotation.of(ClassDesc.ofDescriptor("Lcom/llamalad7/mixinextras/sugar/Local;"));
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
                        method.withCode(code -> code.return_());
                    });
        });
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

    @Test
    void anAccessorOfAttributeSupplierBuilderIsAdaptedViaBridge() throws Exception {
        ClassDesc target = ClassDesc.of("net.minecraft.world.entity.ai.attributes.AttributeSupplier$Builder");
        ClassDesc immutableMapBuilder = ClassDesc.of("com.google.common.collect.ImmutableMap$Builder");
        byte[] vanilla = ClassFile.of().build(target, builder -> {
            builder.withField("builder", immutableMapBuilder, ClassFile.ACC_PRIVATE);
        });
        byte[] patched = ClassFile.of().build(target, builder -> {
            builder.withField("builder", CD_Map, ClassFile.ACC_PRIVATE);
        });
        byte[] mixin = ClassFile.of()
                .build(
                        ClassDesc.of("net.fabricmc.fabric.mixin.object.builder.AttributeSupplierBuilderAccessor"),
                        builder -> {
                            builder.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_INTERFACE | ClassFile.ACC_ABSTRACT);
                            builder.with(RuntimeInvisibleAnnotationsAttribute.of(mixinAnnotation(target)));
                            builder.withMethod(
                                    "getBuilder",
                                    MethodTypeDesc.of(immutableMapBuilder),
                                    ClassFile.ACC_PUBLIC | ClassFile.ACC_ABSTRACT,
                                    method ->
                                            method.with(RuntimeVisibleAnnotationsAttribute.of(generator("Accessor"))));
                        });
        String configText = """
                {"required": true, "package": "net.fabricmc.fabric.mixin.object.builder", "compatibilityLevel": "JAVA_25", "mixins": ["AttributeSupplierBuilderAccessor"]}
                """;
        Path jar = jar(Map.of(
                MEMBER_CONFIG,
                configText,
                "net/fabricmc/fabric/mixin/object/builder/AttributeSupplierBuilderAccessor.class",
                mixin));

        HostPatchAdjustments adjustments = plan(target, jar, vanilla, patched);

        assertEquals(1, adjustments.findings().size(), adjustments.findings().toString());
        HostPatchAdjustments.Finding finding = adjustments.findings().getFirst();
        assertEquals("POLY-COMPAT-212", finding.code());
        assertFalse(finding.incompatible());
        assertTrue(finding.reason().contains("AttributeBuilderBridge"), finding.reason());
    }

    @Test
    void bedSleepEventsInLivingEntityMixinAreAdaptedViaWrapOperation() throws Exception {
        ClassDesc livingEntity = ClassDesc.of("net.minecraft.world.entity.LivingEntity");
        ClassDesc bedBlock = ClassDesc.of("net.minecraft.world.level.block.BedBlock");
        ClassDesc blockState = ClassDesc.of("net.minecraft.world.level.block.state.BlockState");
        ClassDesc direction = ClassDesc.of("net.minecraft.core.Direction");
        ClassDesc blockGetter = ClassDesc.of("net.minecraft.world.level.BlockGetter");
        ClassDesc levelReader = ClassDesc.of("net.minecraft.world.level.LevelReader");
        ClassDesc blockPos = ClassDesc.of("net.minecraft.core.BlockPos");
        ClassDesc level = ClassDesc.of("net.minecraft.world.level.Level");

        byte[] vanilla = ClassFile.of().build(livingEntity, builder -> {
            builder.withMethod("getBedOrientation", MethodTypeDesc.of(direction), ClassFile.ACC_PUBLIC, method -> {
                method.withCode(code -> {
                    code.aconst_null();
                    code.aconst_null();
                    code.invokestatic(
                            bedBlock, "getBedOrientation", MethodTypeDesc.of(direction, blockGetter, blockPos));
                    code.areturn();
                });
            });
            builder.withMethod(
                    "lambda$stopSleeping$0", MethodTypeDesc.of(CD_void, blockPos), ClassFile.ACC_PRIVATE, method -> {
                        method.withCode(code -> {
                            code.aconst_null();
                            code.aconst_null();
                            code.aconst_null();
                            code.iconst_0();
                            code.invokevirtual(
                                    level, "setBlock", MethodTypeDesc.of(CD_boolean, blockPos, blockState, CD_int));
                            code.pop();
                            code.return_();
                        });
                    });
        });

        byte[] patched = ClassFile.of().build(livingEntity, builder -> {
            builder.withMethod("getBedOrientation", MethodTypeDesc.of(direction), ClassFile.ACC_PUBLIC, method -> {
                method.withCode(code -> {
                    code.aconst_null();
                    code.aconst_null();
                    code.aconst_null();
                    code.invokevirtual(
                            blockState, "getBedDirection", MethodTypeDesc.of(direction, levelReader, blockPos));
                    code.areturn();
                });
            });
            builder.withMethod(
                    "lambda$stopSleeping$0", MethodTypeDesc.of(CD_void, blockPos), ClassFile.ACC_PRIVATE, method -> {
                        method.withCode(code -> {
                            code.aconst_null();
                            code.aconst_null();
                            code.aconst_null();
                            code.aconst_null();
                            code.iconst_0();
                            code.invokevirtual(
                                    blockState,
                                    "setBedOccupied",
                                    MethodTypeDesc.of(CD_void, level, blockPos, livingEntity, CD_boolean));
                            code.return_();
                        });
                    });
        });

        Annotation wrapAnnotation = Annotation.of(
                ClassDesc.ofDescriptor("Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;"),
                AnnotationElement.of("method", AnnotationValue.ofArray(AnnotationValue.ofString("getBedOrientation"))),
                AnnotationElement.of(
                        "at",
                        AnnotationValue.ofArray(
                                AnnotationValue.ofAnnotation(
                                        Annotation.of(
                                                ClassDesc.ofDescriptor("Lorg/spongepowered/asm/mixin/injection/At;"),
                                                AnnotationElement.ofString("value", "INVOKE"),
                                                AnnotationElement.ofString(
                                                        "target",
                                                        "Lnet/minecraft/world/level/block/BedBlock;getBedOrientation(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/Direction;"))))));

        Annotation redirectAnnotation = Annotation.of(
                ClassDesc.ofDescriptor("Lorg/spongepowered/asm/mixin/injection/Redirect;"),
                AnnotationElement.of(
                        "method", AnnotationValue.ofArray(AnnotationValue.ofString("lambda$stopSleeping$0"))),
                AnnotationElement.of(
                        "at",
                        AnnotationValue.ofArray(
                                AnnotationValue.ofAnnotation(
                                        Annotation.of(
                                                ClassDesc.ofDescriptor("Lorg/spongepowered/asm/mixin/injection/At;"),
                                                AnnotationElement.ofString("value", "INVOKE"),
                                                AnnotationElement.ofString(
                                                        "target",
                                                        "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))))));

        ClassDesc mixinDesc = ClassDesc.of("net.fabricmc.fabric.mixin.entity.event.LivingEntityMixin");
        byte[] mixinBytes = ClassFile.of().build(mixinDesc, builder -> {
            builder.with(RuntimeInvisibleAnnotationsAttribute.of(mixinAnnotation(livingEntity)));
            builder.withMethod(
                    "onGetSleepingDirection",
                    MethodTypeDesc.of(direction, blockGetter, blockPos, OPERATION),
                    ClassFile.ACC_PRIVATE,
                    method -> {
                        method.with(RuntimeVisibleAnnotationsAttribute.of(wrapAnnotation));
                        method.withCode(code -> {
                            code.aconst_null();
                            code.areturn();
                        });
                    });
            builder.withMethod(
                    "setOccupiedState",
                    MethodTypeDesc.of(CD_boolean, level, blockPos, blockState, CD_int),
                    ClassFile.ACC_PRIVATE,
                    method -> {
                        method.with(RuntimeVisibleAnnotationsAttribute.of(redirectAnnotation));
                        method.withCode(code -> {
                            code.iconst_1();
                            code.ireturn();
                        });
                    });
        });

        String configText = """
                {"required": true, "package": "net.fabricmc.fabric.mixin.entity.event", "compatibilityLevel": "JAVA_25", "mixins": ["LivingEntityMixin"]}
                """;
        Path jar = jar(Map.of(
                MEMBER_CONFIG,
                configText,
                "net/fabricmc/fabric/mixin/entity/event/LivingEntityMixin.class",
                mixinBytes));

        HostedLaunch host = new HostedLaunch(
                "MinecraftForge 26.2-65.1.5",
                List.of(),
                List.of(
                        new InjectionPointReplacement(
                                "net/minecraft/world/entity/LivingEntity",
                                "getBedOrientation",
                                "Lnet/minecraft/world/level/block/BedBlock;getBedOrientation(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/Direction;",
                                "Lnet/minecraft/world/level/block/state/BlockState;getBedDirection(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/Direction;"),
                        new InjectionPointReplacement(
                                "net/minecraft/world/entity/LivingEntity",
                                "lambda$stopSleeping$0",
                                "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z",
                                "Lnet/minecraft/world/level/block/state/BlockState;setBedOccupied(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/LivingEntity;Z)V")),
                directory);

        HostPatchAdjustments adjustments = HostPatchAdjustments.plan(
                host,
                GameSide.DEDICATED_SERVER,
                Map.of(MEMBER_CONFIG, "fabric-entity-events-v1"),
                Map.of("fabric-entity-events-v1", jar),
                vanillaOf("net/minecraft/world/entity/LivingEntity", vanilla),
                new GameClassBytes() {
                    @Override
                    public Optional<byte[]> classBytes(String className) {
                        return className.replace('.', '/').equals("net/minecraft/world/entity/LivingEntity")
                                ? Optional.of(patched)
                                : Optional.empty();
                    }

                    @Override
                    public boolean isDefined(String className) {
                        return className.replace('.', '/').equals("net/minecraft/world/entity/LivingEntity");
                    }
                });

        assertEquals(2, adjustments.findings().size(), adjustments.findings().toString());
        for (HostPatchAdjustments.Finding finding : adjustments.findings()) {
            assertEquals("POLY-COMPAT-226", finding.code());
            assertFalse(finding.incompatible());
            assertTrue(finding.reason().contains("@WrapOperation bridge"), finding.reason());
        }

        byte[] applied = adjustments.apply("net.fabricmc.fabric.mixin.entity.event.LivingEntityMixin", mixinBytes);
        ClassModel appliedModel = ClassFile.of().parse(applied);
        List<String> methodNames = appliedModel.methods().stream()
                .map(m -> m.methodName().stringValue())
                .toList();
        assertTrue(methodNames.contains("poly$hostCall$onGetSleepingDirection"), methodNames.toString());
        assertTrue(methodNames.contains("poly$hostCall$setOccupiedState"), methodNames.toString());
    }

    @Test
    void fluidInteractionsAreAdaptedViaWrapOperation() throws Exception {
        ClassDesc boatDesc = ClassDesc.of("net.minecraft.world.entity.vehicle.boat.AbstractBoat");
        ClassDesc fluidStateDesc = ClassDesc.of("net.minecraft.world.level.material.FluidState");
        ClassDesc tagKeyDesc = ClassDesc.of("net.minecraft.tags.TagKey");

        byte[] boatVanilla = ClassFile.of().build(boatDesc, builder -> {
            builder.withMethod("checkInWater", MethodTypeDesc.of(CD_boolean), ClassFile.ACC_PUBLIC, method -> {
                method.withCode(code -> {
                    code.aconst_null();
                    code.aconst_null();
                    code.invokevirtual(fluidStateDesc, "is", MethodTypeDesc.of(CD_boolean, tagKeyDesc));
                    code.ireturn();
                });
            });
        });

        byte[] boatPatched = ClassFile.of().build(boatDesc, builder -> {
            builder.withMethod("checkInWater", MethodTypeDesc.of(CD_boolean), ClassFile.ACC_PUBLIC, method -> {
                method.withCode(code -> {
                    code.aconst_null();
                    code.aconst_null();
                    code.invokevirtual(boatDesc, "canBoatInFluid", MethodTypeDesc.of(CD_boolean, fluidStateDesc));
                    code.ireturn();
                });
            });
        });

        Annotation wrapAnnotation = Annotation.of(
                ClassDesc.ofDescriptor("Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;"),
                AnnotationElement.of("method", AnnotationValue.ofArray(AnnotationValue.ofString("checkInWater"))),
                AnnotationElement.of(
                        "at",
                        AnnotationValue.ofArray(
                                AnnotationValue.ofAnnotation(
                                        Annotation.of(
                                                ClassDesc.ofDescriptor("Lorg/spongepowered/asm/mixin/injection/At;"),
                                                AnnotationElement.ofString("value", "INVOKE"),
                                                AnnotationElement.ofString(
                                                        "target",
                                                        "Lnet/minecraft/world/level/material/FluidState;is(Lnet/minecraft/tags/TagKey;)Z"))))));

        ClassDesc mixinDesc = ClassDesc.of("net.fabricmc.fabric.mixin.content.registry.fluid.AbstractBoatMixin");
        byte[] mixinBytes = ClassFile.of().build(mixinDesc, builder -> {
            builder.with(RuntimeInvisibleAnnotationsAttribute.of(mixinAnnotation(boatDesc)));
            builder.withMethod(
                    "customFluidSupport",
                    MethodTypeDesc.of(CD_boolean, fluidStateDesc, tagKeyDesc, OPERATION),
                    ClassFile.ACC_PRIVATE,
                    method -> {
                        method.with(RuntimeVisibleAnnotationsAttribute.of(wrapAnnotation));
                        method.withCode(code -> {
                            code.iconst_1();
                            code.ireturn();
                        });
                    });
        });

        String configText = """
                {"required": true, "package": "net.fabricmc.fabric.mixin.content.registry.fluid", "compatibilityLevel": "JAVA_25", "mixins": ["AbstractBoatMixin"]}
                """;
        Path jar = jar(Map.of(
                MEMBER_CONFIG,
                configText,
                "net/fabricmc/fabric/mixin/content/registry/fluid/AbstractBoatMixin.class",
                mixinBytes));

        HostedLaunch host = new HostedLaunch(
                "MinecraftForge 26.2-65.1.5",
                List.of(),
                List.of(
                        new InjectionPointReplacement(
                                "net/minecraft/world/entity/vehicle/boat/AbstractBoat",
                                "checkInWater",
                                "Lnet/minecraft/world/level/material/FluidState;is(Lnet/minecraft/tags/TagKey;)Z",
                                "Lnet/minecraft/world/entity/vehicle/boat/AbstractBoat;canBoatInFluid(Lnet/minecraft/world/level/material/FluidState;)Z")),
                directory);

        HostPatchAdjustments adjustments = HostPatchAdjustments.plan(
                host,
                GameSide.DEDICATED_SERVER,
                Map.of(MEMBER_CONFIG, "fabric-content-registries-v0"),
                Map.of("fabric-content-registries-v0", jar),
                vanillaOf("net/minecraft/world/entity/vehicle/boat/AbstractBoat", boatVanilla),
                new GameClassBytes() {
                    @Override
                    public Optional<byte[]> classBytes(String className) {
                        return className
                                        .replace('.', '/')
                                        .equals("net/minecraft/world/entity/vehicle/boat/AbstractBoat")
                                ? Optional.of(boatPatched)
                                : Optional.empty();
                    }

                    @Override
                    public boolean isDefined(String className) {
                        return className
                                .replace('.', '/')
                                .equals("net/minecraft/world/entity/vehicle/boat/AbstractBoat");
                    }
                });

        assertEquals(1, adjustments.findings().size(), adjustments.findings().toString());
        HostPatchAdjustments.Finding finding = adjustments.findings().get(0);
        assertEquals("POLY-COMPAT-226", finding.code());
        assertFalse(finding.incompatible());
        assertTrue(finding.reason().contains("@WrapOperation bridge"), finding.reason());

        byte[] applied =
                adjustments.apply("net.fabricmc.fabric.mixin.content.registry.fluid.AbstractBoatMixin", mixinBytes);
        ClassModel appliedModel = ClassFile.of().parse(applied);
        List<String> methodNames = appliedModel.methods().stream()
                .map(m -> m.methodName().stringValue())
                .toList();
        assertTrue(methodNames.contains("poly$hostCall$customFluidSupport"), methodNames.toString());
    }

    private HostPatchAdjustments plan(Path jar, byte[] vanilla, byte[] patched) {
        return plan("t/Target", jar, vanilla, patched);
    }

    private HostPatchAdjustments plan(ClassDesc target, Path jar, byte[] vanilla, byte[] patched) {
        String desc = target.descriptorString();
        return plan(desc.substring(1, desc.length() - 1), jar, vanilla, patched);
    }

    private HostPatchAdjustments plan(String targetName, Path jar, byte[] vanilla, byte[] patched) {
        String internal = targetName.replace('.', '/');
        return plan(
                GameSide.DEDICATED_SERVER,
                Map.of(MEMBER_CONFIG, "example"),
                Map.of("example", jar),
                vanillaOf(internal, vanilla),
                new GameClassBytes() {
                    @Override
                    public Optional<byte[]> classBytes(String className) {
                        return className.replace('.', '/').equals(internal) ? Optional.of(patched) : Optional.empty();
                    }

                    @Override
                    public boolean isDefined(String className) {
                        return false;
                    }
                });
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

        HostPatchAdjustments adjustments = plan(
                GameSide.CLIENT,
                Map.of(EXTRACT_CONFIG, "example"),
                Map.of("example", jar),
                vanillaOf("t/Target", vanilla),
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

        HostPatchAdjustments adjustments = plan(
                GameSide.CLIENT,
                Map.of(EXTRACT_CONFIG, "example"),
                Map.of("example", jar),
                vanillaOf("t/Target", vanilla),
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

        HostPatchAdjustments adjustments = plan(
                GameSide.DEDICATED_SERVER,
                Map.of(CONFIG, "example"),
                Map.of("example", jar),
                vanillaOf("t/Target", vanilla),
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

    private static final ClassDesc EITHER = ClassDesc.of("com.mojang.datafixers.util.Either");
    private static final ClassDesc CD_Optional = ClassDesc.of("java.util.Optional");
    private static final String LOAD_CALL = "Lt/Loader;load(Ljava/lang/String;)Lcom/mojang/datafixers/util/Either;";
    private static final MethodTypeDesc WRAP_LOAD = MethodTypeDesc.of(
            EITHER, CD_String, ClassDesc.ofDescriptor("Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;"));
    private static final String WRAP_LOAD_CONFIG_TEXT = "{\"package\":\"m\",\"mixins\":[\"WrapLoadMixin\"]}";

    /**
     * MinecraftForge 26.3-66.1.0 wraps the registry element decoder in {@code ConditionCodec.wrap}: in {@code
     * ResourceManagerRegistryLoadTask.lambda$load$2}, {@code PendingRegistration.loadFromResource} returns an {@code
     * Either} whose left side is an {@code Optional} of the element, which the patch unwraps after the call. Fabric API
     * 0.165's {@code ResourceManagerRegistryLoadTaskMixin.modifyLootTable} wraps that call and maps the left side as a
     * {@code LootTable}; the game stopped with a {@code ClassCastException} ({@code corpusFabricForge} on 26.3). The
     * handler runs through a bridge that hands it the unwrapped value and wraps the left side of its result again; an
     * element the host's conditions skip does not reach it.
     */
    @Test
    void aWrapOfACallWhoseEitherThePatchReadsAsAnOptionalRunsThroughABridge() throws Exception {
        byte[] mixin = wrapLoadMixin(true);
        HostPatchAdjustments adjustments = wrapLoadPlan(mixin);

        assertEquals(1, adjustments.findings().size(), adjustments.findings().toString());
        HostPatchAdjustments.Finding finding = adjustments.findings().getFirst();
        assertEquals("POLY-COMPAT-226", finding.code(), finding.toString());
        assertTrue(finding.reason().contains("java/util/Optional"), finding.reason());
        byte[] applied = adjustments.apply("m.WrapLoadMixin", mixin);
        ClassModel model = ClassFile.of().parse(applied);
        assertFalse(annotationsOf(method(model, "wrapLoad", WRAP_LOAD)).contains("WrapOperation"));
        String bridgeAnnotations = annotationsOf(method(model, "poly$hostOptional$wrapLoad", WRAP_LOAD));
        assertTrue(
                bridgeAnnotations.contains("WrapOperation") && bridgeAnnotations.contains(LOAD_CALL),
                bridgeAnnotations);

        // The bridge as the patched game runs it: a decoded value, an element the host skips, a decoding error.
        ClassLoader loader = new ClassLoader(getClass().getClassLoader()) {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                byte[] bytes = switch (name) {
                    case "com.mojang.datafixers.util.Either" -> either();
                    case "m.WrapLoadMixin" -> applied;
                    default -> throw new ClassNotFoundException(name);
                };
                return defineClass(name, bytes, 0, bytes.length);
            }
        };
        Class<?> either = loader.loadClass("com.mojang.datafixers.util.Either");
        java.lang.reflect.Method left = either.getMethod("left", Object.class);
        java.lang.reflect.Method right = either.getMethod("right", Object.class);
        Class<?> mixinClass = loader.loadClass("m.WrapLoadMixin");
        java.lang.reflect.Method bridge = mixinClass.getDeclaredMethod(
                "poly$hostOptional$wrapLoad",
                String.class,
                com.llamalad7.mixinextras.injector.wrapoperation.Operation.class);
        bridge.setAccessible(true);
        Object instance = mixinClass.getDeclaredConstructor().newInstance();
        List<List<Object>> calls = new java.util.ArrayList<>();
        Function<Object, com.llamalad7.mixinextras.injector.wrapoperation.Operation<Object>> original =
                result -> arguments -> {
                    calls.add(List.of(arguments));
                    return result;
                };

        Object decoded = bridge.invoke(instance, "id", original.apply(left.invoke(null, Optional.of("table"))));
        Object skipped = left.invoke(null, Optional.empty());
        Object notDecoded = bridge.invoke(instance, "id", original.apply(skipped));
        Object failed = bridge.invoke(instance, "id", original.apply(right.invoke(null, "boom")));

        assertEquals(
                Optional.of(Optional.of("table+fabric")),
                either.getMethod("left").invoke(decoded));
        assertTrue(notDecoded == skipped, String.valueOf(notDecoded));
        assertEquals(Optional.of("boom"), either.getMethod("right").invoke(failed));
        assertEquals(List.of(List.of("id"), List.of("id"), List.of("id")), calls);
    }

    /**
     * A wrap of such a call whose handler calls the original with arguments other than its own cannot be handed a
     * result the bridge already has: the mod is not loaded ({@code POLY-COMPAT-224}) rather than crashing the game.
     */
    @Test
    void aWrapOfACallWhoseEitherThePatchReadsAsAnOptionalWithOtherArgumentsMakesTheModIncompatible() throws Exception {
        HostPatchAdjustments adjustments = wrapLoadPlan(wrapLoadMixin(false));

        assertEquals(1, adjustments.findings().size(), adjustments.findings().toString());
        HostPatchAdjustments.Finding finding = adjustments.findings().getFirst();
        assertTrue(finding.incompatible(), finding.toString());
        assertTrue(finding.reason().contains("this injection does not run"), finding.reason());
    }

    private static final ClassDesc LIST_LOADER = ClassDesc.of("t.Loader");
    private static final String WORLD_FIELD = "Lt/Loader;WORLD:Ljava/util/List;";
    private static final String WORLD_AND_HOST_CALL = "Lt/Loader;worldAndHost()Ljava/util/List;";
    private static final MethodTypeDesc LIST_RESULT = MethodTypeDesc.of(CD_List);
    private static final String HOST_LIST_CONFIG_TEXT = "{\"package\":\"m\",\"mixins\":[\"HostListMixin\"]}";

    /**
     * MinecraftForge 26.3-66.1.0 reads {@code RegistryDataLoader.getWorldAndDimensionRegistries()} ({@code
     * WORLD_REGISTRIES} followed by {@code DIMENSION_REGISTRIES}) where the vanilla data generators read {@code
     * WORLD_REGISTRIES}, which Fabric API's registry sync redirects to its own list; {@code fabric-registry-sync-v0}
     * was not loaded ({@code POLY-COMPAT-224}). The redirect moves to the host's call through a bridge that returns the
     * handler's list followed by the host's own entries.
     */
    @Test
    void aRedirectOfAListFieldTheHostExtendsReturnsTheModsListWithTheHostsEntries() throws Exception {
        byte[] mixin = hostListMixin();
        HostPatchAdjustments adjustments = hostListPlan(mixin, listLoader(true));

        assertEquals(1, adjustments.findings().size(), adjustments.findings().toString());
        HostPatchAdjustments.Finding finding = adjustments.findings().getFirst();
        assertEquals("POLY-COMPAT-226", finding.code(), finding.toString());
        assertTrue(finding.reason().contains(WORLD_AND_HOST_CALL), finding.reason());
        byte[] applied = adjustments.apply("m.HostListMixin", mixin);
        ClassModel model = ClassFile.of().parse(applied);
        assertFalse(annotationsOf(method(model, "worldRegistries", LIST_RESULT)).contains("Redirect"));
        String bridgeAnnotations = annotationsOf(method(model, "poly$hostList$worldRegistries", LIST_RESULT));
        assertTrue(
                bridgeAnnotations.contains("Redirect")
                        && bridgeAnnotations.contains(WORLD_AND_HOST_CALL)
                        && bridgeAnnotations.contains("INVOKE")
                        && !bridgeAnnotations.contains(WORLD_FIELD),
                bridgeAnnotations);

        // The bridge as the patched game runs it.
        byte[] loaderBytes = listLoader(true);
        ClassLoader loader = new ClassLoader(getClass().getClassLoader()) {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                byte[] bytes = switch (name) {
                    case "t.Loader" -> loaderBytes;
                    case "m.HostListMixin" -> applied;
                    default -> throw new ClassNotFoundException(name);
                };
                return defineClass(name, bytes, 0, bytes.length);
            }
        };
        Class<?> loaderClass = loader.loadClass("t.Loader");
        loaderClass.getField("WORLD").set(null, List.of("world"));
        loaderClass.getField("HOST").set(null, List.of("world", "dimension"));
        java.lang.reflect.Method bridge =
                loader.loadClass("m.HostListMixin").getDeclaredMethod("poly$hostList$worldRegistries");
        bridge.setAccessible(true);

        assertEquals(List.of("world", "fabric", "dimension"), bridge.invoke(null));

        loaderClass.getField("HOST").set(null, List.of("other", "dimension"));
        java.lang.reflect.InvocationTargetException mismatch = org.junit.jupiter.api.Assertions.assertThrows(
                java.lang.reflect.InvocationTargetException.class, () -> bridge.invoke(null));
        assertTrue(
                mismatch.getCause() instanceof IllegalStateException
                        && mismatch.getCause().getMessage().startsWith("POLY-COMPAT-225"),
                String.valueOf(mismatch.getCause()));
    }

    /** A host method that does not read the field is not known to extend it: the mod is not loaded. */
    @Test
    void aRedirectOfAListFieldWhoseHostCallDoesNotReadItMakesTheModIncompatible() throws Exception {
        HostPatchAdjustments adjustments = hostListPlan(hostListMixin(), listLoader(false));

        assertEquals(1, adjustments.findings().size(), adjustments.findings().toString());
        HostPatchAdjustments.Finding finding = adjustments.findings().getFirst();
        assertTrue(finding.incompatible(), finding.toString());
        assertTrue(finding.reason().contains("this injection does not run"), finding.reason());
    }

    private HostPatchAdjustments hostListPlan(byte[] mixin, byte[] loader) throws IOException {
        Path jar = jar(Map.of(CONFIG, HOST_LIST_CONFIG_TEXT, "m/HostListMixin.class", mixin));
        HostedLaunch host = new HostedLaunch(
                "Host 1",
                List.of(),
                List.of(new InjectionPointReplacement(
                        "t/Target",
                        "forWorldLayer",
                        WORLD_FIELD,
                        WORLD_AND_HOST_CALL,
                        InjectionPointReplacement.Placement.FIELD_THEN_HOST_ENTRIES)),
                directory);
        return HostPatchAdjustments.plan(
                host,
                GameSide.DEDICATED_SERVER,
                Map.of(CONFIG, "example"),
                Map.of("example", jar),
                vanillaOf("t/Target", listTarget(false)),
                classes(Map.of("t/Target", listTarget(true), "t/Loader", loader)));
    }

    /** {@code static List forWorldLayer()} returning {@code Loader.WORLD}, or {@code Loader.worldAndHost()} patched. */
    private static byte[] listTarget(boolean patched) {
        return ClassFile.of()
                .build(
                        TARGET,
                        builder -> builder.withMethodBody(
                                "forWorldLayer", LIST_RESULT, ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC, code -> {
                                    if (patched) {
                                        code.invokestatic(LIST_LOADER, "worldAndHost", LIST_RESULT);
                                    } else {
                                        code.getstatic(LIST_LOADER, "WORLD", CD_List);
                                    }
                                    code.areturn();
                                }));
    }

    /**
     * The host's {@code t.Loader} with the public static fields {@code WORLD} and {@code HOST}; {@code worldAndHost()}
     * returns {@code HOST}, after reading {@code WORLD} when {@code readsWorld}.
     */
    private static byte[] listLoader(boolean readsWorld) {
        return ClassFile.of().build(LIST_LOADER, builder -> {
            builder.withFlags(ClassFile.ACC_PUBLIC);
            builder.withField("WORLD", CD_List, ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC);
            builder.withField("HOST", CD_List, ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC);
            builder.withMethodBody("worldAndHost", LIST_RESULT, ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC, code -> {
                if (readsWorld) {
                    code.getstatic(LIST_LOADER, "WORLD", CD_List);
                    code.pop();
                }
                code.getstatic(LIST_LOADER, "HOST", CD_List);
                code.areturn();
            });
        });
    }

    /**
     * A mixin like Fabric API's {@code RegistriesDatapackGeneratorMixin}: {@code @Redirect} of {@code Loader.WORLD} in
     * {@code forWorldLayer} by {@code static List worldRegistries()}, which returns {@code ["world", "fabric"]}.
     */
    private static byte[] hostListMixin() {
        Annotation redirect = Annotation.of(
                ClassDesc.ofDescriptor("Lorg/spongepowered/asm/mixin/injection/Redirect;"),
                AnnotationElement.of("method", AnnotationValue.ofArray(AnnotationValue.ofString("forWorldLayer"))),
                AnnotationElement.ofAnnotation(
                        "at",
                        Annotation.of(
                                ClassDesc.ofDescriptor("Lorg/spongepowered/asm/mixin/injection/At;"),
                                AnnotationElement.ofString("value", "FIELD"),
                                AnnotationElement.ofString("target", WORLD_FIELD))));
        return ClassFile.of().build(ClassDesc.of("m.HostListMixin"), builder -> {
            builder.with(RuntimeInvisibleAnnotationsAttribute.of(mixinAnnotation()));
            builder.withMethod("worldRegistries", LIST_RESULT, ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC, method -> {
                method.with(RuntimeVisibleAnnotationsAttribute.of(redirect));
                method.withCode(code -> {
                    code.ldc("world");
                    code.ldc("fabric");
                    code.invokestatic(CD_List, "of", MethodTypeDesc.of(CD_List, CD_Object, CD_Object), true);
                    code.areturn();
                });
            });
        });
    }

    private HostPatchAdjustments wrapLoadPlan(byte[] mixin) throws IOException {
        Path jar = jar(Map.of(CONFIG, WRAP_LOAD_CONFIG_TEXT, "m/WrapLoadMixin.class", mixin));
        return plan(
                GameSide.DEDICATED_SERVER,
                Map.of(CONFIG, "example"),
                Map.of("example", jar),
                vanillaOf("t/Target", loadTarget(false)),
                classes(loadTarget(true)));
    }

    /**
     * {@code static Object lambda$load$2(String id)} returning {@code Loader.load(id)}; {@code patched}, it reads the
     * left side of that {@code Either} as an {@code Optional} and returns its value, as Forge's patch does.
     */
    private static byte[] loadTarget(boolean patched) {
        return ClassFile.of()
                .build(
                        TARGET,
                        builder -> builder.withMethodBody(
                                "lambda$load$2",
                                MethodTypeDesc.of(CD_Object, CD_String),
                                ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC,
                                code -> {
                                    code.aload(0);
                                    code.invokestatic(
                                            ClassDesc.of("t.Loader"), "load", MethodTypeDesc.of(EITHER, CD_String));
                                    if (patched) {
                                        code.astore(1);
                                        code.aload(1);
                                        code.invokevirtual(EITHER, "left", MethodTypeDesc.of(CD_Optional));
                                        code.invokevirtual(CD_Optional, "get", MethodTypeDesc.of(CD_Object));
                                        code.checkcast(CD_Optional);
                                        code.invokevirtual(CD_Optional, "get", MethodTypeDesc.of(CD_Object));
                                    }
                                    code.areturn();
                                }));
    }

    /**
     * A mixin like Fabric API's {@code modifyLootTable}: {@code wrapLoad(id, original)} calls {@code original} with
     * {@code id} ({@code ownArguments}) or with another string, and appends {@code "+fabric"} to a decoded value.
     */
    private static byte[] wrapLoadMixin(boolean ownArguments) {
        Annotation wrap = Annotation.of(
                ClassDesc.ofDescriptor("Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;"),
                AnnotationElement.of("method", AnnotationValue.ofArray(AnnotationValue.ofString("lambda$load$2"))),
                AnnotationElement.of(
                        "at",
                        AnnotationValue.ofArray(AnnotationValue.ofAnnotation(Annotation.of(
                                ClassDesc.ofDescriptor("Lorg/spongepowered/asm/mixin/injection/At;"),
                                AnnotationElement.ofString("value", "INVOKE"),
                                AnnotationElement.ofString("target", LOAD_CALL))))));
        return ClassFile.of().build(ClassDesc.of("m.WrapLoadMixin"), builder -> {
            builder.with(RuntimeInvisibleAnnotationsAttribute.of(mixinAnnotation()));
            builder.withMethodBody(ConstantDescs.INIT_NAME, ConstantDescs.MTD_void, ClassFile.ACC_PUBLIC, code -> {
                code.aload(0);
                code.invokespecial(CD_Object, ConstantDescs.INIT_NAME, ConstantDescs.MTD_void);
                code.return_();
            });
            builder.withMethod("wrapLoad", WRAP_LOAD, ClassFile.ACC_PRIVATE, method -> {
                method.with(RuntimeVisibleAnnotationsAttribute.of(wrap));
                method.withCode(code -> {
                    java.lang.classfile.Label returnResult = code.newLabel();
                    code.aload(2);
                    code.iconst_1();
                    code.anewarray(CD_Object);
                    code.dup();
                    code.iconst_0();
                    if (ownArguments) {
                        code.aload(1);
                    } else {
                        code.ldc("other");
                    }
                    code.aastore();
                    code.invokeinterface(OPERATION, "call", MethodTypeDesc.of(CD_Object, CD_Object.arrayType()));
                    code.checkcast(EITHER);
                    code.astore(3);
                    code.aload(3);
                    code.invokevirtual(EITHER, "left", MethodTypeDesc.of(CD_Optional));
                    code.invokevirtual(CD_Optional, "isPresent", MethodTypeDesc.of(CD_boolean));
                    code.ifeq(returnResult);
                    code.aload(3);
                    code.invokevirtual(EITHER, "left", MethodTypeDesc.of(CD_Optional));
                    code.invokevirtual(CD_Optional, "get", MethodTypeDesc.of(CD_Object));
                    code.checkcast(CD_String);
                    code.ldc("+fabric");
                    code.invokevirtual(CD_String, "concat", MethodTypeDesc.of(CD_String, CD_String));
                    code.invokestatic(EITHER, "left", MethodTypeDesc.of(EITHER, CD_Object));
                    code.areturn();
                    code.labelBinding(returnResult);
                    code.aload(3);
                    code.areturn();
                });
            });
        });
    }

    /**
     * A stand-in for DataFixerUpper's {@code Either} with the members the bridge uses: {@code static left(Object)},
     * {@code static right(Object)}, {@code left()} and {@code right()}.
     */
    private static byte[] either() {
        MethodTypeDesc constructor = MethodTypeDesc.of(CD_void, CD_Object, CD_Object);
        return ClassFile.of().build(EITHER, builder -> {
            builder.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL);
            builder.withField("leftValue", CD_Object, ClassFile.ACC_PRIVATE | ClassFile.ACC_FINAL);
            builder.withField("rightValue", CD_Object, ClassFile.ACC_PRIVATE | ClassFile.ACC_FINAL);
            builder.withMethodBody(ConstantDescs.INIT_NAME, constructor, ClassFile.ACC_PRIVATE, code -> {
                code.aload(0);
                code.invokespecial(CD_Object, ConstantDescs.INIT_NAME, ConstantDescs.MTD_void);
                code.aload(0);
                code.aload(1);
                code.putfield(EITHER, "leftValue", CD_Object);
                code.aload(0);
                code.aload(2);
                code.putfield(EITHER, "rightValue", CD_Object);
                code.return_();
            });
            for (String side : List.of("left", "right")) {
                builder.withMethodBody(
                        side,
                        MethodTypeDesc.of(EITHER, CD_Object),
                        ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                        code -> {
                            code.new_(EITHER);
                            code.dup();
                            if (side.equals("left")) {
                                code.aload(0);
                                code.aconst_null();
                            } else {
                                code.aconst_null();
                                code.aload(0);
                            }
                            code.invokespecial(EITHER, ConstantDescs.INIT_NAME, constructor);
                            code.areturn();
                        });
                builder.withMethodBody(side, MethodTypeDesc.of(CD_Optional), ClassFile.ACC_PUBLIC, code -> {
                    code.aload(0);
                    code.getfield(EITHER, side + "Value", CD_Object);
                    code.invokestatic(CD_Optional, "ofNullable", MethodTypeDesc.of(CD_Optional, CD_Object));
                    code.areturn();
                });
            }
        });
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

    /** A handler parameter whose type the patched method has twice cannot be placed: the mod is incompatible. */
    @Test
    void anInjectionWhoseReorderedParametersAreAmbiguousMakesTheModIncompatible() throws Exception {
        byte[] vanilla = extractTarget(MethodTypeDesc.of(CD_void, CD_String, CD_float));
        byte[] patched = extractTarget(MethodTypeDesc.of(CD_void, CD_float, CD_String, CD_String));
        MethodTypeDesc handlerType = MethodTypeDesc.of(CD_void, CD_String, CD_float, CALLBACK_INFO);
        Path jar = jar(Map.of(EXTRACT_CONFIG, EXTRACT_CONFIG_TEXT, "m/ExtractMixin.class", extractMixin(handlerType)));

        HostPatchAdjustments adjustments = plan(
                GameSide.CLIENT,
                Map.of(EXTRACT_CONFIG, "example"),
                Map.of("example", jar),
                vanillaOf("t/Target", vanilla),
                classes(patched));

        assertTrue(
                adjustments.findings().getFirst().reason().contains("does not run")
                        && adjustments.findings().getFirst().incompatible(),
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
                vanillaOf("t/Target", vanilla),
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
                vanillaOf("t/Target", helperTarget(true)),
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
                        && !annotations.contains("require"),
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
                vanillaOf("t/Target", helperTarget(true)),
                classes(helperTarget(false)));

        assertTrue(
                adjustments.findings().getFirst().reason().contains("does not run")
                        && adjustments.findings().getFirst().incompatible(),
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

        HostPatchAdjustments adjustments = plan(
                GameSide.DEDICATED_SERVER,
                Map.of(CONFIG, "example"),
                Map.of("example", jar),
                vanillaOf("t/Target", vanilla),
                classes(Map.of("t/Target", patched, "h/HostApi", hostApi)));

        assertEquals(1, adjustments.findings().size(), adjustments.findings().toString());
        assertFalse(adjustments.hasIncompatible(), adjustments.findings().toString());
        assertFalse(adjustments.findings().getFirst().incompatible());
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

    @Test
    void anIncompatibleDefaultMethodConflictMakesModIncompatible() throws Exception {
        MethodTypeDesc custom = MethodTypeDesc.of(CD_String);
        byte[] hostApi = defaultInterface("h.HostApi", "customAction", custom);
        byte[] modApi = defaultInterface("m.ModApi", "customAction", custom);
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

        HostPatchAdjustments adjustments = plan(
                GameSide.DEDICATED_SERVER,
                Map.of(CONFIG, "example"),
                Map.of("example", jar),
                vanillaOf("t/Target", vanilla),
                classes(Map.of("t/Target", patched, "h/HostApi", hostApi)));

        assertEquals(1, adjustments.findings().size(), adjustments.findings().toString());
        assertTrue(
                adjustments.findings().getFirst().incompatible(),
                adjustments.findings().toString());
        assertTrue(adjustments.hasIncompatible());
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

        HostPatchAdjustments adjustments = plan(
                GameSide.DEDICATED_SERVER,
                Map.of(CONFIG, "example"),
                Map.of("example", jar),
                vanillaOf("t/Target", ClassFile.of().build(TARGET, builder -> {})),
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

    private static byte[] unmodifiableGetterMixin() {
        ClassDesc self = ClassDesc.of("m.MemberAccessor");
        return ClassFile.of().build(self, builder -> {
            builder.with(RuntimeInvisibleAnnotationsAttribute.of(mixinAnnotation()));
            builder.withField("connection", CD_List, ClassFile.ACC_PRIVATE);
            builder.withMethodBody("getConnection", MethodTypeDesc.of(CD_List), ClassFile.ACC_PUBLIC, code -> {
                code.aload(0)
                        .getfield(self, "connection", CD_List)
                        .invokestatic(
                                ClassDesc.of("java.util.Collections"),
                                "unmodifiableList",
                                MethodTypeDesc.of(CD_List, CD_List))
                        .areturn();
            });
        });
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
     * a {@code ClassCastException} ({@code corpusFabricNeoForgeClient}). An injection that uses an interface a mixin
     * that cannot apply adds, or such an accessor mixin, cannot work either.
     */
    @Test
    void anInjectionUsingTheInterfaceOfAMixinThatCannotApplyIsIncompatible() throws Exception {
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

        HostPatchAdjustments adjustments = plan(
                GameSide.CLIENT,
                Map.of(CONFIG, "example"),
                Map.of("example", jar),
                vanillaOf("t/Target", vanilla),
                classes(patched));

        assertTrue(
                adjustments.findings().stream()
                        .filter(finding -> finding.mixin().equals("m/UserMixin"))
                        .allMatch(HostPatchAdjustments.Finding::incompatible),
                adjustments.findings().toString());
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
    }

    private static final ClassDesc SNIPPET = ClassDesc.of("t.Snippet");

    /**
     * ADR-0026 39: Fabric API's {@code RenderPipelineBuilderMixin} wraps {@code new RenderPipeline.Snippet(...)} in
     * {@code buildSnippet} ({@code @WrapOperation} at {@code NEW}, no constructor descriptor); NeoForge's patch gives the
     * constructor one more parameter. Mixin then rejects the handler's signature, and with it every other mixin on the
     * class (Iris's {@code PipelineBuilderMixin}, whose interface Iris's {@code RenderPipeline} mixin casts to). The
     * mod is incompatible.
     */
    @Test
    void aWrapOfAConstructorCallThePatchGaveOtherParametersMakesTheModIncompatible() throws Exception {
        byte[] vanilla = snippetTarget(MethodTypeDesc.of(CD_void, CD_String));
        byte[] patched = snippetTarget(MethodTypeDesc.of(CD_void, CD_String, CD_int));
        String configText = "{\"package\":\"m\",\"client\":[\"WrapNewMixin\"]}";
        Path jar = jar(Map.of(CONFIG, configText, "m/WrapNewMixin.class", wrapNewMixin()));

        HostPatchAdjustments adjustments = plan(
                GameSide.CLIENT,
                Map.of(CONFIG, "example"),
                Map.of("example", jar),
                vanillaOf("t/Target", vanilla),
                classes(patched));

        assertEquals(1, adjustments.findings().size(), adjustments.findings().toString());
        String reason = adjustments.findings().getFirst().reason();
        assertTrue(reason.contains("(Ljava/lang/String;I)V") && reason.endsWith("this injection does not run"), reason);
        assertTrue(
                adjustments.findings().getFirst().incompatible(),
                adjustments.findings().toString());
    }

    /**
     * An injection inside a slice whose boundary the patch removed finds nothing in the patched method (Fabric API's
     * {@code LevelChunkMixin.onRemoveBlockEntity} next to Forge): the mod is incompatible, found before the launch.
     */
    @Test
    void anInjectionInASliceWhoseBoundaryThePatchRemovedMakesTheModIncompatible() throws Exception {
        ClassDesc at = ClassDesc.ofDescriptor("Lorg/spongepowered/asm/mixin/injection/At;");
        Annotation inject = Annotation.of(
                ClassDesc.ofDescriptor("Lorg/spongepowered/asm/mixin/injection/Inject;"),
                AnnotationElement.of("method", AnnotationValue.ofArray(AnnotationValue.ofString("run"))),
                AnnotationElement.of(
                        "at",
                        AnnotationValue.ofArray(AnnotationValue.ofAnnotation(
                                Annotation.of(at, AnnotationElement.ofString("value", "TAIL"))))),
                AnnotationElement.of(
                        "slice",
                        AnnotationValue.ofArray(AnnotationValue.ofAnnotation(Annotation.of(
                                ClassDesc.ofDescriptor("Lorg/spongepowered/asm/mixin/injection/Slice;"),
                                AnnotationElement.of(
                                        "from",
                                        AnnotationValue.ofAnnotation(Annotation.of(
                                                at,
                                                AnnotationElement.ofString("value", "INVOKE"),
                                                AnnotationElement.ofString("target", "Lt/Target;old()V")))))))));
        byte[] mixin = mixin("m.SliceMixin", List.of(new Handler("inSlice", inject)));
        Path jar = jar(Map.of(CONFIG, "{\"package\":\"m\",\"mixins\":[\"SliceMixin\"]}", "m/SliceMixin.class", mixin));

        HostPatchAdjustments adjustments = plan(
                GameSide.DEDICATED_SERVER,
                Map.of(CONFIG, "example"),
                Map.of("example", jar),
                vanillaOf("t/Target", target(true)),
                classes(target(false)));

        assertEquals(1, adjustments.findings().size(), adjustments.findings().toString());
        HostPatchAdjustments.Finding finding = adjustments.findings().getFirst();
        assertTrue(
                finding.incompatible()
                        && finding.reason().contains("slice")
                        && finding.reason().contains("old()V"),
                finding.toString());
    }

    /**
     * The slice's boundary is still there, but the patch moved the injection point before it, out of the slice (Fabric
     * API's {@code LevelChunkMixin.onRemoveBlockEntity}: Forge's {@code getBlockEntity} removes the entry before {@code
     * createBlockEntity}). Mixin finds nothing in the slice, so the mod is incompatible.
     */
    @Test
    void anInjectionPointThePatchMovedOutOfItsSliceMakesTheModIncompatible() throws Exception {
        ClassDesc at = ClassDesc.ofDescriptor("Lorg/spongepowered/asm/mixin/injection/At;");
        Annotation redirect = Annotation.of(
                ClassDesc.ofDescriptor("Lorg/spongepowered/asm/mixin/injection/Redirect;"),
                AnnotationElement.of("method", AnnotationValue.ofArray(AnnotationValue.ofString("run"))),
                AnnotationElement.of(
                        "at",
                        AnnotationValue.ofAnnotation(Annotation.of(
                                at,
                                AnnotationElement.ofString("value", "INVOKE"),
                                AnnotationElement.ofString("target", "Lt/Target;moved()V")))),
                AnnotationElement.of(
                        "slice",
                        AnnotationValue.ofAnnotation(Annotation.of(
                                ClassDesc.ofDescriptor("Lorg/spongepowered/asm/mixin/injection/Slice;"),
                                AnnotationElement.of(
                                        "from",
                                        AnnotationValue.ofAnnotation(Annotation.of(
                                                at,
                                                AnnotationElement.ofString("value", "INVOKE"),
                                                AnnotationElement.ofString("target", "Lt/Target;old()V"))))))));
        byte[] mixin = mixin("m.SliceMixin", List.of(new Handler("inSlice", redirect)));
        Path jar = jar(Map.of(CONFIG, "{\"package\":\"m\",\"mixins\":[\"SliceMixin\"]}", "m/SliceMixin.class", mixin));
        // Vanilla calls old() and then moved(); the patch calls moved() first.
        byte[] vanilla = ClassFile.of()
                .build(
                        TARGET,
                        builder -> builder.withMethodBody(
                                "run",
                                MethodTypeDesc.of(CD_void),
                                0,
                                code -> code.invokestatic(TARGET, "old", MethodTypeDesc.of(CD_void))
                                        .invokestatic(TARGET, "moved", MethodTypeDesc.of(CD_void))
                                        .return_()));
        byte[] patched = ClassFile.of()
                .build(
                        TARGET,
                        builder -> builder.withMethodBody(
                                "run",
                                MethodTypeDesc.of(CD_void),
                                0,
                                code -> code.invokestatic(TARGET, "moved", MethodTypeDesc.of(CD_void))
                                        .invokestatic(TARGET, "old", MethodTypeDesc.of(CD_void))
                                        .return_()));

        HostPatchAdjustments adjustments = plan(
                GameSide.DEDICATED_SERVER,
                Map.of(CONFIG, "example"),
                Map.of("example", jar),
                vanillaOf("t/Target", vanilla),
                classes(patched));

        assertEquals(1, adjustments.findings().size(), adjustments.findings().toString());
        HostPatchAdjustments.Finding finding = adjustments.findings().getFirst();
        assertTrue(
                finding.incompatible() && finding.reason().contains("inside the injection's slice"),
                finding.toString());
        // In the same order as vanilla nothing is reported.
        assertTrue(plan(
                        GameSide.DEDICATED_SERVER,
                        Map.of(CONFIG, "example"),
                        Map.of("example", jar),
                        vanillaOf("t/Target", vanilla),
                        classes(vanilla))
                .findings()
                .isEmpty());
    }

    /**
     * Without a descriptor Mixin injects into the first method of that name only. NeoForge's {@code
     * BaseContainerBlockEntity.setItem(int, ItemStack)} calls its new {@code setItem(int, ItemStack, boolean)}, which
     * holds the call All Stacks Infinite redirects: the call is still in the class, but not in the method Mixin picks.
     */
    @Test
    void aCallThePatchMovedIntoANewOverloadIsNotWhereANameOnlySelectorLooks() throws Exception {
        Annotation redirect = Annotation.of(
                ClassDesc.ofDescriptor("Lorg/spongepowered/asm/mixin/injection/Redirect;"),
                AnnotationElement.of("method", AnnotationValue.ofArray(AnnotationValue.ofString("setItem"))),
                AnnotationElement.of(
                        "at",
                        AnnotationValue.ofAnnotation(Annotation.of(
                                ClassDesc.ofDescriptor("Lorg/spongepowered/asm/mixin/injection/At;"),
                                AnnotationElement.ofString("value", "INVOKE"),
                                AnnotationElement.ofString("target", "Lt/Target;old()V")))));
        byte[] mixin = mixin("m.OverloadMixin", List.of(new Handler("limit", redirect)));
        Path jar = jar(
                Map.of(CONFIG, "{\"package\":\"m\",\"mixins\":[\"OverloadMixin\"]}", "m/OverloadMixin.class", mixin));
        MethodTypeDesc one = MethodTypeDesc.of(CD_void, CD_int);
        MethodTypeDesc two = MethodTypeDesc.of(CD_void, CD_int, CD_boolean);
        byte[] vanilla = ClassFile.of()
                .build(
                        TARGET,
                        builder -> builder.withMethodBody(
                                "setItem",
                                one,
                                0,
                                code -> code.invokestatic(TARGET, "old", MethodTypeDesc.of(CD_void))
                                        .return_()));
        byte[] patched = ClassFile.of()
                .build(
                        TARGET,
                        builder -> builder.withMethodBody(
                                        "setItem",
                                        one,
                                        0,
                                        code -> code.aload(0)
                                                .iload(1)
                                                .iconst_1()
                                                .invokevirtual(TARGET, "setItem", two)
                                                .return_())
                                .withMethodBody(
                                        "setItem",
                                        two,
                                        0,
                                        code -> code.invokestatic(TARGET, "old", MethodTypeDesc.of(CD_void))
                                                .return_()));

        HostPatchAdjustments adjustments = plan(
                GameSide.DEDICATED_SERVER,
                Map.of(CONFIG, "example"),
                Map.of("example", jar),
                vanillaOf("t/Target", vanilla),
                classes(patched));

        assertEquals(1, adjustments.findings().size(), adjustments.findings().toString());
        assertTrue(
                adjustments.findings().getFirst().incompatible(),
                adjustments.findings().toString());
    }

    @Test
    void aSlicedCallOnAReceiverFieldMovedBeforeTheOtherReceiverFieldRetargetsTheSlice() throws Exception {
        ClassDesc mapDesc = ClassDesc.of("java.util.Map");
        MethodTypeDesc removeDesc = MethodTypeDesc.of(CD_Object, CD_Object);
        MethodTypeDesc getDesc = MethodTypeDesc.of(CD_Object, CD_Object);
        ClassDesc at = ClassDesc.ofDescriptor("Lorg/spongepowered/asm/mixin/injection/At;");
        Annotation redirect = Annotation.of(
                ClassDesc.ofDescriptor("Lorg/spongepowered/asm/mixin/injection/Redirect;"),
                AnnotationElement.of("method", AnnotationValue.ofArray(AnnotationValue.ofString("get"))),
                AnnotationElement.of(
                        "at",
                        AnnotationValue.ofAnnotation(Annotation.of(
                                at,
                                AnnotationElement.ofString("value", "INVOKE"),
                                AnnotationElement.ofString(
                                        "target", "Ljava/util/Map;remove(Ljava/lang/Object;)Ljava/lang/Object;")))),
                AnnotationElement.of(
                        "slice",
                        AnnotationValue.ofAnnotation(
                                Annotation.of(
                                        ClassDesc.ofDescriptor("Lorg/spongepowered/asm/mixin/injection/Slice;"),
                                        AnnotationElement.of(
                                                "from",
                                                AnnotationValue.ofAnnotation(
                                                        Annotation.of(
                                                                at,
                                                                AnnotationElement.ofString("value", "INVOKE"),
                                                                AnnotationElement.ofString(
                                                                        "target",
                                                                        "Lt/Target;create(Ljava/lang/Object;)Ljava/lang/Object;"))))))));
        byte[] mixin = ClassFile.of().build(ClassDesc.of("m.ChunkSliceMixin"), builder -> {
            builder.with(RuntimeInvisibleAnnotationsAttribute.of(mixinAnnotation()));
            builder.withMethod(
                    "onRemove", MethodTypeDesc.of(CD_Object, mapDesc, CD_Object), ClassFile.ACC_PRIVATE, method -> {
                        method.with(RuntimeVisibleAnnotationsAttribute.of(redirect));
                        method.withCode(code -> code.aconst_null().areturn());
                    });
        });
        Path jar = jar(Map.of(
                CONFIG, "{\"package\":\"m\",\"mixins\":[\"ChunkSliceMixin\"]}", "m/ChunkSliceMixin.class", mixin));
        byte[] vanilla = ClassFile.of().build(TARGET, builder -> {
            builder.withField("pending", mapDesc, 0);
            builder.withField("entities", mapDesc, 0);
            builder.withMethodBody("get", getDesc, 0, code -> {
                code.aload(0)
                        .getfield(TARGET, "pending", mapDesc)
                        .aload(1)
                        .invokeinterface(mapDesc, "remove", removeDesc)
                        .pop()
                        .aload(0)
                        .aload(1)
                        .invokevirtual(TARGET, "create", getDesc)
                        .pop()
                        .aload(0)
                        .getfield(TARGET, "entities", mapDesc)
                        .aload(1)
                        .invokeinterface(mapDesc, "remove", removeDesc)
                        .areturn();
            });
        });
        byte[] patched = ClassFile.of().build(TARGET, builder -> {
            builder.withField("pending", mapDesc, 0);
            builder.withField("entities", mapDesc, 0);
            builder.withMethodBody("get", getDesc, 0, code -> {
                code.aload(0)
                        .getfield(TARGET, "entities", mapDesc)
                        .aload(1)
                        .invokeinterface(mapDesc, "remove", removeDesc)
                        .pop()
                        .aload(0)
                        .getfield(TARGET, "pending", mapDesc)
                        .aload(1)
                        .invokeinterface(mapDesc, "remove", removeDesc)
                        .pop()
                        .aload(0)
                        .aload(1)
                        .invokevirtual(TARGET, "create", getDesc)
                        .areturn();
            });
        });

        HostPatchAdjustments adjustments = plan(
                GameSide.DEDICATED_SERVER,
                Map.of(CONFIG, "example"),
                Map.of("example", jar),
                vanillaOf("t/Target", vanilla),
                classes(patched));

        assertEquals(1, adjustments.findings().size(), adjustments.findings().toString());
        HostPatchAdjustments.Finding finding = adjustments.findings().getFirst();
        assertFalse(finding.incompatible(), finding.toString());
        assertEquals("POLY-COMPAT-226", finding.code());

        ClassModel rewritten = ClassFile.of().parse(adjustments.apply("m.ChunkSliceMixin", mixin));
        MethodModel handler = rewritten.methods().stream()
                .filter(method -> method.methodName().equalsString("onRemove"))
                .findFirst()
                .orElseThrow();
        String annotations = annotationsOf(handler);
        assertTrue(annotations.contains("Lt/Target;pending:Ljava/util/Map;"), annotations);
        assertFalse(annotations.contains("Lt/Target;create("), annotations);
    }

    @Test
    void aCallTheHostMovedToAnotherMethodRetargetsTheMethodSelector() throws Exception {
        Annotation redirect = Annotation.of(
                ClassDesc.ofDescriptor("Lorg/spongepowered/asm/mixin/injection/Redirect;"),
                AnnotationElement.of("method", AnnotationValue.ofArray(AnnotationValue.ofString("setItem(I)V"))),
                AnnotationElement.of(
                        "at",
                        AnnotationValue.ofAnnotation(Annotation.of(
                                ClassDesc.ofDescriptor("Lorg/spongepowered/asm/mixin/injection/At;"),
                                AnnotationElement.ofString("value", "INVOKE"),
                                AnnotationElement.ofString("target", "Lt/Target;old()V")))));
        byte[] mixin = mixin("m.OverloadMixin", List.of(new Handler("limit", redirect)));
        Path jar = jar(
                Map.of(CONFIG, "{\"package\":\"m\",\"mixins\":[\"OverloadMixin\"]}", "m/OverloadMixin.class", mixin));
        MethodTypeDesc one = MethodTypeDesc.of(CD_void, CD_int);
        MethodTypeDesc two = MethodTypeDesc.of(CD_void, CD_int, CD_boolean);
        byte[] vanilla = ClassFile.of()
                .build(
                        TARGET,
                        builder -> builder.withMethodBody(
                                "setItem",
                                one,
                                0,
                                code -> code.invokestatic(TARGET, "old", MethodTypeDesc.of(CD_void))
                                        .return_()));
        byte[] patched = ClassFile.of()
                .build(
                        TARGET,
                        builder -> builder.withMethodBody(
                                        "setItem",
                                        one,
                                        0,
                                        code -> code.aload(0)
                                                .iload(1)
                                                .iconst_1()
                                                .invokevirtual(TARGET, "setItem", two)
                                                .return_())
                                .withMethodBody(
                                        "setItem",
                                        two,
                                        0,
                                        code -> code.invokestatic(TARGET, "old", MethodTypeDesc.of(CD_void))
                                                .return_()));

        HostPatchAdjustments adjustments = HostPatchAdjustments.plan(
                new HostedLaunch(
                        "Host 1",
                        List.of(),
                        List.of(new InjectionPointReplacement(
                                "t/Target",
                                "setItem",
                                "Lt/Target;old()V",
                                "setItem(IZ)V",
                                InjectionPointReplacement.Placement.IN_METHOD)),
                        directory),
                GameSide.DEDICATED_SERVER,
                Map.of(CONFIG, "example"),
                Map.of("example", jar),
                vanillaOf("t/Target", vanilla),
                classes(patched));

        assertEquals(1, adjustments.findings().size(), adjustments.findings().toString());
        assertFalse(
                adjustments.findings().getFirst().incompatible(),
                adjustments.findings().toString());

        ClassModel rewritten = ClassFile.of().parse(adjustments.apply("m.OverloadMixin", mixin));
        MethodModel handler = rewritten.methods().stream()
                .filter(method -> method.methodName().equalsString("limit"))
                .findFirst()
                .orElseThrow();
        assertTrue(annotationsOf(handler).contains("setItem(IZ)V"), annotationsOf(handler));
    }

    @Test
    void aCallMovedToAnotherMethodWithReplacedTargetRetargetsBothMethodAndPoint() throws Exception {
        Annotation modifyArg = Annotation.of(
                ClassDesc.ofDescriptor("Lorg/spongepowered/asm/mixin/injection/ModifyArg;"),
                AnnotationElement.of("method", AnnotationValue.ofArray(AnnotationValue.ofString("lambda$run$5"))),
                AnnotationElement.of(
                        "at",
                        AnnotationValue.ofAnnotation(Annotation.of(
                                ClassDesc.ofDescriptor("Lorg/spongepowered/asm/mixin/injection/At;"),
                                AnnotationElement.ofString("value", "INVOKE"),
                                AnnotationElement.ofString("target", "Lt/Target;init(Ljava/util/List;Z)V")))));
        byte[] mixin = mixin("m.TagsMixin", List.of(new Handler("addReplaced", modifyArg)));
        Path jar = jar(Map.of(CONFIG, "{\"package\":\"m\",\"mixins\":[\"TagsMixin\"]}", "m/TagsMixin.class", mixin));
        MethodTypeDesc two = MethodTypeDesc.of(CD_void, ClassDesc.of("java.util.List"), CD_boolean);
        MethodTypeDesc three =
                MethodTypeDesc.of(CD_void, ClassDesc.of("java.util.List"), CD_boolean, ClassDesc.of("java.util.List"));
        byte[] vanilla = ClassFile.of()
                .build(
                        TARGET,
                        builder -> builder.withMethodBody(
                                "lambda$run$5",
                                MethodTypeDesc.of(CD_void),
                                0,
                                code -> code.aconst_null()
                                        .iconst_0()
                                        .invokestatic(TARGET, "init", two)
                                        .return_()));
        byte[] patched = ClassFile.of()
                .build(
                        TARGET,
                        builder -> builder.withMethodBody(
                                        "lambda$run$5",
                                        MethodTypeDesc.of(CD_void),
                                        0,
                                        java.lang.classfile.CodeBuilder::return_)
                                .withMethodBody(
                                        "lambda$run$6",
                                        MethodTypeDesc.of(CD_void),
                                        0,
                                        code -> code.aconst_null()
                                                .iconst_0()
                                                .aconst_null()
                                                .invokestatic(TARGET, "init", three)
                                                .return_()));

        HostPatchAdjustments adjustments = HostPatchAdjustments.plan(
                new HostedLaunch(
                        "Host 1",
                        List.of(),
                        List.of(new InjectionPointReplacement(
                                "t/Target",
                                "lambda$run$5",
                                "Lt/Target;init(Ljava/util/List;Z)V",
                                "lambda$run$6:Lt/Target;init(Ljava/util/List;ZLjava/util/List;)V",
                                InjectionPointReplacement.Placement.IN_METHOD)),
                        directory),
                GameSide.DEDICATED_SERVER,
                Map.of(CONFIG, "example"),
                Map.of("example", jar),
                vanillaOf("t/Target", vanilla),
                classes(patched));

        assertEquals(1, adjustments.findings().size(), adjustments.findings().toString());
        assertFalse(
                adjustments.findings().getFirst().incompatible(),
                adjustments.findings().toString());

        ClassModel rewritten = ClassFile.of().parse(adjustments.apply("m.TagsMixin", mixin));
        MethodModel handler = rewritten.methods().stream()
                .filter(method -> method.methodName().equalsString("addReplaced"))
                .findFirst()
                .orElseThrow();
        String annotations = annotationsOf(handler);
        assertTrue(annotations.contains("lambda$run$6"), annotations);
        assertTrue(annotations.contains("Lt/Target;init(Ljava/util/List;ZLjava/util/List;)V"), annotations);
    }

    @Test
    void aUnaryRedirectWhoseCallTheHostReplacedWithAVoidCaptureBridgesThroughModifyArg() throws Exception {
        ClassDesc mapDesc = ClassDesc.of("java.util.Map");
        MethodTypeDesc copyDesc = MethodTypeDesc.of(mapDesc, mapDesc);
        MethodTypeDesc captureDesc = MethodTypeDesc.of(CD_void, mapDesc, mapDesc);
        Annotation redirect = Annotation.of(
                ClassDesc.ofDescriptor("Lorg/spongepowered/asm/mixin/injection/Redirect;"),
                AnnotationElement.of("method", AnnotationValue.ofArray(AnnotationValue.ofString("loadDefault"))),
                AnnotationElement.of(
                        "at",
                        AnnotationValue.ofAnnotation(Annotation.of(
                                ClassDesc.ofDescriptor("Lorg/spongepowered/asm/mixin/injection/At;"),
                                AnnotationElement.ofString("value", "INVOKE"),
                                AnnotationElement.ofString(
                                        "target", "Ljava/util/Map;copyOf(Ljava/util/Map;)Ljava/util/Map;")))));
        byte[] mixin = ClassFile.of().build(ClassDesc.of("m.LanguageRedirectMixin"), builder -> {
            builder.withFlags(ClassFile.ACC_PUBLIC);
            builder.with(RuntimeInvisibleAnnotationsAttribute.of(mixinAnnotation()));
            builder.withMethod("create", copyDesc, ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC, method -> {
                method.with(RuntimeVisibleAnnotationsAttribute.of(redirect));
                method.withCode(code -> code.aload(0)
                        .ldc("mod.key")
                        .ldc("Mod Value")
                        .invokeinterface(mapDesc, "put", MethodTypeDesc.of(CD_Object, CD_Object, CD_Object))
                        .pop()
                        .aload(0)
                        .invokestatic(mapDesc, "copyOf", copyDesc, true)
                        .areturn());
            });
        });
        Path jar = jar(Map.of(
                CONFIG,
                "{\"package\":\"m\",\"mixins\":[\"LanguageRedirectMixin\"]}",
                "m/LanguageRedirectMixin.class",
                mixin));
        byte[] vanilla = ClassFile.of()
                .build(
                        TARGET,
                        builder -> builder.withMethodBody(
                                "loadDefault",
                                MethodTypeDesc.of(CD_void),
                                ClassFile.ACC_STATIC,
                                code -> code.aconst_null()
                                        .invokestatic(mapDesc, "copyOf", copyDesc, true)
                                        .pop()
                                        .return_()));
        byte[] patched = ClassFile.of()
                .build(
                        TARGET,
                        builder -> builder.withMethodBody(
                                "loadDefault",
                                MethodTypeDesc.of(CD_void),
                                ClassFile.ACC_STATIC,
                                code -> code.aconst_null()
                                        .aconst_null()
                                        .invokestatic(TARGET, "capture", captureDesc)
                                        .return_()));

        HostPatchAdjustments adjustments = HostPatchAdjustments.plan(
                new HostedLaunch(
                        "Host 1",
                        List.of(),
                        List.of(new InjectionPointReplacement(
                                "t/Target",
                                "loadDefault",
                                "Ljava/util/Map;copyOf(Ljava/util/Map;)Ljava/util/Map;",
                                "Lt/Target;capture(Ljava/util/Map;Ljava/util/Map;)V",
                                InjectionPointReplacement.Placement.SAME_PLACE)),
                        directory),
                GameSide.DEDICATED_SERVER,
                Map.of(CONFIG, "example"),
                Map.of("example", jar),
                vanillaOf("t/Target", vanilla),
                classes(patched));

        assertEquals(1, adjustments.findings().size(), adjustments.findings().toString());
        assertFalse(
                adjustments.findings().getFirst().incompatible(),
                adjustments.findings().toString());

        byte[] transformed = adjustments.apply("m.LanguageRedirectMixin", mixin);
        ClassModel rewritten = ClassFile.of().parse(transformed);
        MethodModel original = rewritten.methods().stream()
                .filter(method -> method.methodName().equalsString("create"))
                .findFirst()
                .orElseThrow();
        assertEquals("[]", annotationsOf(original));
        MethodModel bridge = rewritten.methods().stream()
                .filter(method -> method.methodName().equalsString("poly$hostRedirect$create"))
                .findFirst()
                .orElseThrow();
        assertTrue(annotationsOf(bridge).contains("ModifyArg"), annotationsOf(bridge));
        assertTrue(
                annotationsOf(bridge).contains("Lt/Target;capture(Ljava/util/Map;Ljava/util/Map;)V"),
                annotationsOf(bridge));

        Class<?> loaded = new ClassLoader(getClass().getClassLoader()) {
            Class<?> define(byte[] bytes) {
                return defineClass("m.LanguageRedirectMixin", bytes, 0, bytes.length);
            }
        }.define(transformed);
        java.lang.reflect.Method bridgeMethod = loaded.getDeclaredMethod("poly$hostRedirect$create", Map.class);
        bridgeMethod.setAccessible(true);
        Map<String, String> mutable = new java.util.HashMap<>();
        Object returned = bridgeMethod.invoke(null, mutable);
        assertTrue(mutable == returned);
        assertEquals("Mod Value", mutable.get("mod.key"));
    }

    /**
     * {@code NEW} with a constructor descriptor matches only that constructor: when the patch calls another one, the
     * injection finds nothing (Fabric API's {@code EntityMixin.modifyBlockStateParticleOption} next to NeoForge).
     */
    @Test
    void anInjectionAtANewOfAConstructorThePatchNoLongerCallsMakesTheModIncompatible() throws Exception {
        byte[] vanilla = snippetTarget(MethodTypeDesc.of(CD_void, CD_String));
        byte[] patched = snippetTarget(MethodTypeDesc.of(CD_void, CD_String, CD_int));
        Annotation modify = Annotation.of(
                ClassDesc.ofDescriptor("Lcom/llamalad7/mixinextras/injector/ModifyExpressionValue;"),
                AnnotationElement.of("method", AnnotationValue.ofArray(AnnotationValue.ofString("buildSnippet"))),
                AnnotationElement.of(
                        "at",
                        AnnotationValue.ofArray(AnnotationValue.ofAnnotation(Annotation.of(
                                ClassDesc.ofDescriptor("Lorg/spongepowered/asm/mixin/injection/At;"),
                                AnnotationElement.ofString("value", "NEW"),
                                AnnotationElement.ofString("target", "(Ljava/lang/String;)Lt/Snippet;"))))));
        byte[] mixin = mixin("m.NewMixin", List.of(new Handler("modifySnippet", modify)));
        Path jar = jar(Map.of(CONFIG, "{\"package\":\"m\",\"mixins\":[\"NewMixin\"]}", "m/NewMixin.class", mixin));

        HostPatchAdjustments adjustments = plan(
                GameSide.DEDICATED_SERVER,
                Map.of(CONFIG, "example"),
                Map.of("example", jar),
                vanillaOf("t/Target", vanilla),
                classes(patched));

        assertEquals(1, adjustments.findings().size(), adjustments.findings().toString());
        assertTrue(
                adjustments.findings().getFirst().incompatible(),
                adjustments.findings().toString());
        // The same constructor in both: nothing to report.
        assertTrue(plan(
                        GameSide.DEDICATED_SERVER,
                        Map.of(CONFIG, "example"),
                        Map.of("example", jar),
                        vanillaOf("t/Target", vanilla),
                        classes(vanilla))
                .findings()
                .isEmpty());
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

        HostPatchAdjustments adjustments = plan(
                GameSide.CLIENT,
                Map.of(CONFIG, "example"),
                Map.of("example", jar),
                name -> Optional.ofNullable(vanilla.get(name)),
                classes(Map.of("t/Common", common(), "t/Game", patchedGame)));

        assertEquals(1, adjustments.findings().size(), adjustments.findings().toString());
        HostPatchAdjustments.Finding finding = adjustments.findings().getFirst();
        assertEquals("POLY-COMPAT-222", finding.code());
        assertFalse(finding.incompatible());
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
        HostPatchAdjustments unchanged = plan(
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
        return mixinAnnotation(TARGET);
    }

    private static Annotation mixinAnnotation(ClassDesc target) {
        return Annotation.of(
                ClassDesc.ofDescriptor("Lorg/spongepowered/asm/mixin/Mixin;"),
                AnnotationElement.of("value", AnnotationValue.ofArray(AnnotationValue.ofClass(target))));
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
