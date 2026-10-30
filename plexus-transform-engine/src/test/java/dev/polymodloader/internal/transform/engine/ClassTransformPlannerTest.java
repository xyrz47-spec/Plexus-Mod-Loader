package dev.polymodloader.internal.transform.engine;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.polymodloader.api.runtime.GameSide;
import dev.polymodloader.internal.transform.MethodTarget;
import dev.polymodloader.internal.transform.ReplaceConstantReturnWithStaticCall;
import dev.polymodloader.internal.transform.ReplaceReturnedStringConstant;
import dev.polymodloader.internal.transform.TransformException;
import dev.polymodloader.internal.transform.TransformFailurePolicy;
import dev.polymodloader.internal.transform.TransformProblem;
import dev.polymodloader.internal.transform.TransformProblemCode;
import dev.polymodloader.internal.transform.TransformRuleDefinition;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

class ClassTransformPlannerTest {

    private static final String OWNER = "test/BrandSource";
    private static final MethodTarget BRAND = new MethodTarget(OWNER, "brand", "()Ljava/lang/String;");
    private static final byte[] BRAND_SOURCE = brandSource("vanilla");
    private static final ClassBytesSource CLASSES =
            name -> name.equals(OWNER) ? Optional.of(BRAND_SOURCE.clone()) : Optional.empty();

    private final ClassTransformPlanner planner = new ClassTransformPlanner();

    @Test
    void acceptedRuleChangesBehaviourOfTheLoadedClass() throws Exception {
        TransformPlan plan =
                planner.plan(List.of(rule("test:brand", BRAND, "vanilla")), "26.2", GameSide.CLIENT, CLASSES);
        List<TransformReportEntry> reported = new ArrayList<>();
        ClassTransformExecutor executor = new ClassTransformExecutor(plan, Optional.empty(), reported::add);

        byte[] input = BRAND_SOURCE.clone();
        byte[] transformed = executor.transform(OWNER, input);

        assertEquals(List.of(), plan.rejected());
        assertArrayEquals(BRAND_SOURCE, input, "input bytes must not be modified");
        assertEquals("poly", invokeBrand(transformed));
        assertEquals(List.of("test:brand"), reported.getFirst().appliedRuleIds());
        assertTrue(
                !reported.getFirst().sha256Before().equals(reported.getFirst().sha256After()));
    }

    @Test
    void constantReturnCanBeRedirectedToAStaticHook() throws Exception {
        TransformRuleDefinition redirect = new TransformRuleDefinition(
                "test:hook",
                "test",
                "26.2",
                EnumSet.allOf(GameSide.class),
                BRAND,
                new ReplaceConstantReturnWithStaticCall(
                        "vanilla", "dev/polymodloader/internal/transform/engine/TestHooks", "brand"),
                TransformFailurePolicy.DISABLE_RULE);
        TransformPlan plan = planner.plan(List.of(redirect), "26.2", GameSide.CLIENT, CLASSES);

        byte[] transformed =
                new ClassTransformExecutor(plan, Optional.empty(), entry -> {}).transform(OWNER, BRAND_SOURCE);

        assertEquals(List.of(), plan.rejected());
        assertEquals("from-hook", invokeBrand(transformed));
    }

    @Test
    void classesWithoutRulesAreReturnedUntouched() {
        TransformPlan plan = planner.plan(List.of(), "26.2", GameSide.CLIENT, CLASSES);
        ClassTransformExecutor executor = new ClassTransformExecutor(plan, Optional.empty(), entry -> {});

        assertSame(BRAND_SOURCE, executor.transform(OWNER, BRAND_SOURCE));
    }

    @Test
    void wrongDescriptorFailsSafelyAndNamesTheExistingOverloads() {
        MethodTarget wrong = new MethodTarget(OWNER, "brand", "()Ljava/lang/Object;");

        TransformPlan plan =
                planner.plan(List.of(rule("test:brand", wrong, "vanilla")), "26.2", GameSide.CLIENT, CLASSES);

        TransformProblem problem = onlyProblem(plan, TransformProblemCode.TARGET_METHOD_NOT_FOUND);
        assertTrue(problem.message().contains("()Ljava/lang/String;"), problem.message());
        assertTrue(problem.message().contains("(I)Ljava/lang/String;"), problem.message());
        assertEquals(0, plan.acceptedRuleCount());
    }

    @Test
    void missingClassIsRejected() {
        MethodTarget missing = new MethodTarget("test/Missing", "brand", "()Ljava/lang/String;");

        onlyProblem(
                planner.plan(List.of(rule("test:brand", missing, "vanilla")), "26.2", GameSide.CLIENT, CLASSES),
                TransformProblemCode.TARGET_CLASS_NOT_FOUND);
    }

    @Test
    void unexpectedBytecodeFailsThePrecondition() {
        TransformProblem problem = onlyProblem(
                planner.plan(List.of(rule("test:brand", BRAND, "fabric")), "26.2", GameSide.CLIENT, CLASSES),
                TransformProblemCode.PRECONDITION_FAILED);

        assertTrue(problem.message().contains("vanilla"), problem.message());
    }

    @Test
    void competingRulesOnOneMethodAreAllRejected() {
        TransformPlan plan = planner.plan(
                List.of(rule("first:brand", BRAND, "vanilla"), rule("second:brand", BRAND, "vanilla")),
                "26.2",
                GameSide.CLIENT,
                CLASSES);

        assertEquals(0, plan.acceptedRuleCount());
        assertEquals(
                List.of(TransformProblemCode.CONFLICTING_RULES, TransformProblemCode.CONFLICTING_RULES),
                plan.rejected().stream().map(TransformProblem::code).toList());
    }

    @Test
    void duplicateRuleIdsAreRejected() {
        MethodTarget other = new MethodTarget(OWNER, "brand", "(I)Ljava/lang/String;");
        TransformPlan plan = planner.plan(
                List.of(rule("test:brand", BRAND, "vanilla"), rule("test:brand", other, "vanilla")),
                "26.2",
                GameSide.CLIENT,
                CLASSES);

        assertEquals(1, plan.acceptedRuleCount());
        onlyProblem(plan, TransformProblemCode.DUPLICATE_RULE_ID);
    }

    @Test
    void rulesForAnotherGameVersionAreRejected() {
        onlyProblem(
                planner.plan(List.of(rule("test:brand", BRAND, "vanilla")), "26.3", GameSide.CLIENT, CLASSES),
                TransformProblemCode.GAME_VERSION_MISMATCH);
    }

    @Test
    void rulesForTheOtherSideAreSkippedWithoutError() {
        TransformRuleDefinition clientOnly = new TransformRuleDefinition(
                "test:brand",
                "test",
                "26.2",
                Set.of(GameSide.CLIENT),
                BRAND,
                new ReplaceReturnedStringConstant("vanilla", "poly"),
                TransformFailurePolicy.DISABLE_RULE);

        TransformPlan plan = planner.plan(List.of(clientOnly), "26.2", GameSide.DEDICATED_SERVER, CLASSES);

        assertEquals(0, plan.acceptedRuleCount());
        assertEquals(List.of(), plan.rejected());
    }

    @Test
    void abortStartupRulesSurfaceAsAbortingProblems() {
        TransformRuleDefinition critical = new TransformRuleDefinition(
                "test:critical",
                "test",
                "26.2",
                EnumSet.allOf(GameSide.class),
                new MethodTarget("test/Missing", "run", "()V"),
                new ReplaceReturnedStringConstant("a", "b"),
                TransformFailurePolicy.ABORT_STARTUP);

        TransformPlan plan = planner.plan(List.of(critical), "26.2", GameSide.CLIENT, CLASSES);

        assertEquals(1, plan.abortingProblems().size());
    }

    @Test
    void bytecodeThatChangedAfterPlanningFailsLoudly() {
        TransformPlan plan =
                planner.plan(List.of(rule("test:brand", BRAND, "vanilla")), "26.2", GameSide.CLIENT, CLASSES);
        ClassTransformExecutor executor = new ClassTransformExecutor(plan, Optional.empty(), entry -> {});

        TransformException failure =
                assertThrows(TransformException.class, () -> executor.transform(OWNER, brandSource("modded")));

        assertEquals(TransformProblemCode.PRECONDITION_FAILED, failure.code());
    }

    @Test
    void dumpDirectoryReceivesBeforeAndAfterClassFiles(@TempDir Path dump) {
        TransformPlan plan =
                planner.plan(List.of(rule("test:brand", BRAND, "vanilla")), "26.2", GameSide.CLIENT, CLASSES);

        new ClassTransformExecutor(plan, Optional.of(dump), entry -> {}).transform(OWNER, BRAND_SOURCE);

        assertTrue(Files.isRegularFile(dump.resolve("before/test/BrandSource.class")));
        assertTrue(Files.isRegularFile(dump.resolve("after/test/BrandSource.class")));
    }

    private static TransformProblem onlyProblem(TransformPlan plan, TransformProblemCode expected) {
        assertEquals(1, plan.rejected().size(), () -> "rejected: " + plan.rejected());
        TransformProblem problem = plan.rejected().getFirst();
        assertEquals(expected, problem.code());
        return problem;
    }

    private static TransformRuleDefinition rule(String id, MethodTarget target, String expectedConstant) {
        return new TransformRuleDefinition(
                id,
                id.substring(0, id.indexOf(':')),
                "26.2",
                EnumSet.allOf(GameSide.class),
                target,
                new ReplaceReturnedStringConstant(expectedConstant, "poly"),
                TransformFailurePolicy.DISABLE_RULE);
    }

    /** A class shaped like Minecraft's ClientBrandRetriever plus an overload with another descriptor. */
    private static byte[] brandSource(String brand) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V25, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, OWNER, null, "java/lang/Object", null);
        MethodVisitor brandMethod = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "brand", "()Ljava/lang/String;", null, null);
        brandMethod.visitCode();
        brandMethod.visitLdcInsn(brand);
        brandMethod.visitInsn(Opcodes.ARETURN);
        brandMethod.visitMaxs(0, 0);
        brandMethod.visitEnd();
        MethodVisitor overload = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "brand", "(I)Ljava/lang/String;", null, null);
        overload.visitCode();
        overload.visitLdcInsn("numbered");
        overload.visitInsn(Opcodes.ARETURN);
        overload.visitMaxs(0, 0);
        overload.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static String invokeBrand(byte[] classFile) throws Exception {
        ClassLoader loader = new SingleClassLoader(Map.of("test.BrandSource", classFile));
        Method brand = loader.loadClass("test.BrandSource").getMethod("brand");
        return (String) brand.invoke(null);
    }

    private static final class SingleClassLoader extends ClassLoader {
        private final Map<String, byte[]> classes;

        SingleClassLoader(Map<String, byte[]> classes) {
            super(ClassTransformPlannerTest.class.getClassLoader());
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
}
