package dev.polymodloader.runtime.classloading;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GameLibrariesTest {

    @TempDir
    Path folder;

    @Test
    void librariesAreTakenAndTheGameJarAndSharedJarsLeftOnTheClassPath() throws IOException {
        Path library = jar("datafixerupper.jar", Map.of("com/mojang/datafixers/DSL.class", ""), null);
        Path poly = jar("poly-runtime.jar", Map.of("dev/polymodloader/X.class", ""), null);
        Path game = jar("server-26.2.jar", Map.of("net/minecraft/server/Main.class", ""), null);

        assertEquals(List.of(library), GameLibraries.fromClassPath(classPath(library, poly, game), game));
    }

    /**
     * Gradle starts a JVM whose class path is too long for Windows with one manifest-only jar whose Class-Path names the
     * real entries. Taking that jar would put the whole class path, the vanilla game jar included, into the game class
     * loader, where FML then found the vanilla game instead of Forge's patched one (Quilt corpus with Forge, 2026-10-24).
     * Its entries are taken as if they were on the class path themselves, as the JVM does.
     */
    @Test
    void aManifestOnlyClassPathJarStandsForItsEntries() throws IOException {
        Path library = jar("libs/datafixerupper.jar", Map.of("com/mojang/datafixers/DSL.class", ""), null);
        Path poly = jar("libs/poly-runtime.jar", Map.of("dev/polymodloader/X.class", ""), null);
        Path game = jar("versions/server-26.2.jar", Map.of("net/minecraft/server/Main.class", ""), null);
        Path pathing = jar(
                "tmp/gradle-javaexec-classpath123.jar",
                Map.of(),
                library.toUri() + " " + poly.toUri() + " " + game.toUri());

        assertEquals(List.of(library), GameLibraries.fromClassPath(classPath(pathing), game));
    }

    @Test
    void aJarWithClassesKeepsItsOwnClassPathEntriesOutOfTheGameLoader() throws IOException {
        // An installed server's polymodloader-server.jar: Poly Mod Loader's classes and a Class-Path naming the
        // libraries; it stays on the class path, and its Class-Path is not followed (as before).
        Path library = jar("libraries/datafixerupper.jar", Map.of("com/mojang/datafixers/DSL.class", ""), null);
        Path server = jar(
                "polymodloader-server.jar",
                Map.of("dev/polymodloader/bootstrap/Main.class", ""),
                "libraries/datafixerupper.jar");
        Path game = jar("server-26.2.jar", Map.of("net/minecraft/server/Main.class", ""), null);

        assertEquals(List.of(), GameLibraries.fromClassPath(classPath(server), game));
        assertEquals(true, Files.exists(library));
    }

    private static String classPath(Path... jars) {
        return String.join(
                File.pathSeparator,
                java.util.Arrays.stream(jars).map(Path::toString).toList());
    }

    private Path jar(String name, Map<String, String> entries, String manifestClassPath) throws IOException {
        Path jar = folder.resolve(name);
        Files.createDirectories(jar.getParent());
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        if (manifestClassPath != null) {
            manifest.getMainAttributes().put(Attributes.Name.CLASS_PATH, manifestClassPath);
        }
        try (OutputStream out = Files.newOutputStream(jar);
                JarOutputStream jarOut = new JarOutputStream(out, manifest)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                jarOut.putNextEntry(new JarEntry(entry.getKey()));
                jarOut.write(entry.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                jarOut.closeEntry();
            }
        }
        return jar;
    }
}
