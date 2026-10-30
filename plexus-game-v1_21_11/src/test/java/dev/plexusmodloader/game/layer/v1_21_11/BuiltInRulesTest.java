package dev.plexusmodloader.game.layer.v1_21_11;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.plexusmodloader.api.runtime.GameSide;
import dev.plexusmodloader.game.version.v1_21_11.Minecraft1_21_11Adapter;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.CheckClassAdapter;

/**
 * Plans and applies every built-in rule of {@link Minecraft1_21_11Adapter} to the real 1.21.11 classes on
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
        TransformRuleDefinition rule = new Minecraft1_21_11Adapter()
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
                "dev.plexusmodloader.game.layer.v1_21_11.InteractionHooks",
                "dev.plexusmodloader.game.layer.v1_21_11.client.CreativeTabPaging",
                "dev.plexusmodloader.game.layer.v1_21_11.gametest.GameTestContextView")) {
            assertEquals(name, Class.forName(name, true, loader).getName());
        }
    }

    private static void assertRulesApply(GameSide side) {
        List<TransformRuleDefinition> rules = new Minecraft1_21_11Adapter().builtInTransformRules();
        ClassBytesSource classes = BuiltInRulesTest::read;
        TransformPlan plan = new ClassTransformPlanner().plan(rules, "1.21.11", side, classes);

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
