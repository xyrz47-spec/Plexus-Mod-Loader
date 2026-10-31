package dev.plexusmodloader.runtime.classloading;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * Minecraft's libraries that the game class loader loads itself when mods of another loader are installed (ADR-0020),
 * as Fabric Loader's Knot loads them: such mods may change library classes (Fabric API's mixins change
 * DataFixerUpper's {@code TaggedChoice}), and a class can only be changed by the loader that defines it. Each library is
 * taken whole, so its classes never split between two loaders.
 *
 * <p>Left on Plexus Mod Loader's class path: the game jar, Plexus Mod Loader's own jars, and libraries Plexus Mod Loader itself uses
 * ({@link #SHARED_PACKAGES}), which game and Plexus Mod Loader must share as one copy.
 */
public final class GameLibraries {

    /** Packages Plexus Mod Loader's own code uses; a jar holding any of them stays on Plexus Mod Loader's class path. */
    static final List<String> SHARED_PACKAGES = List.of(
            "dev/plexusmodloader/",
            "com/google/gson/",
            "org/slf4j/",
            "org/apache/logging/log4j/",
            // Mojang's log4j plugins: log4j (shared) creates them, and the game reads their static queues.
            "com/mojang/logging/",
            "org/objectweb/asm/",
            "org/spongepowered/",
            "com/llamalad7/mixinextras/",
            "org/tomlj/",
            "net/fabricmc/");

    private GameLibraries() {}

    /**
     * The jars on {@code classPath} the game class loader should own.
     *
     * @param classPath the JVM class path, entries separated by {@link File#pathSeparator}
     * @param gameJar the Minecraft jar, never a library
     */
    public static List<Path> fromClassPath(String classPath, Path gameJar) {
        List<Path> libraries = new ArrayList<>();
        for (String entry : classPath.split(File.pathSeparator)) {
            if (entry.isBlank() || !entry.endsWith(".jar")) {
                continue;
            }
            add(
                    Path.of(entry).toAbsolutePath().normalize(),
                    gameJar.toAbsolutePath().normalize(),
                    libraries,
                    0);
        }
        return List.copyOf(libraries);
    }

    private static void add(Path jar, Path gameJar, List<Path> libraries, int depth) {
        if (!Files.isRegularFile(jar) || jar.equals(gameJar) || libraries.contains(jar)) {
            return;
        }
        List<Path> standsFor = classPathOfManifestOnlyJar(jar);
        if (!standsFor.isEmpty()) {
            // A jar with no classes whose Class-Path names the real entries, as Gradle starts a JVM whose class path is
            // too long for Windows: those entries are the class path. Taking the jar itself would bring every entry,
            // the vanilla game jar included, into the game class loader through its Class-Path.
            if (depth < 4) {
                standsFor.forEach(next -> add(next, gameJar, libraries, depth + 1));
            }
            return;
        }
        if (!holdsShared(jar)) {
            libraries.add(jar);
        }
    }

    /** The Class-Path entries of {@code jar} when it holds no classes, resolved as the JVM resolves them; else empty. */
    private static List<Path> classPathOfManifestOnlyJar(Path jar) {
        try (JarFile file = new JarFile(jar.toFile())) {
            Manifest manifest = file.getManifest();
            String classPath =
                    manifest == null ? null : manifest.getMainAttributes().getValue(Attributes.Name.CLASS_PATH);
            if (classPath == null
                    || classPath.isBlank()
                    || file.stream().anyMatch(entry -> entry.getName().endsWith(".class"))) {
                return List.of();
            }
            URI base = jar.toUri();
            List<Path> entries = new ArrayList<>();
            for (String item : classPath.trim().split("\\s+")) {
                try {
                    URI resolved = base.resolve(item);
                    if ("file".equalsIgnoreCase(resolved.getScheme())) {
                        entries.add(Path.of(resolved).toAbsolutePath().normalize());
                    }
                } catch (IllegalArgumentException e) {
                    // An entry the JVM could not use either.
                }
            }
            return entries;
        } catch (IOException e) {
            return List.of();
        }
    }

    private static boolean holdsShared(Path jar) {
        try (JarFile file = new JarFile(jar.toFile())) {
            return file.stream()
                    .map(JarEntry::getName)
                    .anyMatch(name -> SHARED_PACKAGES.stream().anyMatch(name::startsWith));
        } catch (IOException e) {
            // A jar Plexus Mod Loader cannot read is left where the JVM put it.
            return true;
        }
    }
}
