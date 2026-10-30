package dev.plexusmodloader.bootstrap;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * Finds the Minecraft game jar on the JVM class path when {@code poly.gameJar} is not set. The official
 * Minecraft Launcher starts a Poly Mod Loader profile (which inherits the vanilla version) with the vanilla game jar
 * on the class path instead of passing its location.
 *
 * <p>The game jar is the one class path jar with Mojang's {@code version.json} at its root, the same file
 * Poly Mod Loader reads to pick the version adapter. Poly Mod Loader's game class loader still defines every game
 * class from it (child-first), so having the jar on the class path as well changes nothing.
 *
 * <p>The class path includes what the jars' manifests add with {@code Class-Path}, as the JVM's does. A server the
 * installer set up starts with {@code java -jar polymodloader-server.jar}, whose manifest lists the server jar.
 */
final class ClassPathGameJarLocator {

    private ClassPathGameJarLocator() {}

    /**
     * @param classPath the JVM class path ({@code java.class.path})
     * @return the game jar
     * @throws IllegalArgumentException with {@code PLEXUS-BOOTSTRAP-006} when no jar or more than one jar has a
     *     {@code version.json}
     */
    static Path locate(String classPath) {
        List<Path> candidates = new ArrayList<>();
        Deque<Path> pending = new ArrayDeque<>();
        Arrays.stream(classPath.split(File.pathSeparator))
                .filter(path -> !path.isBlank())
                .map(Path::of)
                .forEach(pending::add);
        Set<Path> seen = new LinkedHashSet<>();
        while (!pending.isEmpty()) {
            Path path = pending.removeFirst().toAbsolutePath().normalize();
            if (!seen.add(path) || !Files.isRegularFile(path)) {
                continue;
            }
            try (JarFile jar = new JarFile(path.toFile())) {
                if (jar.getEntry("version.json") != null) {
                    candidates.add(path);
                }
                pending.addAll(manifestClassPath(path, jar.getManifest()));
            } catch (IOException | IllegalArgumentException e) {
                // Not a readable jar, or a Class-Path entry that is not a file: then it is not the game jar either.
            }
        }
        if (candidates.size() == 1) {
            return candidates.getFirst();
        }
        throw new IllegalArgumentException(
                candidates.isEmpty()
                        ? "PLEXUS-BOOTSTRAP-006 poly.gameJar is not set and no jar on the class path has a version.json;"
                                + " set -Dpoly.gameJar=<Minecraft jar> or reinstall the Poly Mod Loader profile"
                        : "PLEXUS-BOOTSTRAP-006 poly.gameJar is not set and " + candidates.size()
                                + " jars on the class path have a version.json " + candidates
                                + "; set -Dpoly.gameJar=<Minecraft jar>");
    }

    /** The jar's {@code Class-Path} entries: URLs relative to the jar's folder, separated by spaces. */
    private static List<Path> manifestClassPath(Path jar, Manifest manifest) {
        if (manifest == null) {
            return List.of();
        }
        String value = manifest.getMainAttributes().getValue(Attributes.Name.CLASS_PATH);
        if (value == null || value.isBlank()) {
            return List.of();
        }
        URI base = jar.toUri();
        List<Path> entries = new ArrayList<>();
        for (String entry : value.strip().split("\\s+")) {
            URI resolved = base.resolve(entry);
            if ("file".equals(resolved.getScheme())) {
                entries.add(Path.of(resolved));
            }
        }
        return entries;
    }
}
