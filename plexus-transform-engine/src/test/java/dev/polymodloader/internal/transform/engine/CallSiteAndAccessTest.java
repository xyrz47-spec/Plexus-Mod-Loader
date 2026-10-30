package dev.polymodloader.internal.transform.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.polymodloader.api.runtime.GameSide;
import dev.polymodloader.internal.transform.AccessWidening;
import dev.polymodloader.internal.transform.AccessWidening.Access;
import dev.polymodloader.internal.transform.AccessWidening.Kind;
import dev.polymodloader.internal.transform.AccessWideningProblem;
import dev.polymodloader.internal.transform.HeadHookResult;
import dev.polymodloader.internal.transform.InvokeHookAtCall;
import dev.polymodloader.internal.transform.InvokeHookAtHead;
import dev.polymodloader.internal.transform.InvokeHookBeforeReturn;
import dev.polymodloader.internal.transform.MethodTarget;
import dev.polymodloader.internal.transform.MethodTransformAction;
import dev.polymodloader.internal.transform.RedirectCall;
import dev.polymodloader.internal.transform.ReplaceReturnedStringConstant;
import dev.polymodloader.internal.transform.TransformFailurePolicy;
import dev.polymodloader.internal.transform.TransformProblem;
import dev.polymodloader.internal.transform.TransformProblemCode;
import dev.polymodloader.internal.transform.TransformRuleDefinition;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Call-site redirects, rules combined on one method in declared order, and access widening, applied
 * to a javac-compiled class that is then loaded and run, so the JVM verifier checks every result.
 */
class CallSiteAndAccessTest {

    private static final String FIXTURE = "dev/polymodloader/internal/transform/engine/fixture/";
    private static final String TARGET = FIXTURE + "CallSiteTarget";
    private static final String HOOKS = FIXTURE + "CallSiteTargetHooks";
    private static final String VISIT = "(Ljava/lang/String;)Ljava/lang/String;";
    private static final MethodTarget ABS = new MethodTarget("java/lang/Math", "abs", "(I)I");
    private static final MethodTarget TRIM = new MethodTarget("java/lang/String", "trim", "()Ljava/lang/String;");
    private static final MethodTarget APPEND =
            new MethodTarget("java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;");
    private static final MethodTarget CLEAR = new MethodTarget("java/util/List", "clear", "()V");
    private static final MethodTarget UPPER =
            new MethodTarget("java/lang/String", "toUpperCase", "()Ljava/lang/String;");

    @Test
    void redirectReplacesEveryOrOneStaticCall() throws Exception {
        Class<?> all = load(
                plan(rule("test:abs_all", "twice", "(I)I", new RedirectCall(ABS, RedirectCall.ALL, HOOKS, "abs"))));
        assertEquals(1003 + 1030, all.getMethod("twice", int.class).invoke(null, -3));

        Class<?> second = load(plan(rule("test:abs_second", "twice", "(I)I", new RedirectCall(ABS, 1, HOOKS, "abs"))));
        assertEquals(3 + 1030, second.getMethod("twice", int.class).invoke(null, -3));
    }

    @Test
    void redirectPassesTheReceiverOfAVirtualCall() throws Exception {
        Class<?> type =
                load(plan(rule("test:trim", "greet", VISIT, new RedirectCall(TRIM, RedirectCall.ALL, HOOKS, "trim"))));
        Object target = type.getConstructor().newInstance();

        assertEquals("hi  x!", type.getMethod("greet", String.class).invoke(target, " x "));
    }

    @Test
    void redirectWithoutAMatchingCallIsRejected() {
        TransformPlan plan = plan(
                rule("test:none", "greet", VISIT, new RedirectCall(ABS, RedirectCall.ALL, HOOKS, "abs")),
                rule("test:too_far", "twice", "(I)I", new RedirectCall(ABS, 2, HOOKS, "abs")));

        assertEquals(
                List.of(TransformProblemCode.PRECONDITION_FAILED, TransformProblemCode.PRECONDITION_FAILED),
                plan.rejected().stream().map(TransformProblem::code).toList());
        assertTrue(plan.rejected().get(0).message().contains("never calls java/lang/Math.abs(I)I"));
        assertTrue(plan.rejected().get(1).message().contains("only 2 such call(s)"));
    }

    @Test
    void orderedRulesOnOneMethodRunInDeclaredOrder() throws Exception {
        // Listed out of order on purpose: "after" decides.
        TransformPlan plan = plan(
                rule(
                        "test:return_second",
                        VISIT,
                        new InvokeHookBeforeReturn(HOOKS, "visitReturnSecond"),
                        "test:return_first"),
                rule("test:head_second", VISIT, head("visitHeadSecond"), "test:head_first"),
                rule(
                        "test:shout",
                        VISIT,
                        new RedirectCall(UPPER, RedirectCall.ALL, HOOKS, "shout"),
                        "test:head_second"),
                rule("test:return_first", VISIT, new InvokeHookBeforeReturn(HOOKS, "visitReturnFirst"), "test:shout"),
                rule("test:head_first", VISIT, head("visitHeadFirst")));
        assertEquals(List.of(), plan.rejected());
        assertEquals(
                List.of("test:head_first", "test:head_second", "test:shout", "test:return_first", "test:return_second"),
                plan.rulesByClass().get(TARGET).stream()
                        .map(TransformRuleDefinition::ruleId)
                        .toList());

        Class<?> type = load(plan);
        Object target = type.getConstructor().newInstance();

        assertEquals("X?12", type.getMethod("visit", String.class).invoke(target, "x"));
        assertEquals(
                List.of("head1", "head2", "body:x", "return1", "return2"),
                type.getField("log").get(target));
    }

    @Test
    void rulesOnOneMethodWithoutOneOrderAreRejectedTogether() {
        TransformPlan unordered =
                plan(rule("test:a", VISIT, head("visitHeadFirst")), rule("test:b", VISIT, head("visitHeadSecond")));
        TransformPlan cycle = plan(
                rule("test:a", VISIT, head("visitHeadFirst"), "test:b"),
                rule("test:b", VISIT, head("visitHeadSecond"), "test:a"));

        for (TransformPlan plan : List.of(unordered, cycle)) {
            assertEquals(
                    List.of(TransformProblemCode.CONFLICTING_RULES, TransformProblemCode.CONFLICTING_RULES),
                    plan.rejected().stream().map(TransformProblem::code).toList());
            assertTrue(plan.rejected().getFirst().message().contains("declare an order"));
            assertEquals(0, plan.acceptedRuleCount());
        }
    }

    @Test
    void constantReplacementsAndRepeatedRedirectsCannotBeCombined() {
        TransformPlan constant = plan(
                rule("test:a", VISIT, head("visitHeadFirst")),
                rule("test:b", VISIT, new ReplaceReturnedStringConstant("x", "y"), "test:a"));
        TransformPlan twice = plan(
                rule("test:a", VISIT, new RedirectCall(UPPER, RedirectCall.ALL, HOOKS, "shout")),
                rule("test:b", VISIT, new RedirectCall(UPPER, RedirectCall.ALL, HOOKS, "shout"), "test:a"));

        assertTrue(constant.rejected().getFirst().message().contains("cannot be combined"));
        assertTrue(twice.rejected().getFirst().message().contains("both redirect calls"));
    }

    @Test
    void wideningMakesPrivateMembersPublicAndFinalFieldsWritable() throws Exception {
        TransformPlan plan = new ClassTransformPlanner()
                .plan(
                        List.of(),
                        List.of(
                                widening(TARGET, Kind.METHOD, "secret", "()I", Access.ACCESSIBLE),
                                widening(TARGET, Kind.FIELD, "locked", "I", Access.ACCESSIBLE),
                                widening(TARGET, Kind.FIELD, "locked", "I", Access.MUTABLE)),
                        "26.2",
                        GameSide.CLIENT,
                        CallSiteAndAccessTest::bytesOf);
        assertEquals(List.of(), plan.rejectedWidenings());

        Class<?> type = load(plan);
        Object target = type.getConstructor().newInstance();
        Method secret = type.getMethod("secret");
        assertTrue(Modifier.isPublic(secret.getModifiers()));
        assertTrue(Modifier.isFinal(secret.getModifiers()), "a formerly private method stays unoverridable");
        assertEquals(7, secret.invoke(target));
        Field locked = type.getField("locked");
        assertFalse(Modifier.isFinal(locked.getModifiers()));
        locked.setInt(target, 5);
        assertEquals(5, type.getMethod("locked").invoke(target));
    }

    @Test
    void extendableClassesAndMethodsLoseFinalAndCallsBecomeVirtual() throws Exception {
        String nested = TARGET + "$Sealed";
        TransformPlan plan = new ClassTransformPlanner()
                .plan(
                        List.of(),
                        List.of(
                                widening(nested, Kind.CLASS, "", "", Access.EXTENDABLE),
                                widening(TARGET, Kind.METHOD, "secret", "()I", Access.EXTENDABLE)),
                        "26.2",
                        GameSide.CLIENT,
                        CallSiteAndAccessTest::bytesOf);
        ClassTransformExecutor executor = new ClassTransformExecutor(plan, Optional.empty(), entry -> {});

        Class<?> sealed = load(plan, nested);
        assertFalse(Modifier.isFinal(sealed.getModifiers()));
        assertTrue(Modifier.isPublic(sealed.getModifiers()));

        ClassNode target = parse(executor.transform(TARGET, bytes(TARGET)));
        MethodNode secret = target.methods.stream()
                .filter(method -> method.name.equals("secret"))
                .findFirst()
                .orElseThrow();
        assertEquals(
                Opcodes.ACC_PROTECTED,
                secret.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED | Opcodes.ACC_PRIVATE));
        assertEquals(0, secret.access & Opcodes.ACC_FINAL);
        for (MethodNode method : target.methods) {
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call && call.name.equals("secret")) {
                    assertEquals(Opcodes.INVOKEVIRTUAL, call.getOpcode(), "calls to an extendable method are virtual");
                }
            }
        }
        Object instance = load(plan).getConstructor().newInstance();
        assertEquals(7, instance.getClass().getMethod("callSecret").invoke(instance));
    }

    @Test
    void wideningOfAMissingMemberIsRejectedWithWhatExists() {
        TransformPlan plan = new ClassTransformPlanner()
                .plan(
                        List.of(),
                        List.of(
                                widening(TARGET, Kind.METHOD, "secret", "()J", Access.ACCESSIBLE),
                                widening(FIXTURE + "Missing", Kind.CLASS, "", "", Access.ACCESSIBLE)),
                        "26.2",
                        GameSide.CLIENT,
                        name -> name.endsWith("Missing") ? Optional.empty() : Optional.of(bytes(name)));

        assertEquals(
                List.of(TransformProblemCode.ACCESS_TARGET_NOT_FOUND, TransformProblemCode.ACCESS_TARGET_NOT_FOUND),
                plan.rejectedWidenings().stream()
                        .map(AccessWideningProblem::code)
                        .toList());
        assertTrue(plan.rejectedWidenings().getFirst().message().contains("descriptors [()I]"));
        assertEquals(2, plan.abortingWideningProblems().size());
    }

    @Test
    void wideningsRejectAccessThatDoesNotFitTheKind() {
        assertThrows(
                IllegalArgumentException.class, () -> widening(TARGET, Kind.METHOD, "secret", "()I", Access.MUTABLE));
        assertThrows(
                IllegalArgumentException.class, () -> widening(TARGET, Kind.FIELD, "locked", "I", Access.EXTENDABLE));
        assertThrows(IllegalArgumentException.class, () -> widening(TARGET, Kind.CLASS, "x", "", Access.ACCESSIBLE));
        assertThrows(
                IllegalArgumentException.class,
                () -> widening(TARGET, Kind.METHOD, "<clinit>", "()V", Access.ACCESSIBLE));
    }

    /** As Fabric's access widener: subclasses may call the constructor, and calls to it stay invokespecial. */
    @Test
    void anExtendableConstructorBecomesProtectedAndKeepsItsCalls() throws Exception {
        TransformPlan plan = new ClassTransformPlanner()
                .plan(
                        List.of(),
                        List.of(widening(TARGET, Kind.METHOD, "<init>", "(I)V", Access.EXTENDABLE)),
                        "26.2",
                        GameSide.CLIENT,
                        CallSiteAndAccessTest::bytesOf);
        assertEquals(List.of(), plan.rejectedWidenings());
        ClassTransformExecutor executor = new ClassTransformExecutor(plan, Optional.empty(), entry -> {});

        ClassNode target = parse(executor.transform(TARGET, bytes(TARGET)));
        MethodNode constructor = target.methods.stream()
                .filter(method -> method.name.equals("<init>") && method.desc.equals("(I)V"))
                .findFirst()
                .orElseThrow();
        assertEquals(
                Opcodes.ACC_PROTECTED,
                constructor.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED | Opcodes.ACC_PRIVATE));
        for (MethodNode method : target.methods) {
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call && call.name.equals("<init>")) {
                    assertEquals(Opcodes.INVOKESPECIAL, call.getOpcode(), "constructor calls stay invokespecial");
                }
            }
        }
        Object instance = load(plan).getConstructor().newInstance();
        assertEquals(1, instance.getClass().getMethod("locked").invoke(instance));
    }

    @Test
    void aMutableInterfaceFieldStaysFinal() throws Exception {
        String limits = TARGET + "$Limits";
        TransformPlan plan = new ClassTransformPlanner()
                .plan(
                        List.of(),
                        List.of(widening(limits, Kind.FIELD, "NAMES", "Ljava/util/List;", Access.MUTABLE)),
                        "26.2",
                        GameSide.CLIENT,
                        CallSiteAndAccessTest::bytesOf);

        Field names = load(plan, limits).getField("NAMES");
        assertTrue(Modifier.isFinal(names.getModifiers()));
        assertEquals(List.of("limit"), names.get(null));
    }

    @Test
    void cacheKeyDependsOnWideningsButNotTheirOrder() {
        AccessWidening first = widening(TARGET, Kind.METHOD, "secret", "()I", Access.ACCESSIBLE);
        AccessWidening second = widening(TARGET, Kind.FIELD, "locked", "I", Access.MUTABLE);
        byte[] original = bytes(TARGET);

        assertEquals(
                TransformCacheKey.of(TARGET, original, List.of(), List.of(first, second), "f"),
                TransformCacheKey.of(TARGET, original, List.of(), List.of(second, first), "f"));
        assertFalse(TransformCacheKey.of(TARGET, original, List.of(), List.of(first), "f")
                .equals(TransformCacheKey.of(TARGET, original, List.of(), List.of(first, second), "f")));
    }

    /** Call observers keep the call (ADR-0020): the method's result is the original one, the hook sees the values. */
    @Test
    void observersRunBeforeOrAfterACallAndKeepIt() throws Exception {
        TransformPlan plan = plan(
                rule(
                        "test:see_abs",
                        "twice",
                        "(I)I",
                        new InvokeHookAtCall(
                                ABS, InvokeHookAtCall.ALL, InvokeHookAtCall.Timing.BEFORE, HOOKS, "seeAbs")),
                rule(
                        "test:see_append",
                        "greet",
                        VISIT,
                        new InvokeHookAtCall(
                                APPEND, InvokeHookAtCall.ALL, InvokeHookAtCall.Timing.BEFORE, HOOKS, "seeAppend")),
                rule(
                        "test:after_clear",
                        "tidy",
                        "()V",
                        new InvokeHookAtCall(CLEAR, 0, InvokeHookAtCall.Timing.AFTER, HOOKS, "afterClear")));
        assertEquals(List.of(), plan.rejected());
        Class<?> type = load(plan);
        @SuppressWarnings("unchecked")
        List<String> seen = (List<String>) type.getClassLoader()
                .loadClass(HOOKS.replace('/', '.'))
                .getField("SEEN")
                .get(null);
        Object target = type.getConstructor().newInstance();

        assertEquals(33, type.getMethod("twice", int.class).invoke(null, -3));
        assertEquals("hi  x", type.getMethod("greet", String.class).invoke(target, " x "));
        type.getMethod("tidy").invoke(target);

        assertEquals(
                List.of("abs -3", "abs -30", "append 'hi ' to ''", "append ' x ' to 'hi '", "cleared, size 0"), seen);
        assertEquals(List.of("c"), type.getField("log").get(target));
        ClassNode transformed =
                parse(new ClassTransformExecutor(plan, Optional.empty(), entry -> {}).transform(TARGET, bytes(TARGET)));
        long absCalls = transformed.methods.stream()
                .filter(method -> method.name.equals("twice"))
                .flatMap(method -> java.util.stream.StreamSupport.stream(method.instructions.spliterator(), false))
                .filter(instruction -> instruction instanceof org.objectweb.asm.tree.MethodInsnNode call
                        && call.owner.equals("java/lang/Math")
                        && call.name.equals("abs"))
                .count();
        assertEquals(2, absCalls, "the observed calls stay in place");
    }

    /**
     * With another transformer in between (a bridge's Mixin), redirects come in a late pass; one whose call the other
     * transformer already took is left out with a warning instead of failing the class (ADR-0020).
     */
    @Test
    void redirectsComeLateAndYieldToAnotherTransformer() throws Exception {
        TransformPlan plan = plan(
                rule("test:abs_all", "twice", "(I)I", new RedirectCall(ABS, RedirectCall.ALL, HOOKS, "abs")),
                rule(
                        "test:see_append",
                        "greet",
                        VISIT,
                        new InvokeHookAtCall(APPEND, 0, InvokeHookAtCall.Timing.BEFORE, HOOKS, "seeAppend")));
        ClassTransformExecutor executor = new ClassTransformExecutor(plan, Optional.empty(), entry -> {});
        byte[] original = bytes(TARGET);
        List<String> skipped = new ArrayList<>();

        byte[] early = executor.transformEarly(TARGET, original);
        byte[] late = executor.transformLate(TARGET, early, skipped::add);

        assertEquals(List.of(), skipped);
        assertEquals(
                calls(executor.transform(TARGET, original)), calls(late), "two passes make the calls one pass makes");
        // Another transformer that already replaced Math.abs leaves the redirect nothing to do.
        byte[] taken = executor.transformLate(TARGET, executor.transform(TARGET, original), skipped::add);
        assertEquals(1, skipped.size());
        assertTrue(skipped.getFirst().startsWith("PLEXUS-TRANSFORM-011 rule test:abs_all"), skipped.getFirst());
        assertTrue(skipped.getFirst().contains("never calls java/lang/Math.abs(I)I"), skipped.getFirst());
        assertEquals(calls(executor.transform(TARGET, original)), calls(taken));
    }

    @Test
    void observersThatCannotApplyAreRejected() {
        TransformPlan plan = plan(
                rule(
                        "test:after_abs",
                        "twice",
                        "(I)I",
                        new InvokeHookAtCall(ABS, 0, InvokeHookAtCall.Timing.AFTER, HOOKS, "seeAbs")),
                rule("test:redirect_trim", "greet", VISIT, new RedirectCall(TRIM, RedirectCall.ALL, HOOKS, "trim")),
                rule(
                        "test:see_trim",
                        "greet",
                        VISIT,
                        new InvokeHookAtCall(
                                TRIM, InvokeHookAtCall.ALL, InvokeHookAtCall.Timing.BEFORE, HOOKS, "seeAppend"),
                        "test:redirect_trim"));

        List<String> messages =
                plan.rejected().stream().map(TransformProblem::message).toList();
        assertTrue(messages.stream().anyMatch(message -> message.contains("needs a void call")), messages.toString());
        assertTrue(
                messages.stream()
                        .anyMatch(message -> message.contains("redirects the calls to java/lang/String.trim()")),
                messages.toString());
    }

    /** Every call instruction of every method, as {@code method: owner.name}, in order. */
    private static List<String> calls(byte[] classFile) {
        List<String> calls = new ArrayList<>();
        for (org.objectweb.asm.tree.MethodNode method : parse(classFile).methods) {
            for (org.objectweb.asm.tree.AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof org.objectweb.asm.tree.MethodInsnNode call) {
                    calls.add(method.name + ": " + call.owner + "." + call.name);
                }
            }
        }
        return calls;
    }

    private static InvokeHookAtHead head(String hook) {
        return new InvokeHookAtHead(HOOKS, hook, HeadHookResult.CONTINUE);
    }

    private static TransformRuleDefinition rule(
            String id, String descriptor, MethodTransformAction action, String... after) {
        return rule(id, "visit", descriptor, action, after);
    }

    private static TransformRuleDefinition rule(
            String id, String method, String descriptor, MethodTransformAction action, String... after) {
        return new TransformRuleDefinition(
                id,
                "test",
                "26.2",
                EnumSet.allOf(GameSide.class),
                new MethodTarget(TARGET, method, descriptor),
                action,
                TransformFailurePolicy.ABORT_STARTUP,
                List.of(after));
    }

    private static AccessWidening widening(String owner, Kind kind, String name, String descriptor, Access access) {
        return new AccessWidening(
                "test",
                "26.2",
                EnumSet.allOf(GameSide.class),
                owner,
                kind,
                name,
                descriptor,
                Set.of(access),
                TransformFailurePolicy.ABORT_STARTUP);
    }

    private static TransformPlan plan(TransformRuleDefinition... rules) {
        return new ClassTransformPlanner()
                .plan(List.of(rules), "26.2", GameSide.CLIENT, name -> Optional.of(bytes(name)));
    }

    private static Class<?> load(TransformPlan plan) throws ClassNotFoundException {
        return load(plan, TARGET);
    }

    /** Loads {@code className} and the other fixtures through the plan in a fresh class loader. */
    private static Class<?> load(TransformPlan plan, String className) throws ClassNotFoundException {
        ClassTransformExecutor executor = new ClassTransformExecutor(plan, Optional.empty(), entry -> {});
        ClassLoader loader = new ClassLoader(CallSiteAndAccessTest.class.getClassLoader()) {
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
        return loader.loadClass(className.replace('/', '.'));
    }

    private static ClassNode parse(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    private static Optional<byte[]> bytesOf(String internalName) {
        return Optional.of(bytes(internalName));
    }

    private static byte[] bytes(String internalName) {
        try (InputStream input =
                CallSiteAndAccessTest.class.getClassLoader().getResourceAsStream(internalName + ".class")) {
            if (input == null) {
                throw new IllegalStateException("missing test class " + internalName);
            }
            return input.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
