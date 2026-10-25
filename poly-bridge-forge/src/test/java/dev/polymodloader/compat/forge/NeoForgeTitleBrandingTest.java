package dev.polymodloader.compat.forge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.polymodloader.api.runtime.GameSide;
import dev.polymodloader.internal.transform.InvokeHookBeforeReturn;
import dev.polymodloader.internal.transform.MethodTarget;
import dev.polymodloader.internal.transform.TransformFailurePolicy;
import dev.polymodloader.internal.transform.TransformRuleDefinition;
import dev.polymodloader.internal.transform.engine.ClassTransformExecutor;
import dev.polymodloader.internal.transform.engine.ClassTransformPlanner;
import dev.polymodloader.internal.transform.engine.TransformPlan;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.CheckClassAdapter;

/** ADR-0026: the title screen's mod counts above NeoForge's branding. */
class NeoForgeTitleBrandingTest {

    private static final String FOR_EACH_LINE = "(ZZLjava/util/function/BiConsumer;)V";
    private static final List<String> NEOFORGE_TOP_TO_BOTTOM = List.of("Minecraft 26.2", "NeoForge 26.2.0.93, 50 mods");
    private static final List<String> POLY_TOP_TO_BOTTOM = List.of("Poly mods: 2", "NeoForge mods: 40");

    /**
     * The title screen asks for the list bottom to top ({@code reverse}) and draws index {@code i} above index {@code i -
     * 1}: Poly Mod Loader's lines come after NeoForge's, bottom one first, so they sit above NeoForge's.
     */
    @Test
    void reversedListHasPolyLinesAfterNeoForgesBottomOneFirst() {
        NeoForgeTitleBranding.install(() -> POLY_TOP_TO_BOTTOM, message -> {});

        List<String> lines = NeoForgeTitleBranding.withModCounts(true, true, NEOFORGE_TOP_TO_BOTTOM.reversed());

        assertEquals(
                List.of("NeoForge 26.2.0.93, 50 mods", "Minecraft 26.2", "NeoForge mods: 40", "Poly mods: 2"), lines);
    }

    /** A caller that reads the list top to bottom (FancyMenu's own branding element) gets Poly Mod Loader's lines first. */
    @Test
    void topToBottomListHasPolyLinesFirst() {
        NeoForgeTitleBranding.install(() -> POLY_TOP_TO_BOTTOM, message -> {});

        List<String> lines = NeoForgeTitleBranding.withModCounts(true, false, NEOFORGE_TOP_TO_BOTTOM);

        assertEquals(
                List.of("Poly mods: 2", "NeoForge mods: 40", "Minecraft 26.2", "NeoForge 26.2.0.93, 50 mods"), lines);
    }

    @Test
    void withoutLinesNeoForgesListIsReturnedAsItIs() {
        NeoForgeTitleBranding.install(List::of, message -> {});

        assertSame(NEOFORGE_TOP_TO_BOTTOM, NeoForgeTitleBranding.withModCounts(true, false, NEOFORGE_TOP_TO_BOTTOM));
    }

    /**
     * The rule changes the list every caller of {@code BrandingControl} reads, not the title screen's call: FancyMenu
     * turns that call off and draws NeoForge's branding lines itself (corpusNeoForgeClient, 2026-10-24).
     */
    @Test
    void ruleHooksTheBrandingListNotTheTitleScreensCall() throws NoSuchMethodException {
        TransformRuleDefinition rule = NeoForgeTitleBranding.rule("26.2");
        InvokeHookBeforeReturn hook = (InvokeHookBeforeReturn) rule.action();

        assertEquals("poly", rule.ownerModId());
        assertEquals(Set.of(GameSide.CLIENT), rule.sides());
        assertEquals(TransformFailurePolicy.DISABLE_RULE, rule.failurePolicy());
        assertEquals(
                new MethodTarget(
                        "net/neoforged/neoforge/internal/BrandingControl", "getBrandings", "(ZZ)Ljava/util/List;"),
                rule.target());
        Method method =
                NeoForgeTitleBranding.class.getMethod(hook.hookMethodName(), boolean.class, boolean.class, List.class);
        assertEquals(hook.hookOwnerInternalName(), Type.getInternalName(method.getDeclaringClass()));
        assertEquals("(ZZLjava/util/List;)Ljava/util/List;", Type.getMethodDescriptor(method));
        assertTrue(Modifier.isStatic(method.getModifiers()) && Modifier.isPublic(method.getModifiers()));
    }

    /**
     * Against NeoForge's universal jar ({@code -PneoForgeUniversal=<neoforge-<version>-universal.jar>}): the rule is
     * accepted, the transformed class verifies, and {@code getBrandings} returns through the hook; {@code forEachLine},
     * which the title screen and FancyMenu call, reads {@code getBrandings}.
     */
    @Test
    @EnabledIfSystemProperty(named = "poly.neoForgeUniversal", matches = ".+")
    void ruleAppliesToNeoForgesBrandingControl() throws IOException {
        try (JarFile universal = new JarFile(System.getProperty("poly.neoForgeUniversal"))) {
            TransformRuleDefinition rule = NeoForgeTitleBranding.rule("test");
            TransformPlan plan = new ClassTransformPlanner()
                    .plan(List.of(rule), "test", GameSide.CLIENT, name -> read(universal, name));
            assertEquals(1, plan.acceptedRuleCount(), plan.rejected().toString());

            byte[] original =
                    read(universal, NeoForgeTitleBranding.BRANDING_CONTROL).orElseThrow();
            byte[] transformed = new ClassTransformExecutor(plan, Optional.empty(), entry -> {})
                    .transform(NeoForgeTitleBranding.BRANDING_CONTROL, original);
            new ClassReader(transformed).accept(new CheckClassAdapter(new ClassWriter(0), false), 0);
            ClassNode node = parse(transformed);
            // Before each of its two returns (with and without the Minecraft line).
            String hook = Type.getInternalName(NeoForgeTitleBranding.class) + ".withModCounts";
            assertEquals(
                    List.of(hook, hook),
                    calls(method(node, "getBrandings", NeoForgeTitleBranding.GET_BRANDINGS), "withModCounts"));
            assertEquals(
                    List.of(NeoForgeTitleBranding.BRANDING_CONTROL + ".getBrandings"),
                    calls(method(node, "forEachLine", FOR_EACH_LINE), "getBrandings"));
        }
    }

    /**
     * Against the client jar NeoForge's installer patched ({@code
     * -PneoForgePatchedClient=<minecraft-client-patched-<version>.jar>}): the title screen draws NeoForge's branding
     * through {@code BrandingControl.forEachLine}, so it shows the hooked list.
     */
    @Test
    @EnabledIfSystemProperty(named = "poly.neoForgePatchedClient", matches = ".+")
    void neoForgesTitleScreenDrawsItsBrandingThroughBrandingControl() throws IOException {
        try (JarFile jar = new JarFile(
                Path.of(System.getProperty("poly.neoForgePatchedClient")).toFile())) {
            ClassNode titleScreen =
                    parse(read(jar, NeoForgeTitleBranding.TITLE_SCREEN).orElseThrow());
            MethodNode render = titleScreen.methods.stream()
                    .filter(candidate -> candidate.name.equals("extractRenderState"))
                    .findFirst()
                    .orElseThrow();
            assertEquals(
                    List.of(NeoForgeTitleBranding.BRANDING_CONTROL + ".forEachLine"), calls(render, "forEachLine"));
        }
    }

    private static MethodNode method(ClassNode node, String name, String descriptor) {
        return node.methods.stream()
                .filter(candidate -> candidate.name.equals(name) && candidate.desc.equals(descriptor))
                .findFirst()
                .orElseThrow();
    }

    private static List<String> calls(MethodNode method, String name) {
        List<String> calls = new ArrayList<>();
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call
                    && call.name.equals(name)
                    && call.getOpcode() == Opcodes.INVOKESTATIC) {
                calls.add(call.owner + "." + call.name);
            }
        }
        return calls;
    }

    private static Optional<byte[]> read(JarFile jar, String internalName) {
        ZipEntry entry = jar.getEntry(internalName + ".class");
        if (entry == null) {
            return Optional.empty();
        }
        try (InputStream in = jar.getInputStream(entry)) {
            return Optional.of(in.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static ClassNode parse(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }
}
