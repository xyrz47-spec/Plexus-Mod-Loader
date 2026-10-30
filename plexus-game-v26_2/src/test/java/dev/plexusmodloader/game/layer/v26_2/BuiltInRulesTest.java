package dev.plexusmodloader.game.layer.v26_2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.plexusmodloader.api.runtime.GameSide;
import dev.plexusmodloader.game.version.v26_2.Minecraft26_2Adapter;
import dev.plexusmodloader.internal.transform.RedirectCall;
import dev.plexusmodloader.internal.transform.TransformFailurePolicy;
import dev.plexusmodloader.internal.transform.TransformProblem;
import dev.plexusmodloader.internal.transform.TransformRuleDefinition;
import dev.plexusmodloader.internal.transform.engine.ClassBytesSource;
import dev.plexusmodloader.internal.transform.engine.ClassTransformExecutor;
import dev.plexusmodloader.internal.transform.engine.ClassTransformPlanner;
import dev.plexusmodloader.internal.transform.engine.TransformPlan;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.CheckClassAdapter;

/**
 * Plans and applies every built-in rule of {@link Minecraft26_2Adapter} to the real 26.2 classes on
 * the test class path, for both sides, and checks that each inserted call reaches a public static hook
 * of this game layer with exactly the descriptor the engine calls. Client-only rules are otherwise
 * only exercised by tasks that open a game window.
 */
class BuiltInRulesTest {

    /** Hooks live in the game layer and the version adapter. */
    private static final String HOOK_PACKAGE = "dev/plexusmodloader/game/";

    @Test
    void clientRulesApplyAndReachTheirHooks() {
        assertRulesApply(GameSide.CLIENT);
    }

    @Test
    void serverRulesApplyAndReachTheirHooks() {
        assertRulesApply(GameSide.DEDICATED_SERVER);
    }

    /**
     * The title screen rule redirects the first call to {@code GuiGraphicsExtractor.text(Font, String, int, int, int)}
     * in {@code TitleScreen.extractRenderState}: there is only that one, the version text, so the mod counts are drawn
     * above that line and no other. Cosmetic, so it never stops the launch.
     */
    @Test
    void titleScreenRuleRedirectsTheOnlyVersionTextCall() {
        TransformRuleDefinition rule = new Minecraft26_2Adapter()
                .builtInTransformRules().stream()
                        .filter(candidate -> candidate.ruleId().equals("poly:client_title_mod_counts"))
                        .findFirst()
                        .orElseThrow();
        assertEquals(TransformFailurePolicy.DISABLE_RULE, rule.failurePolicy());
        RedirectCall redirect = (RedirectCall) rule.action();
        ClassNode node = new ClassNode();
        new ClassReader(read(rule.target().ownerInternalName()).orElseThrow()).accept(node, 0);
        MethodNode method = node.methods.stream()
                .filter(candidate -> candidate.name.equals(rule.target().name())
                        && candidate.desc.equals(rule.target().descriptor()))
                .findFirst()
                .orElseThrow();
        long calls = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call
                    && call.owner.equals(redirect.invoked().ownerInternalName())
                    && call.name.equals(redirect.invoked().name())
                    && call.desc.equals(redirect.invoked().descriptor())) {
                calls++;
            }
        }
        assertEquals(1, calls);
        assertEquals(0, redirect.ordinal());
    }

    /** Hooks that reach protected or private game members resolve them when their class initialises. */
    @Test
    void hookAccessorsResolveAgainstTheGameClasses() throws ClassNotFoundException {
        ClassLoader loader = BuiltInRulesTest.class.getClassLoader();
        for (String name : List.of(
                "dev.plexusmodloader.game.layer.v26_2.InteractionHooks",
                "dev.plexusmodloader.game.layer.v26_2.client.CreativeTabPaging",
                "dev.plexusmodloader.game.layer.v26_2.gametest.GameTestContextView")) {
            assertEquals(name, Class.forName(name, true, loader).getName());
        }
    }

    /**
     * ADR-0026 6: the built-in rules NeoForge's patch makes inapplicable, planned against the game jar NeoForge's
     * installer patched ({@code -PneoForgePatchedClient=<minecraft-client-patched-<version>.jar>}, {@code
     * -PneoForgePatchedServer=<minecraft-server-patched-<version>.jar>}). Only these are off when NeoForge runs; each is
     * reported in the log (optional rules as information). NeoForge's own code does what most of them did: the client
     * and server brands, the title screen branding (which {@code NeoForgeTitleBranding} draws the counts over) and the
     * creative screen's tab pages (NeoForge pages creative tabs itself, as Forge does). Not so {@code
     * poly:payload_codecs}: NeoForge registers custom payloads its own way, so Poly Mods' network channels are not
     * encoded through Poly Mod Loader's hook when NeoForge runs (ADR-0026).
     */
    @Test
    @EnabledIfSystemProperty(named = "poly.neoForgePatchedClient", matches = ".+")
    void rulesNeoForgesPatchTurnsOffOnTheClient() throws IOException {
        assertEquals(
                Set.of(
                        "poly:client_brand",
                        "poly:client_creative_tabs_clicked",
                        "poly:client_creative_tabs_drawn",
                        "poly:client_creative_tabs_hovered",
                        "poly:client_creative_tabs_released",
                        "poly:client_title_mod_counts",
                        "poly:payload_codecs",
                        "poly:server_brand"),
                rejectedOn(Path.of(System.getProperty("poly.neoForgePatchedClient")), GameSide.CLIENT));
    }

    @Test
    @EnabledIfSystemProperty(named = "poly.neoForgePatchedServer", matches = ".+")
    void rulesNeoForgesPatchTurnsOffOnTheServer() throws IOException {
        assertEquals(
                Set.of("poly:payload_codecs", "poly:server_brand"),
                rejectedOn(Path.of(System.getProperty("poly.neoForgePatchedServer")), GameSide.DEDICATED_SERVER));
    }

    /** The IDs of the built-in rules for {@code side} that do not apply to {@code patchedJar}'s classes. */
    private static Set<String> rejectedOn(Path patchedJar, GameSide side) throws IOException {
        try (JarFile jar = new JarFile(patchedJar.toFile())) {
            ClassBytesSource classes = internalName -> {
                ZipEntry entry = jar.getEntry(internalName + ".class");
                if (entry == null) {
                    return read(internalName);
                }
                try (InputStream in = jar.getInputStream(entry)) {
                    return Optional.of(in.readAllBytes());
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            };
            TransformPlan plan = new ClassTransformPlanner()
                    .plan(new Minecraft26_2Adapter().builtInTransformRules(), "26.2", side, classes);
            Set<String> rejected = new TreeSet<>();
            plan.rejected().forEach(problem -> rejected.add(problem.rule().ruleId()));
            return rejected;
        }
    }

    private static void assertRulesApply(GameSide side) {
        List<TransformRuleDefinition> rules = new Minecraft26_2Adapter().builtInTransformRules();
        ClassBytesSource classes = BuiltInRulesTest::read;
        TransformPlan plan = new ClassTransformPlanner().plan(rules, "26.2", side, classes);

        assertEquals(
                List.of(),
                plan.rejected().stream().map(TransformProblem::toString).toList(),
                side + " rejected");
        long expected =
                rules.stream().filter(rule -> rule.sides().contains(side)).count();
        assertEquals(expected, plan.acceptedRuleCount(), side + " accepted every rule for its side");

        ClassTransformExecutor executor = new ClassTransformExecutor(plan, Optional.empty(), entry -> {});
        List<String> missingHooks = new ArrayList<>();
        int hookCalls = 0;
        for (Map.Entry<String, List<TransformRuleDefinition>> entry :
                plan.rulesByClass().entrySet()) {
            byte[] original = read(entry.getKey()).orElseThrow();
            byte[] transformed = executor.transform(entry.getKey(), original);
            assertTrue(transformed != original, entry.getKey() + " was transformed");
            // Structural check of the result (no data-flow analysis, which would load game classes).
            new ClassReader(transformed).accept(new CheckClassAdapter(new ClassWriter(0), false), 0);
            ClassNode node = new ClassNode();
            new ClassReader(transformed).accept(node, 0);
            for (MethodNode method : node.methods) {
                for (AbstractInsnNode instruction : method.instructions) {
                    if (instruction instanceof MethodInsnNode call && call.owner.startsWith(HOOK_PACKAGE)) {
                        hookCalls++;
                        if (!hookExists(call)) {
                            missingHooks.add(entry.getKey() + "." + method.name + " calls " + call.owner + "."
                                    + call.name + call.desc);
                        }
                    }
                }
            }
        }
        assertEquals(List.of(), missingHooks, "every inserted call has a public static hook");
        assertTrue(hookCalls > 0, "the game layer's hooks were found in the transformed classes");
    }

    private static boolean hookExists(MethodInsnNode call) {
        Class<?> owner;
        try {
            owner = Class.forName(call.owner.replace('/', '.'), false, BuiltInRulesTest.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            return false;
        }
        return Arrays.stream(owner.getDeclaredMethods()).anyMatch(method -> isHook(method, call));
    }

    private static boolean isHook(Method method, MethodInsnNode call) {
        return method.getName().equals(call.name)
                && Type.getMethodDescriptor(method).equals(call.desc)
                && Modifier.isStatic(method.getModifiers())
                && Modifier.isPublic(method.getModifiers());
    }

    private static Optional<byte[]> read(String internalName) {
        try (InputStream in = BuiltInRulesTest.class.getClassLoader().getResourceAsStream(internalName + ".class")) {
            return in == null ? Optional.empty() : Optional.of(in.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
