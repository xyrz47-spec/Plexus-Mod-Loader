package dev.polymodloader.runtime.classloading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.polymodloader.compat.bridge.BridgeClassTransformer;
import dev.polymodloader.internal.transform.engine.ClassTransformExecutor;
import dev.polymodloader.internal.transform.engine.TransformPlan;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** The game class loader as the owner of a JPMS game layer (ADR-0021 G3a). */
class GameClassLoaderModulesTest {

    @TempDir
    Path directory;

    // Module resources are jar: URLs; the JDK's cache of their jar files would keep the test jars locked on Windows.
    @BeforeEach
    void doNotCacheJarUrls() {
        URLConnection.setDefaultUseCaches("jar", false);
    }

    @AfterEach
    void cacheJarUrlsAgain() {
        URLConnection.setDefaultUseCaches("jar", true);
    }

    @Test
    void definesModuleClassesItselfAndKeepsPlainJarsUnnamed() throws Exception {
        // test.a: a.Greeter.greet() returns "a"; a.Hello is a Supplier service; a/data.txt is a resource.
        Path moduleA = jar(
                "module-a.jar",
                "test.a",
                Map.of(
                        "a/Greeter.class", staticString("a/Greeter", "greet", "a"),
                        "a/Hello.class", supplier("a/Hello", "hello"),
                        "a/data.txt", "data of a".getBytes(StandardCharsets.UTF_8),
                        "META-INF/services/java.util.function.Supplier", "a.Hello".getBytes(StandardCharsets.UTF_8)));
        // test.b: b.User.call() returns a.Greeter.greet().
        Path moduleB =
                jar("module-b.jar", "test.b", Map.of("b/User.class", delegate("b/User", "call", "a/Greeter", "greet")));
        // An ordinary jar of the game class loader: c.Plain.call() returns b.User.call().
        Path plain = jar("plain.jar", null, Map.of("c/Plain.class", delegate("c/Plain", "call", "b/User", "call")));

        List<String> transformed = new CopyOnWriteArrayList<>();
        ClassTransformExecutor executor =
                new ClassTransformExecutor(new TransformPlan(Map.of(), List.of()), Optional.empty(), entry -> {});
        try (GameClassLoader loader =
                new GameClassLoader(List.of(plain), getClass().getClassLoader(), executor)) {
            loader.installBridgeTransformer(new BridgeClassTransformer() {
                @Override
                public String name() {
                    return "recorder";
                }

                @Override
                public byte[] transform(String className, byte[] bytes) {
                    transformed.add(className);
                    return bytes;
                }
            });
            Configuration configuration = ModuleLayer.boot()
                    .configuration()
                    .resolveAndBind(ModuleFinder.of(moduleA, moduleB), ModuleFinder.of(), Set.of("test.a", "test.b"));

            ModuleLayer layer = loader.define(configuration, List.of(ModuleLayer.boot()), true)
                    .layer();

            Module b = layer.findModule("test.b").orElseThrow();
            Class<?> user = Class.forName(b, "b.User");
            assertNotNull(user, "Class.forName(module, name) finds the class through the game class loader");
            assertSame(b, user.getModule());
            assertSame(loader, user.getClassLoader());
            assertEquals("a", user.getMethod("call").invoke(null));
            assertEquals("test.a", loader.loadClass("a.Greeter").getModule().getName());
            assertTrue(transformed.containsAll(List.of("b.User", "a.Greeter")), transformed.toString());

            List<Object> services = ServiceLoader.load(layer, Supplier.class).stream()
                    .<Object>map(ServiceLoader.Provider::get)
                    .toList();
            assertEquals(1, services.size());
            assertEquals("hello", ((Supplier<?>) services.get(0)).get());
            assertSame(
                    layer.findModule("test.a").orElseThrow(),
                    services.get(0).getClass().getModule());

            try (InputStream data = layer.findModule("test.a").orElseThrow().getResourceAsStream("a/data.txt")) {
                assertNotNull(data, "module resources come from the module's own content");
                assertEquals("data of a", new String(data.readAllBytes(), StandardCharsets.UTF_8));
            }
            assertNotNull(loader.getResource("a/data.txt"), "module resources are also class loader resources");

            Class<?> plainClass = loader.loadClass("c.Plain");
            assertFalse(plainClass.getModule().isNamed());
            assertEquals("a", plainClass.getMethod("call").invoke(null));
            assertTrue(loader.classBytes("a/Greeter").isPresent(), "Mixin can read module classes too");
        }
    }

    private Path jar(String name, String moduleName, Map<String, byte[]> entries) throws IOException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        if (moduleName != null) {
            manifest.getMainAttributes().putValue("Automatic-Module-Name", moduleName);
        }
        Path jar = directory.resolve(name);
        try (OutputStream file = Files.newOutputStream(jar);
                JarOutputStream out = new JarOutputStream(file, manifest)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                out.putNextEntry(new JarEntry(entry.getKey()));
                out.write(entry.getValue());
                out.closeEntry();
            }
        }
        return jar;
    }

    /** {@code public class <owner> { public static String <method>() { return "<value>"; } }} */
    private static byte[] staticString(String owner, String method, String value) {
        ClassWriter writer = classWithConstructor(owner, "java/lang/Object", new String[0]);
        MethodVisitor code =
                writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, method, "()Ljava/lang/String;", null, null);
        code.visitCode();
        code.visitLdcInsn(value);
        code.visitInsn(Opcodes.ARETURN);
        code.visitMaxs(0, 0);
        code.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    /** {@code public class <owner> { public static String <method>() { return <target>.<targetMethod>(); } }} */
    private static byte[] delegate(String owner, String method, String target, String targetMethod) {
        ClassWriter writer = classWithConstructor(owner, "java/lang/Object", new String[0]);
        MethodVisitor code =
                writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, method, "()Ljava/lang/String;", null, null);
        code.visitCode();
        code.visitMethodInsn(Opcodes.INVOKESTATIC, target, targetMethod, "()Ljava/lang/String;", false);
        code.visitInsn(Opcodes.ARETURN);
        code.visitMaxs(0, 0);
        code.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    /** {@code public class <owner> implements Supplier { public Object get() { return "<value>"; } }} */
    private static byte[] supplier(String owner, String value) {
        ClassWriter writer =
                classWithConstructor(owner, "java/lang/Object", new String[] {"java/util/function/Supplier"});
        MethodVisitor code = writer.visitMethod(Opcodes.ACC_PUBLIC, "get", "()Ljava/lang/Object;", null, null);
        code.visitCode();
        code.visitLdcInsn(value);
        code.visitInsn(Opcodes.ARETURN);
        code.visitMaxs(0, 0);
        code.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static ClassWriter classWithConstructor(String owner, String superName, String[] interfaces) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, owner, null, superName, interfaces);
        MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, superName, "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();
        return writer;
    }
}
