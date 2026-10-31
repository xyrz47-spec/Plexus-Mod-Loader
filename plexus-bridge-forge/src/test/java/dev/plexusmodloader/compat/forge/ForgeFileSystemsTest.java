package dev.plexusmodloader.compat.forge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.plexusmodloader.compat.bridge.SystemClassPath;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ForgeFileSystemsTest {

    /** The libraries of Forge 26.2-65.1.5's {@code version.json} that matter here, in the installer's order. */
    private static final String VERSION_JSON = """
            {"libraries": [
              {"name": "net.minecraftforge:forge:26.2-65.1.5:universal",
               "downloads": {"artifact": {"path": "net/minecraftforge/forge/26.2-65.1.5/forge-26.2-65.1.5-universal.jar"}}},
              {"name": "net.minecraftforge:JarJarFileSystems:0.4.2",
               "downloads": {"artifact": {"path": "net/minecraftforge/JarJarFileSystems/0.4.2/JarJarFileSystems-0.4.2.jar"}}},
              {"name": "net.minecraftforge:roimfs:1.0.0",
               "downloads": {"artifact": {"path": "net/minecraftforge/roimfs/1.0.0/roimfs-1.0.0.jar"}}},
              {"name": "net.minecraftforge:securemodules:2.2.24",
               "downloads": {"artifact": {"path": "net/minecraftforge/securemodules/2.2.24/securemodules-2.2.24.jar"}}},
              {"name": "net.minecraftforge:unsafe:0.9.2",
               "downloads": {"artifact": {"path": "net/minecraftforge/unsafe/0.9.2/unsafe-0.9.2.jar"}}},
              {"name": "net.minecraftforge:JarJarSelector:0.4.2",
               "downloads": {"artifact": {"path": "net/minecraftforge/JarJarSelector/0.4.2/JarJarSelector-0.4.2.jar"}}}
            ]}
            """;

    @TempDir
    Path directory;

    @Test
    void picksForgesFileSystemJarsFromTheInstallersVersionJson() throws IOException {
        Path libraries = directory.resolve("libraries");

        List<Path> jars = ForgeInstallation.fileSystemJars(json(VERSION_JSON), libraries);

        assertEquals(
                List.of(
                        libraries.resolve("net/minecraftforge/securemodules/2.2.24/securemodules-2.2.24.jar"),
                        libraries.resolve("net/minecraftforge/unsafe/0.9.2/unsafe-0.9.2.jar"),
                        libraries.resolve("net/minecraftforge/JarJarFileSystems/0.4.2/JarJarFileSystems-0.4.2.jar"),
                        libraries.resolve("net/minecraftforge/roimfs/1.0.0/roimfs-1.0.0.jar")),
                jars);
    }

    @Test
    void aVersionJsonWithoutOneOfThemIsAnError() {
        JsonObject version =
                json(VERSION_JSON.replace("net.minecraftforge:roimfs:1.0.0", "net.minecraftforge:other:1"));

        IOException problem = assertThrows(
                IOException.class, () -> ForgeInstallation.fileSystemJars(version, directory.resolve("libraries")));

        assertTrue(problem.getMessage().contains("net.minecraftforge:roimfs"), problem.getMessage());
    }

    /** Without them on the class path and without Plexus Mod Loader's agent, nothing is downloaded. */
    @Test
    void refusesWithoutTheAgentBeforeAnyDownload() throws IOException {
        try (URLClassLoader system = new URLClassLoader(new URL[0], null)) {
            ForgeFileSystems fileSystems = new ForgeFileSystems(system, Optional.empty(), Set::of);

            IOException problem = assertThrows(IOException.class, fileSystems::checkAvailable);

            assertTrue(problem.getMessage().contains("without its Java agent"), problem.getMessage());
            assertTrue(problem.getMessage().contains("java -jar plexusmodloader-server.jar"), problem.getMessage());
            assertTrue(problem.getMessage().contains("install the profile again"), problem.getMessage());
        }
    }

    @Test
    void appendsTheJarsAndChecksThatJavaListsTheirFileSystems() throws IOException {
        Path union = providerJar("securemodules.jar");
        Path other = directory.resolve("unsafe.jar");
        Files.copy(union, other);
        try (AppendableLoader system = new AppendableLoader()) {
            List<Path> appended = new ArrayList<>();
            SystemClassPath classPath = jar -> {
                appended.add(jar);
                system.append(jar);
            };
            ForgeFileSystems fileSystems = new ForgeFileSystems(
                    system, Optional.of(classPath), () -> Set.of("file", "jar", "union", "jij", "path", "roimfs"));

            fileSystems.checkAvailable();
            List<String> log = new ArrayList<>();
            fileSystems.install(List.of(union, other), log::add);

            assertEquals(List.of(union, other), appended);
            assertTrue(log.get(0).contains("JVM class path"), log.toString());
        }
    }

    /** The trap: Java lists its file systems once; jars appended later add classes but no file system. */
    @Test
    void reportsThatTheJvmListedItsFileSystemsBeforeTheJarsWereAppended() throws IOException {
        Path union = providerJar("securemodules.jar");
        try (AppendableLoader system = new AppendableLoader()) {
            ForgeFileSystems fileSystems =
                    new ForgeFileSystems(system, Optional.of(system::append), () -> Set.of("file", "jar"));

            IOException problem =
                    assertThrows(IOException.class, () -> fileSystems.install(List.of(union), line -> {}));

            assertTrue(problem.getMessage().contains("listed its file systems before"), problem.getMessage());
            assertTrue(problem.getMessage().contains("union"), problem.getMessage());
        }
    }

    @Test
    void reportsAnAppendThatHadNoEffect() throws IOException {
        Path union = providerJar("securemodules.jar");
        try (URLClassLoader system = new URLClassLoader(new URL[0], null)) {
            ForgeFileSystems fileSystems =
                    new ForgeFileSystems(system, Optional.of(jar -> {}), () -> Set.of("file", "jar"));

            IOException problem =
                    assertThrows(IOException.class, () -> fileSystems.install(List.of(union), line -> {}));

            assertTrue(problem.getMessage().contains("still does not see"), problem.getMessage());
        }
    }

    /** Development runs put them on the class path from the start; nothing is appended then. */
    @Test
    void leavesAClassPathThatAlreadyHasThemAlone() throws IOException {
        Path union = providerJar("securemodules.jar");
        try (URLClassLoader system = new URLClassLoader(new URL[] {union.toUri().toURL()}, null)) {
            ForgeFileSystems fileSystems = new ForgeFileSystems(
                    system,
                    Optional.of(jar -> {
                        throw new AssertionError("nothing to append");
                    }),
                    () -> Set.of("union", "jij", "path", "roimfs"));

            fileSystems.checkAvailable();
            List<String> log = new ArrayList<>();
            fileSystems.install(List.of(union), log::add);

            assertEquals(List.of(), log);
        }
    }

    /** A jar with the class Plexus Mod Loader looks for: securemodules' union file system provider. */
    private Path providerJar(String name) throws IOException {
        Path jar = directory.resolve(name);
        try (OutputStream file = Files.newOutputStream(jar);
                JarOutputStream out = new JarOutputStream(file)) {
            out.putNextEntry(new JarEntry(ForgeFileSystems.UNION_PROVIDER_CLASS));
            out.closeEntry();
        }
        return jar;
    }

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    /** A system class loader stand-in that can take jars, as the JDK's does for an agent. */
    private static final class AppendableLoader extends URLClassLoader {
        AppendableLoader() {
            super(new URL[0], null);
        }

        void append(Path jar) throws IOException {
            addURL(jar.toUri().toURL());
        }
    }
}
