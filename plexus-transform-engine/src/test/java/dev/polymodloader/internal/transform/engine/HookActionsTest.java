package dev.polymodloader.internal.transform.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.polymodloader.api.runtime.GameSide;
import dev.polymodloader.internal.transform.HeadHookResult;
import dev.polymodloader.internal.transform.InvokeHookAtHead;
import dev.polymodloader.internal.transform.InvokeHookBeforeReturn;
import dev.polymodloader.internal.transform.MethodTarget;
import dev.polymodloader.internal.transform.MethodTransformAction;
import dev.polymodloader.internal.transform.TransformFailurePolicy;
import dev.polymodloader.internal.transform.TransformProblem;
import dev.polymodloader.internal.transform.TransformProblemCode;
import dev.polymodloader.internal.transform.TransformRuleDefinition;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Applies the hook actions to a javac-compiled class and runs the result, so the JVM verifier
 * checks the stack map frames the actions add.
 */
class HookActionsTest {

    private static final String FIXTURE = "dev/polymodloader/internal/transform/engine/fixture/";
    private static final String TARGET = FIXTURE + "HookTarget";
    private static final String HOOKS = FIXTURE + "HookTargetHooks";
    private static final String DESCRIBE = "(Ljava/lang/String;J)Ljava/lang/String;";
    private static final AtomicInteger RULE_NUMBER = new AtomicInteger();

    @Test
    void returnIfNonNullShortCircuitsOnlyWhenTheHookAnswers() throws Exception {
        Class<?> type = transformed(
                rule("describe", DESCRIBE, new InvokeHookAtHead(HOOKS, "describe", HeadHookResult.RETURN_IF_NON_NULL)));
        Object target = type.getConstructor().newInstance();
        var describe = type.getMethod("describe", String.class, long.class);

        assertEquals("intercepted:3", describe.invoke(target, "intercept", 3L));
        assertEquals("a..", describe.invoke(target, "a", 2L), "null from the hook runs the original body");
    }

    @Test
    @SuppressWarnings("unchecked")
    void returnIfNonNullUnboxesForPrimitiveTargets() throws Exception {
        Class<?> type = transformed(
                rule("count", "()I", new InvokeHookAtHead(HOOKS, "countOverride", HeadHookResult.RETURN_IF_NON_NULL)));
        Object target = type.getConstructor().newInstance();
        var count = type.getMethod("count");

        assertEquals(1, count.invoke(target), "null from the hook runs the original body");
        ((List<String>) type.getField("log").get(target)).add("override");
        assertEquals(42, count.invoke(target), "the boxed hook result is unboxed and returned");
    }

    @Test
    void cancelIfTrueSkipsTheBody() throws Exception {
        Class<?> type =
                transformed(rule("act", "(I)V", new InvokeHookAtHead(HOOKS, "act", HeadHookResult.CANCEL_IF_TRUE)));
        Object target = type.getConstructor().newInstance();
        var act = type.getMethod("act", int.class);

        act.invoke(target, 2);
        act.invoke(target, -1);

        assertEquals(
                List.of("constructed", "hook:2", "act", "act", "hook:-1"),
                type.getField("log").get(target));
    }

    @Test
    void staticTargetsGetNoReceiver() throws Exception {
        Class<?> type = transformed(
                rule("label", "(I)Ljava/lang/String;", new InvokeHookAtHead(HOOKS, "label", HeadHookResult.CONTINUE)));
        TestHooks.ENTERED.clear();

        assertEquals("positive", type.getMethod("label", int.class).invoke(null, 5));
        assertEquals(List.of("label:5"), TestHooks.ENTERED);
    }

    @Test
    void returnHooksRunAfterConstructorsAndReplaceReturnedValues() throws Exception {
        Class<?> constructed = transformed(rule("<init>", "()V", new InvokeHookBeforeReturn(HOOKS, "constructed")));
        assertEquals(
                List.of("constructed", "after-constructor"),
                constructed.getField("log").get(constructed.getConstructor().newInstance()));

        Class<?> labelled =
                transformed(rule("label", "(I)Ljava/lang/String;", new InvokeHookBeforeReturn(HOOKS, "labelReturned")));
        assertEquals("other!-2", labelled.getMethod("label", int.class).invoke(null, -2));

        Class<?> counted = transformed(rule("count", "()I", new InvokeHookBeforeReturn(HOOKS, "countReturned")));
        assertEquals(
                101, counted.getMethod("count").invoke(counted.getConstructor().newInstance()));
    }

    @Test
    void shapesTheHookCannotHandleAreRejectedBeforeLoading() {
        List<TransformRuleDefinition> rules = List.of(
                rule("describe", DESCRIBE, new InvokeHookAtHead(HOOKS, "x", HeadHookResult.CANCEL_IF_TRUE)),
                rule("<init>", "()V", new InvokeHookAtHead(HOOKS, "x", HeadHookResult.CONTINUE)),
                rule("act", "(I)V", new InvokeHookAtHead(HOOKS, "x", HeadHookResult.RETURN_IF_NON_NULL)),
                rule("label", "(I)Ljava/lang/String;", new InvokeHookBeforeReturn(HOOKS, "x")));
        TransformPlan plan = plan(rules);

        assertEquals(
                List.of(
                        TransformProblemCode.PRECONDITION_FAILED,
                        TransformProblemCode.PRECONDITION_FAILED,
                        TransformProblemCode.PRECONDITION_FAILED),
                plan.rejected().stream().map(TransformProblem::code).toList(),
                "only the label return hook is valid");
    }

    @Test
    void returnHooksOnMethodsThatReassignParametersAreRejected() {
        TransformPlan plan = plan(List.of(rule("describe", DESCRIBE, new InvokeHookBeforeReturn(HOOKS, "x"))));

        assertEquals(
                List.of(TransformProblemCode.PRECONDITION_FAILED),
                plan.rejected().stream().map(TransformProblem::code).toList(),
                "describe() decrements its weight parameter");
    }

    private static TransformRuleDefinition rule(String method, String descriptor, MethodTransformAction action) {
        return new TransformRuleDefinition(
                "test:rule_" + RULE_NUMBER.incrementAndGet(),
                "test",
                "26.2",
                EnumSet.allOf(GameSide.class),
                new MethodTarget(TARGET, method, descriptor),
                action,
                TransformFailurePolicy.ABORT_STARTUP);
    }

    private static TransformPlan plan(List<TransformRuleDefinition> rules) {
        return new ClassTransformPlanner().plan(rules, "26.2", GameSide.CLIENT, name -> Optional.of(bytes(name)));
    }

    private static Class<?> transformed(TransformRuleDefinition rule) throws ClassNotFoundException {
        TransformPlan plan = plan(List.of(rule));
        assertEquals(List.of(), plan.rejected());
        ClassTransformExecutor executor = new ClassTransformExecutor(plan, Optional.empty(), entry -> {});
        ClassLoader loader = new ClassLoader(HookActionsTest.class.getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                synchronized (getClassLoadingLock(name)) {
                    if (!name.startsWith(FIXTURE.replace('/', '.'))) {
                        return super.loadClass(name, resolve);
                    }
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null) {
                        String internalName = name.replace('.', '/');
                        byte[] classFile = executor.transform(internalName, bytes(internalName));
                        loaded = defineClass(name, classFile, 0, classFile.length);
                    }
                    return loaded;
                }
            }
        };
        return loader.loadClass(TARGET.replace('/', '.'));
    }

    private static byte[] bytes(String internalName) {
        try (InputStream input = HookActionsTest.class.getClassLoader().getResourceAsStream(internalName + ".class")) {
            if (input == null) {
                throw new IllegalStateException("missing test class " + internalName);
            }
            return input.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
