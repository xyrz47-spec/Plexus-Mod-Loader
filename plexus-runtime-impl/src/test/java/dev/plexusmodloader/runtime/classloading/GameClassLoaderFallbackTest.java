package dev.plexusmodloader.runtime.classloading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.plexusmodloader.internal.transform.engine.ClassTransformExecutor;
import dev.plexusmodloader.internal.transform.engine.TransformPlan;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A fallback class loader (NeoForge's FML plugin libraries, such as the Kotlin standard library of Kotlin for Forge,
 * ADR-0026) serves what the game class loader and its parent do not have, as FML's own class loader asks its fallback;
 * a fallback whose parent is the game class loader does not loop.
 */
class GameClassLoaderFallbackTest {

    @TempDir
    Path directory;

    @BeforeEach
    void doNotCacheJarUrls() {
        URLConnection.setDefaultUseCaches("jar", false);
    }

    @AfterEach
    void cacheJarUrlsAgain() {
        URLConnection.setDefaultUseCaches("jar", true);
    }

    @Test
    void classesAndResourcesOnlyTheFallbackHasComeFromIt() throws Exception {
        Path library = jar(
                "library.jar",
                Map.of(
                        "kotlin/Unit.class", ClassFile.of().build(ClassDesc.of("kotlin.Unit"), builder -> {}),
                        "kotlin/info.txt", "from the plugin library".getBytes(StandardCharsets.UTF_8)));
        ClassTransformExecutor executor =
                new ClassTransformExecutor(new TransformPlan(Map.of(), List.of()), Optional.empty(), entry -> {});
        try (GameClassLoader loader = new GameClassLoader(List.of(), getClass().getClassLoader(), executor);
                URLClassLoader plugins =
                        new URLClassLoader(new URL[] {library.toUri().toURL()}, loader)) {
            assertThrows(ClassNotFoundException.class, () -> loader.loadClass("kotlin.Unit"));
            assertNull(loader.getResource("kotlin/info.txt"));

            loader.addFallback(plugins);

            Class<?> unit = loader.loadClass("kotlin.Unit");
            assertSame(plugins, unit.getClassLoader(), "the fallback defines it, untransformed, as FML's loader would");
            assertNotNull(loader.getResource("kotlin/info.txt"));
            assertEquals(
                    1, Collections.list(loader.getResources("kotlin/info.txt")).size());
            assertThrows(ClassNotFoundException.class, () -> loader.loadClass("kotlin.Missing"));
            assertNull(loader.getResource("kotlin/missing.txt"));
        }
    }

    /**
     * The fallbacks are asked without this loader's class loading lock held. A fallback whose parent is this loader
     * takes its own lock for a class, then asks this loader for it (FML's plugin loader resolving a superclass); a
     * thread holding this loader's lock for the same class while it waits for the fallback's lock deadlocked with it
     * (two of Kotlin for Forge's mods constructed in parallel, 2026-10-24).
     */
    @Test
    void theFallbackIsAskedWithoutThisLoadersLock() throws Exception {
        ClassTransformExecutor executor =
                new ClassTransformExecutor(new TransformPlan(Map.of(), List.of()), Optional.empty(), entry -> {});
        String name = "kotlin.jvm.internal.Reflection";
        CountDownLatch firstHoldsFallbackLock = new CountDownLatch(1);
        CountDownLatch secondReachedFallback = new CountDownLatch(1);
        Object fallbackLock = new Object();
        ThreadLocal<Boolean> insideFallback = ThreadLocal.withInitial(() -> false);
        try (GameClassLoader loader = new GameClassLoader(List.of(), getClass().getClassLoader(), executor)) {
            // As FML's plugin loader: it takes its own lock for the class, then asks its parent, the game class loader.
            ClassLoader fallback = new ClassLoader(loader) {
                @Override
                protected Class<?> loadClass(String className, boolean resolve) throws ClassNotFoundException {
                    if (insideFallback.get()) {
                        throw new ClassNotFoundException(className);
                    }
                    boolean first = firstHoldsFallbackLock.getCount() > 0;
                    if (!first) {
                        secondReachedFallback.countDown();
                    }
                    synchronized (fallbackLock) {
                        insideFallback.set(true);
                        try {
                            if (first) {
                                firstHoldsFallbackLock.countDown();
                                secondReachedFallback.await(5, TimeUnit.SECONDS);
                                try {
                                    getParent().loadClass(className);
                                } catch (ClassNotFoundException expected) {
                                    // Neither has it; what matters is that the call returns.
                                }
                            }
                            throw new ClassNotFoundException(className);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new ClassNotFoundException(className, e);
                        } finally {
                            insideFallback.set(false);
                        }
                    }
                }
            };
            loader.addFallback(fallback);
            Thread first = Thread.ofPlatform().daemon().start(() -> {
                try {
                    fallback.loadClass(name);
                } catch (ClassNotFoundException expected) {
                    // as above
                }
            });
            firstHoldsFallbackLock.await(5, TimeUnit.SECONDS);
            Thread second = Thread.ofPlatform().daemon().start(() -> {
                try {
                    loader.loadClass(name);
                } catch (ClassNotFoundException expected) {
                    // as above
                }
            });

            first.join(Duration.ofSeconds(10));
            second.join(Duration.ofSeconds(10));
            assertEquals(false, first.isAlive() || second.isAlive(), "the two class lookups deadlocked");
        }
    }

    private Path jar(String name, Map<String, byte[]> entries) throws IOException {
        Path jar = directory.resolve(name);
        try (OutputStream file = Files.newOutputStream(jar);
                JarOutputStream out = new JarOutputStream(file)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                out.putNextEntry(new JarEntry(entry.getKey()));
                out.write(entry.getValue());
                out.closeEntry();
            }
        }
        return jar;
    }
}
