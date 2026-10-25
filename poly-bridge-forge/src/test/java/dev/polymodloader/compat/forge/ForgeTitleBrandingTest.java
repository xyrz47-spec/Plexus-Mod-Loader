package dev.polymodloader.compat.forge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.polymodloader.api.runtime.GameSide;
import dev.polymodloader.internal.transform.MethodTarget;
import dev.polymodloader.internal.transform.RedirectCall;
import dev.polymodloader.internal.transform.TransformFailurePolicy;
import dev.polymodloader.internal.transform.TransformProblem;
import dev.polymodloader.internal.transform.TransformProblemCode;
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
import net.minecraftforge.internal.BrandingControl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.CheckClassAdapter;

class ForgeTitleBrandingTest {

    private static final String FOR_EACH_LINE = "(ZZLjava/util/function/ObjIntConsumer;)V";

    private record Drawn(String text, int index) {}

    /** Forge's lines keep their indexes; Poly Mod Loader's follow, bottom one first, so they sit above Forge's. */
    @Test
    void polyLinesAreDrawnAboveForgesLines() {
        ForgeTitleBranding.install(() -> List.of("Poly mods: 2", "Fabric mods: 14", "Forge mods: 3"), message -> {});
        List<Drawn> drawn = new ArrayList<>();

        ForgeTitleBranding.forEachLine(true, true, (text, index) -> drawn.add(new Drawn(text, index)));

        assertEquals(
                List.of(
                        new Drawn(BrandingControl.LINES.get(0), 0),
                        new Drawn(BrandingControl.LINES.get(1), 1),
                        new Drawn("Forge mods: 3", 2),
                        new Drawn("Fabric mods: 14", 3),
                        new Drawn("Poly mods: 2", 4)),
                drawn);
    }

    @Test
    void withoutLinesOnlyForgesAreDrawn() {
        ForgeTitleBranding.install(List::of, message -> {});
        List<Drawn> drawn = new ArrayList<>();

        ForgeTitleBranding.forEachLine(true, true, (text, index) -> drawn.add(new Drawn(text, index)));

        assertEquals(
                List.of(new Drawn(BrandingControl.LINES.get(0), 0), new Drawn(BrandingControl.LINES.get(1), 1)), drawn);
    }

    @Test
    void ruleRedirectsForgesBrandingCallToThePublicStaticHook() throws NoSuchMethodException {
        TransformRuleDefinition rule = ForgeTitleBranding.rule("26.2");
        RedirectCall redirect = (RedirectCall) rule.action();

        assertEquals("poly", rule.ownerModId());
        assertEquals(Set.of(GameSide.CLIENT), rule.sides());
        assertEquals(TransformFailurePolicy.DISABLE_RULE, rule.failurePolicy());
        assertEquals(List.of(ForgeTitleBranding.VANILLA_RULE_ID), rule.after());
        assertEquals(
                new MethodTarget("net/minecraftforge/internal/BrandingControl", "forEachLine", FOR_EACH_LINE),
                redirect.invoked());
        Method hook = ForgeTitleBranding.class.getMethod(
                redirect.hookMethodName(), boolean.class, boolean.class, java.util.function.ObjIntConsumer.class);
        assertEquals(redirect.hookOwnerInternalName(), Type.getInternalName(hook.getDeclaringClass()));
        assertEquals(FOR_EACH_LINE, Type.getMethodDescriptor(hook));
        assertTrue(Modifier.isStatic(hook.getModifiers()) && Modifier.isPublic(hook.getModifiers()));
    }

    /**
     * Against Forge's patched client classes: the version adapter's rule on the same method (its call is not in Forge's
     * patch) is off, this rule applies, and the patched title screen then calls the hook instead of Forge. Runs with
     * {@code -PforgePatchedClient=<forge-<version>-client.jar>}, or {@code -PforgeClientInstaller=<installer jar>
     * -PforgeVanillaClient=<client-<mc>.jar>} to patch it here.
     */
    @Test
    @EnabledIfSystemProperty(named = "poly.forgeVanillaClient", matches = ".+")
    void ruleAppliesToForgesPatchedClientFromItsInstaller(@TempDir Path directory) throws IOException {
        Path patched = directory.resolve("forge-client.jar");
        ForgePatchSet patches;
        try (JarFile installer = new JarFile(System.getProperty("poly.forgeClientInstaller"));
                InputStream lzma = installer.getInputStream(installer.getEntry("data/client.lzma"))) {
            patches = ForgePatchSet.read(lzma);
        }
        patches.patch(
                Path.of(System.getProperty("poly.forgeVanillaClient")),
                patched,
                Optional.of(".forge_patched_minecraft"));
        assertRuleAppliesTo(patched);
    }

    @Test
    @EnabledIfSystemProperty(named = "poly.forgePatchedClient", matches = ".+")
    void ruleAppliesToForgesPatchedClient() throws IOException {
        assertRuleAppliesTo(Path.of(System.getProperty("poly.forgePatchedClient")));
    }

    /** Forge's universal jar has the method the hook calls ({@code -PforgeUniversal=<forge-<version>-universal.jar>}). */
    @Test
    @EnabledIfSystemProperty(named = "poly.forgeUniversal", matches = ".+")
    void forgesBrandingControlHasTheMethodTheHookCalls() throws IOException {
        try (JarFile universal = new JarFile(System.getProperty("poly.forgeUniversal"))) {
            ClassNode node =
                    parse(read(universal, ForgeTitleBranding.BRANDING_CONTROL).orElseThrow());
            assertTrue((node.access & Opcodes.ACC_PUBLIC) != 0, "BrandingControl is public");
            assertTrue(
                    node.methods.stream()
                            .anyMatch(method -> method.name.equals("forEachLine")
                                    && method.desc.equals(FOR_EACH_LINE)
                                    && (method.access & Opcodes.ACC_PUBLIC) != 0
                                    && (method.access & Opcodes.ACC_STATIC) != 0),
                    "public static forEachLine" + FOR_EACH_LINE);
        }
    }

    private static void assertRuleAppliesTo(Path patchedClient) throws IOException {
        String gameVersion = "test";
        TransformRuleDefinition forge = ForgeTitleBranding.rule(gameVersion);
        // The version adapter's rule, as Minecraft26_2Adapter declares it.
        TransformRuleDefinition vanilla = new TransformRuleDefinition(
                ForgeTitleBranding.VANILLA_RULE_ID,
                "poly",
                gameVersion,
                Set.of(GameSide.CLIENT),
                forge.target(),
                new RedirectCall(
                        new MethodTarget(
                                "net/minecraft/client/gui/GuiGraphicsExtractor",
                                "text",
                                "(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V"),
                        0,
                        "dev/polymodloader/game/layer/v26_2/client/TitleScreenModCounts",
                        "onVersionText"),
                TransformFailurePolicy.DISABLE_RULE);
        try (JarFile jar = new JarFile(patchedClient.toFile())) {
            TransformPlan plan = new ClassTransformPlanner()
                    .plan(List.of(vanilla, forge), gameVersion, GameSide.CLIENT, name -> read(jar, name));

            assertEquals(1, plan.acceptedRuleCount(), plan.rejected().toString());
            List<TransformProblem> rejected = plan.rejected();
            assertEquals(1, rejected.size());
            assertEquals(
                    ForgeTitleBranding.VANILLA_RULE_ID,
                    rejected.getFirst().rule().ruleId());
            assertEquals(
                    TransformProblemCode.PRECONDITION_FAILED,
                    rejected.getFirst().code());

            byte[] original = read(jar, ForgeTitleBranding.TITLE_SCREEN).orElseThrow();
            byte[] transformed = new ClassTransformExecutor(plan, Optional.empty(), entry -> {})
                    .transform(ForgeTitleBranding.TITLE_SCREEN, original);
            new ClassReader(transformed).accept(new CheckClassAdapter(new ClassWriter(0), false), 0);
            MethodNode method = parse(transformed).methods.stream()
                    .filter(candidate -> candidate.name.equals("extractRenderState"))
                    .findFirst()
                    .orElseThrow();
            List<String> calls = new ArrayList<>();
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call && call.name.equals("forEachLine")) {
                    calls.add(call.owner + "." + call.name + call.desc);
                }
            }
            assertEquals(
                    List.of(Type.getInternalName(ForgeTitleBranding.class) + ".forEachLine" + FOR_EACH_LINE), calls);
        }
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
