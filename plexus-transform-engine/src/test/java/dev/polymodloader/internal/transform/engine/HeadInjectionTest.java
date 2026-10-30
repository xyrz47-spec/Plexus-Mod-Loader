package dev.polymodloader.internal.transform.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.polymodloader.api.runtime.GameSide;
import dev.polymodloader.internal.transform.InjectStaticCallAtHead;
import dev.polymodloader.internal.transform.MethodTarget;
import dev.polymodloader.internal.transform.TransformFailurePolicy;
import dev.polymodloader.internal.transform.TransformProblem;
import dev.polymodloader.internal.transform.TransformProblemCode;
import dev.polymodloader.internal.transform.TransformRuleDefinition;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

class HeadInjectionTest {

    private static final String OWNER = "test/Counter";
    private static final String HOOKS = "dev/polymodloader/internal/transform/engine/TestHooks";

    @Test
    void hookRunsWithThisBeforeTheOriginalBodyAndFramesStayValid() throws Exception {
        byte[] original = counterClass();
        TransformPlan plan = plan(rule(new MethodTarget(OWNER, "tick", "(I)I")), original);

        byte[] transformed = new ClassTransformExecutor(plan, Optional.empty(), entry -> {}).transform(OWNER, original);
        Class<?> type = define(transformed);
        Object counter = type.getConstructor().newInstance();
        TestHooks.ENTERED.clear();

        assertEquals(List.of(), plan.rejected());
        assertEquals(10, type.getMethod("tick", int.class).invoke(counter, 15), "original behaviour is kept");
        assertEquals(List.of(counter), TestHooks.ENTERED, "hook ran once, with this");
    }

    @Test
    void staticMethodsAreRejected() {
        TransformPlan plan = plan(rule(new MethodTarget(OWNER, "create", "()Ltest/Counter;")), counterClass());

        assertEquals(
                List.of(TransformProblemCode.PRECONDITION_FAILED),
                plan.rejected().stream().map(TransformProblem::code).toList());
    }

    @Test
    void constructorsAreRejected() {
        TransformPlan plan = plan(rule(new MethodTarget(OWNER, "<init>", "()V")), counterClass());

        assertEquals(
                List.of(TransformProblemCode.PRECONDITION_FAILED),
                plan.rejected().stream().map(TransformProblem::code).toList());
    }

    private static TransformRuleDefinition rule(MethodTarget target) {
        return new TransformRuleDefinition(
                "test:enter",
                "test",
                "26.2",
                EnumSet.allOf(GameSide.class),
                target,
                new InjectStaticCallAtHead(HOOKS, "onEnter"),
                TransformFailurePolicy.DISABLE_RULE);
    }

    private static TransformPlan plan(TransformRuleDefinition rule, byte[] classFile) {
        return new ClassTransformPlanner()
                .plan(List.of(rule), "26.2", GameSide.CLIENT, name -> Optional.of(classFile.clone()));
    }

    /**
     * {@code class Counter { int tick(int n) { while (n > 10) n--; return n; } static Counter create() }} —
     * the loop makes javac-style stack map frames necessary, which head injection must keep valid.
     */
    private static byte[] counterClass() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        writer.visit(Opcodes.V25, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, OWNER, null, "java/lang/Object", null);
        MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();
        MethodVisitor tick = writer.visitMethod(Opcodes.ACC_PUBLIC, "tick", "(I)I", null, null);
        tick.visitCode();
        Label loop = new Label();
        Label done = new Label();
        tick.visitLabel(loop);
        tick.visitVarInsn(Opcodes.ILOAD, 1);
        tick.visitIntInsn(Opcodes.BIPUSH, 10);
        tick.visitJumpInsn(Opcodes.IF_ICMPLE, done);
        tick.visitIincInsn(1, -1);
        tick.visitJumpInsn(Opcodes.GOTO, loop);
        tick.visitLabel(done);
        tick.visitVarInsn(Opcodes.ILOAD, 1);
        tick.visitInsn(Opcodes.IRETURN);
        tick.visitMaxs(0, 0);
        tick.visitEnd();
        MethodVisitor create =
                writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "create", "()Ltest/Counter;", null, null);
        create.visitCode();
        create.visitTypeInsn(Opcodes.NEW, OWNER);
        create.visitInsn(Opcodes.DUP);
        create.visitMethodInsn(Opcodes.INVOKESPECIAL, OWNER, "<init>", "()V", false);
        create.visitInsn(Opcodes.ARETURN);
        create.visitMaxs(0, 0);
        create.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static Class<?> define(byte[] classFile) throws ClassNotFoundException {
        return new ClassLoader(HeadInjectionTest.class.getClassLoader()) {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                if (!name.equals("test.Counter")) {
                    throw new ClassNotFoundException(name);
                }
                return defineClass(name, classFile, 0, classFile.length);
            }
        }.loadClass("test.Counter");
    }
}
