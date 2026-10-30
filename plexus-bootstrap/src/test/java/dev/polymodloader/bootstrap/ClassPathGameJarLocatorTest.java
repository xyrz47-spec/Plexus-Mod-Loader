package dev.polymodloader.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClassPathGameJarLocatorTest {

    @TempDir
    Path directory;

    @Test
    void findsTheOneJarWithVersionJson() throws IOException {
        Path library = jar("library.jar", "com/example/Library.class");
        Path game = jar("26.2.jar", "version.json");
        Path classes = Files.createDirectories(directory.resolve("classes"));

        Path found =
                ClassPathGameJarLocator.locate(classPath(library, classes, game, directory.resolve("missing.jar")));

        assertEquals(game, found);
    }

    /** {@code java -jar polymodloader-server.jar}: the class path is the launch jar, whose manifest lists the rest. */
    @Test
    void followsTheManifestClassPathOfALaunchJar() throws IOException {
        Files.createDirectories(directory.resolve("versions/26.2"));
        Path game = jar("versions/26.2/server-26.2.jar", "version.json");
        jar("library.jar", "com/example/Library.class");
        Path launch = directory.resolve("polymodloader-server.jar");
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.CLASS_PATH, "library.jar versions/26.2/server-26.2.jar");
        try (OutputStream file = Files.newOutputStream(launch);
                JarOutputStream out = new JarOutputStream(file, manifest)) {
            out.putNextEntry(new ZipEntry("dev/polymodloader/Empty.class"));
        }

        assertEquals(game, ClassPathGameJarLocator.locate(classPath(launch)));
    }

    @Test
    void rejectsAClassPathWithoutGameJar() throws IOException {
        Path library = jar("library.jar", "com/example/Library.class");
        Path broken = Files.writeString(directory.resolve("broken.jar"), "not a zip file");

        IllegalArgumentException problem = assertThrows(
                IllegalArgumentException.class, () -> ClassPathGameJarLocator.locate(classPath(library, broken)));

        assertTrue(problem.getMessage().startsWith("PLEXUS-BOOTSTRAP-006"), problem.getMessage());
        assertTrue(problem.getMessage().contains("no jar on the class path"), problem.getMessage());
    }

    @Test
    void rejectsTwoGameJars() throws IOException {
        Path first = jar("26.2.jar", "version.json");
        Path second = jar("other.jar", "version.json");

        IllegalArgumentException problem = assertThrows(
                IllegalArgumentException.class, () -> ClassPathGameJarLocator.locate(classPath(first, second)));

        assertTrue(problem.getMessage().contains("2 jars on the class path"), problem.getMessage());
    }

    private Path jar(String name, String entry) throws IOException {
        Path jar = directory.resolve(name);
        try (OutputStream file = Files.newOutputStream(jar);
                ZipOutputStream zip = new ZipOutputStream(file)) {
            zip.putNextEntry(new ZipEntry(entry));
            zip.write("{}".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return jar;
    }

    private static String classPath(Path... entries) {
        StringBuilder text = new StringBuilder();
        for (Path entry : entries) {
            if (!text.isEmpty()) {
                text.append(File.pathSeparator);
            }
            text.append(entry);
        }
        return text.toString();
    }
}
