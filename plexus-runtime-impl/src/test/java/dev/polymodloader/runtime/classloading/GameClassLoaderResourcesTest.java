package dev.polymodloader.runtime.classloading;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import dev.polymodloader.internal.transform.engine.ClassTransformExecutor;
import dev.polymodloader.internal.transform.engine.TransformPlan;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Resource lookup of the game class loader when the JVM class path holds another copy of the game jar. */
class GameClassLoaderResourcesTest {

    @TempDir
    Path directory;

    // The JDK's cache of jar: URL files would keep the test jars locked on Windows.
    @BeforeEach
    void doNotCacheJarUrls() {
        URLConnection.setDefaultUseCaches("jar", false);
    }

    @AfterEach
    void cacheJarUrlsAgain() {
        URLConnection.setDefaultUseCaches("jar", true);
    }

    @Test
    void ownJarsComeBeforeTheParentButPolyModLoaderResourcesStayWithTheParent() throws Exception {
        // As in a launcher install with Forge: the class path has the vanilla jar, this loader the patched one.
        Path vanilla = jar(
                "vanilla.jar",
                Map.of(
                        "net/game/Server.class", "vanilla",
                        "dev/polymodloader/runtime/info.txt", "parent"));
        Path patched = jar(
                "patched.jar",
                Map.of(
                        "net/game/Server.class", "patched",
                        "dev/polymodloader/runtime/info.txt", "mod copy"));
        ClassTransformExecutor executor =
                new ClassTransformExecutor(new TransformPlan(Map.of(), List.of()), Optional.empty(), entry -> {});
        try (URLClassLoader parent =
                        new URLClassLoader(new URL[] {vanilla.toUri().toURL()}, null);
                GameClassLoader loader = new GameClassLoader(List.of(patched), parent, executor)) {
            assertEquals("patched", read(loader, "net/game/Server.class"));
            assertEquals("parent", read(loader, "dev/polymodloader/runtime/info.txt"));
            assertEquals("vanilla", read(parent, "net/game/Server.class"), "the parent still sees its own copy");
        }
    }

    /**
     * With a host loader (Forge), the patched game is a resource jar until the loader defines it as a module; Mixin reads
     * its classes before that, and must see the patched class, not the vanilla copy on the class path.
     */
    @Test
    void classBytesComeFromResourceJarsBeforeTheParent() throws Exception {
        byte[] vanillaServer = serverClass("vanilla");
        byte[] patchedServer = serverClass("patched");
        Path vanilla = jar("vanilla.jar", Map.of("net/game/Server.class", vanillaServer));
        Path patched = jar("patched.jar", Map.of("net/game/Server.class", patchedServer));
        ClassTransformExecutor executor =
                new ClassTransformExecutor(new TransformPlan(Map.of(), List.of()), Optional.empty(), entry -> {});
        try (URLClassLoader parent =
                        new URLClassLoader(new URL[] {vanilla.toUri().toURL()}, null);
                GameClassLoader loader =
                        new GameClassLoader(List.of(), List.of(patched), parent, executor, List.of(), warning -> {})) {
            assertArrayEquals(
                    patchedServer, loader.classBytes("net.game.Server").orElseThrow());
        }
    }

    private static byte[] serverClass(String field) {
        return ClassFile.of()
                .build(ClassDesc.of("net.game.Server"), builder -> builder.withField(field, ConstantDescs.CD_int, 0));
    }

    private static String read(ClassLoader loader, String name) throws IOException {
        try (InputStream input = loader.getResourceAsStream(name)) {
            assertNotNull(input, name);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private Path jar(String name, Map<String, ?> entries) throws IOException {
        Path jar = directory.resolve(name);
        try (OutputStream file = Files.newOutputStream(jar);
                JarOutputStream out = new JarOutputStream(file)) {
            for (Map.Entry<String, ?> entry : entries.entrySet()) {
                out.putNextEntry(new JarEntry(entry.getKey()));
                out.write(
                        entry.getValue() instanceof byte[] bytes
                                ? bytes
                                : entry.getValue().toString().getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        return jar;
    }
}
