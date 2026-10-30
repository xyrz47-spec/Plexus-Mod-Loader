package dev.polymodloader.compat.fabric;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.polymodloader.api.runtime.GameSide;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypeReference;

class EnvironmentStripperTest {

    private static final String CLIENT_CLASS = "com.example.common.ClientOnly";
    private static final String CLIENT_ONLY = "Lorg/quiltmc/loader/api/minecraft/ClientOnly;";
    private static final String DEDICATED_SERVER_ONLY = "Lorg/quiltmc/loader/api/minecraft/DedicatedServerOnly;";
    private static final String REQUIRES = "Lorg/quiltmc/loader/api/Requires;";
    private static final String QUILT_CLASS = "com.example.quilt_mod.Thing";

    @Test
    void aClassForTheClientCannotLoadOnAServer() {
        EnvironmentStripper stripper = new EnvironmentStripper(GameSide.DEDICATED_SERVER, Set.of());

        assertThrows(IllegalStateException.class, () -> stripper.apply(CLIENT_CLASS, clientOnlyClass()));
    }

    @Test
    void aClassOfAModTheHostLoaderRunsIsLeftAlone() {
        // A Forge mod built from code shared with its Fabric build keeps Fabric's annotations; Forge ignores them.
        EnvironmentStripper stripper = new EnvironmentStripper(GameSide.DEDICATED_SERVER, Set.of(CLIENT_CLASS));
        byte[] bytes = clientOnlyClass();

        assertSame(bytes, stripper.apply(CLIENT_CLASS, bytes));
    }

    private static byte[] clientOnlyClass() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, CLIENT_CLASS.replace('.', '/'), null, "java/lang/Object", null);
        AnnotationVisitor environment = writer.visitAnnotation("Lnet/fabricmc/api/Environment;", true);
        environment.visitEnum("value", "Lnet/fabricmc/api/EnvType;", "CLIENT");
        environment.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    // Quilt's annotations (ADR-0027 decision 9).

    private static EnvironmentStripper quilt(GameSide side, Set<String> classes, Map<String, byte[]> packageInfos) {
        return new EnvironmentStripper(
                side,
                Set.of(),
                new EnvironmentStripper.QuiltScope(classes, packageInfos, Set.of("quilt_mod", "present_mod")));
    }

    /** A class whose members {@code members} adds, of a Quilt mod or not. */
    private static byte[] type(String name, String[] interfaces, Consumer<ClassWriter> members) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, name.replace('.', '/'), null, "java/lang/Object", interfaces);
        members.accept(writer);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void method(ClassWriter writer, String name, String annotation, Consumer<AnnotationVisitor> values) {
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, name, "()V", null, null);
        if (annotation != null) {
            AnnotationVisitor visitor = method.visitAnnotation(annotation, false);
            values.accept(visitor);
            visitor.visitEnd();
        }
        method.visitCode();
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    private static void field(ClassWriter writer, String name, String annotation, Consumer<AnnotationVisitor> values) {
        FieldVisitor field = writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, name, "I", null, null);
        AnnotationVisitor visitor = field.visitAnnotation(annotation, false);
        values.accept(visitor);
        visitor.visitEnd();
        field.visitEnd();
    }

    private static Consumer<AnnotationVisitor> requires(String... mods) {
        return visitor -> {
            AnnotationVisitor array = visitor.visitArray("value");
            for (String mod : mods) {
                array.visit(null, mod);
            }
            array.visitEnd();
        };
    }

    private static Consumer<AnnotationVisitor> none() {
        return visitor -> {};
    }

    /** The class's interfaces, fields and methods, by name. */
    private static List<String> members(byte[] bytes) {
        List<String> members = new ArrayList<>();
        new ClassReader(bytes)
                .accept(
                        new ClassVisitor(Opcodes.ASM9) {
                            @Override
                            public void visit(
                                    int version,
                                    int access,
                                    String name,
                                    String signature,
                                    String superName,
                                    String[] interfaces) {
                                if (interfaces != null) {
                                    for (String itf : interfaces) {
                                        members.add("implements " + itf);
                                    }
                                }
                            }

                            @Override
                            public FieldVisitor visitField(
                                    int access, String name, String descriptor, String signature, Object value) {
                                members.add("field " + name);
                                return null;
                            }

                            @Override
                            public MethodVisitor visitMethod(
                                    int access, String name, String descriptor, String signature, String[] exceptions) {
                                members.add(name);
                                return null;
                            }
                        },
                        0);
        return members;
    }

    @Test
    void quiltSideAnnotationsRemoveMembersOfQuiltModClassesOnly() {
        byte[] bytes = type(QUILT_CLASS, null, writer -> {
            method(writer, "render", CLIENT_ONLY, none());
            method(writer, "tick", null, null);
            field(writer, "serverState", DEDICATED_SERVER_ONLY, none());
        });

        assertEquals(
                List.of("field serverState", "tick"),
                members(quilt(GameSide.DEDICATED_SERVER, Set.of(QUILT_CLASS), Map.of())
                        .apply(QUILT_CLASS, bytes)));
        assertEquals(
                List.of("render", "tick"),
                members(quilt(GameSide.CLIENT, Set.of(QUILT_CLASS), Map.of()).apply(QUILT_CLASS, bytes)));
        assertSame(
                bytes,
                quilt(GameSide.DEDICATED_SERVER, Set.of(), Map.of()).apply(QUILT_CLASS, bytes),
                "a Fabric mod's class keeps Fabric Loader's behaviour: Quilt's annotations mean nothing");
    }

    @Test
    void aQuiltClassForTheOtherSideCannotLoad() {
        byte[] bytes = type(
                QUILT_CLASS,
                null,
                writer -> writer.visitAnnotation(CLIENT_ONLY, false).visitEnd());

        IllegalStateException refused = assertThrows(
                IllegalStateException.class,
                () -> quilt(GameSide.DEDICATED_SERVER, Set.of(QUILT_CLASS), Map.of())
                        .apply(QUILT_CLASS, bytes));
        assertTrue(refused.getMessage().contains("annotated with @ClientOnly but we're on the dedicated server"));
        assertSame(bytes, quilt(GameSide.CLIENT, Set.of(QUILT_CLASS), Map.of()).apply(QUILT_CLASS, bytes));
    }

    @Test
    void requiresRemovesWhatNeedsAModThatIsNotLoaded() {
        byte[] bytes = type(QUILT_CLASS, null, writer -> {
            field(writer, "integration", REQUIRES, requires("absent_mod"));
            method(writer, "withPresent", REQUIRES, requires("present_mod", "quilt_mod"));
            method(writer, "withBoth", REQUIRES, requires("present_mod", "absent_mod"));
        });

        assertEquals(
                List.of("withPresent"),
                members(quilt(GameSide.CLIENT, Set.of(QUILT_CLASS), Map.of()).apply(QUILT_CLASS, bytes)));
        byte[] needsAbsent = type(QUILT_CLASS, null, writer -> {
            AnnotationVisitor annotation = writer.visitAnnotation(REQUIRES, false);
            requires("absent_mod").accept(annotation);
            annotation.visitEnd();
        });
        IllegalStateException refused = assertThrows(
                IllegalStateException.class,
                () -> quilt(GameSide.CLIENT, Set.of(QUILT_CLASS), Map.of()).apply(QUILT_CLASS, needsAbsent));
        assertTrue(refused.getMessage().contains("[absent_mod] are not loaded"), refused::getMessage);
    }

    /** {@code @ClientOnly} on a package's {@code package-info} refuses every class of that package on a server. */
    @Test
    void aPackageInfoForTheOtherSideRefusesThePackage() {
        byte[] packageInfo = type(
                "com.example.quilt_mod.package-info",
                null,
                writer -> writer.visitAnnotation(CLIENT_ONLY, false).visitEnd());
        byte[] plain = type(QUILT_CLASS, null, writer -> method(writer, "tick", null, null));
        Map<String, byte[]> infos = Map.of("com.example.quilt_mod", packageInfo);

        IllegalStateException refused = assertThrows(
                IllegalStateException.class,
                () -> quilt(GameSide.DEDICATED_SERVER, Set.of(QUILT_CLASS), infos)
                        .apply(QUILT_CLASS, plain));
        assertTrue(refused.getMessage().startsWith("Cannot load package com.example.quilt_mod because the package"));
        assertSame(plain, quilt(GameSide.CLIENT, Set.of(QUILT_CLASS), infos).apply(QUILT_CLASS, plain));
    }

    /** An annotation on an {@code implements} clause removes that interface. */
    @Test
    void aTypeAnnotationOnAnInterfaceRemovesIt() {
        byte[] bytes = type(QUILT_CLASS, new String[] {"java/lang/Runnable", "java/io/Serializable"}, writer -> {
            writer.visitTypeAnnotation(TypeReference.newSuperTypeReference(0).getValue(), null, CLIENT_ONLY, false)
                    .visitEnd();
            MethodVisitor run = writer.visitMethod(Opcodes.ACC_PUBLIC, "run", "()V", null, null);
            run.visitCode();
            run.visitInsn(Opcodes.RETURN);
            run.visitMaxs(0, 1);
            run.visitEnd();
        });

        assertEquals(
                List.of("implements java/io/Serializable", "run"),
                members(quilt(GameSide.DEDICATED_SERVER, Set.of(QUILT_CLASS), Map.of())
                        .apply(QUILT_CLASS, bytes)));
    }

    /**
     * A removed method takes the lambda bodies only it uses ({@code stripLambdas}, the default); a body another method
     * uses too stays. The result still loads.
     */
    @Test
    void lambdasOfARemovedMethodGoWithIt() throws ReflectiveOperationException {
        byte[] bytes = lambdaClass(true);

        byte[] stripped =
                quilt(GameSide.DEDICATED_SERVER, Set.of(QUILT_CLASS), Map.of()).apply(QUILT_CLASS, bytes);

        assertEquals(List.of("shared", "lambda$shared$1", "<init>"), members(stripped));
        Class<?> loaded = new ClassLoader(getClass().getClassLoader()) {
            Class<?> define() {
                return defineClass(QUILT_CLASS, stripped, 0, stripped.length);
            }
        }.define();
        loaded.getMethod("shared").invoke(null);
        assertEquals(
                List.of("lambda$render$0", "shared", "lambda$shared$1", "<init>"),
                members(quilt(GameSide.DEDICATED_SERVER, Set.of(QUILT_CLASS), Map.of())
                        .apply(QUILT_CLASS, lambdaClass(false))),
                "stripLambdas = false keeps the lambda body");
    }

    /**
     * {@code render()} ({@code @ClientOnly}) makes a lambda of {@code lambda$render$0}; {@code shared()} makes one of
     * {@code lambda$shared$1}.
     */
    private static byte[] lambdaClass(boolean stripLambdas) {
        String owner = QUILT_CLASS.replace('.', '/');
        Handle metafactory = new Handle(
                Opcodes.H_INVOKESTATIC,
                "java/lang/invoke/LambdaMetafactory",
                "metafactory",
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;"
                        + "Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)"
                        + "Ljava/lang/invoke/CallSite;",
                false);
        return type(QUILT_CLASS, null, writer -> {
            for (String name : List.of("render", "shared")) {
                MethodVisitor method =
                        writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, name, "()V", null, null);
                if (name.equals("render")) {
                    AnnotationVisitor annotation = method.visitAnnotation(CLIENT_ONLY, false);
                    if (!stripLambdas) {
                        annotation.visit("stripLambdas", false);
                    }
                    annotation.visitEnd();
                }
                String lambda = "lambda$" + name + (name.equals("render") ? "$0" : "$1");
                method.visitCode();
                method.visitInvokeDynamicInsn(
                        "run",
                        "()Ljava/lang/Runnable;",
                        metafactory,
                        Type.getType("()V"),
                        new Handle(Opcodes.H_INVOKESTATIC, owner, lambda, "()V", false),
                        Type.getType("()V"));
                method.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/lang/Runnable", "run", "()V", true);
                method.visitInsn(Opcodes.RETURN);
                method.visitMaxs(0, 0);
                method.visitEnd();
                MethodVisitor body = writer.visitMethod(
                        Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, lambda, "()V", null, null);
                body.visitCode();
                body.visitInsn(Opcodes.RETURN);
                body.visitMaxs(0, 0);
                body.visitEnd();
            }
            MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
            init.visitCode();
            init.visitVarInsn(Opcodes.ALOAD, 0);
            init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            init.visitInsn(Opcodes.RETURN);
            init.visitMaxs(0, 0);
            init.visitEnd();
        });
    }
}
