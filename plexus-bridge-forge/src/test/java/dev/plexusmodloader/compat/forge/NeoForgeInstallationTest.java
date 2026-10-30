package dev.plexusmodloader.compat.forge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * ADR-0026: NeoForge's own server starts with the libraries its installer writes into the server's class path ({@code
 * data/win_args.txt}), which include libraries of {@code install_profile.json} that {@code version.json} does not list:
 * LWJGL's core and tinyfd for NeoForge 26.1.x (ImmediatelyFast's 26.1 build reads its configuration with LWJGL's {@code
 * MathUtil} on the server), tinyfd for 26.2.
 */
class NeoForgeInstallationTest {

    @Test
    void theServerAlsoGetsTheInstallProfileLibrariesOnNeoForgesServerClassPath(@TempDir Path directory)
            throws IOException {
        Path installer = installer(directory.resolve("installer.jar"));

        try (JarFile jar = new JarFile(installer.toFile())) {
            List<String> names = NeoForgeInstallation.serverClassPathLibraries(jar).stream()
                    .map(library -> library.get("name").getAsString())
                    .toList();

            assertEquals(List.of("org.lwjgl:lwjgl-tinyfd:3.4.1", "org.lwjgl:lwjgl:3.4.1"), names);
        }
    }

    @Test
    void anInstallerWithoutServerArgumentsAddsNothing(@TempDir Path directory) throws IOException {
        Path installer = directory.resolve("installer.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(installer))) {
            put(out, "install_profile.json", "{\"libraries\": []}");
        }

        try (JarFile jar = new JarFile(installer.toFile())) {
            assertEquals(List.of(), NeoForgeInstallation.serverClassPathLibraries(jar));
        }
    }

    /**
     * An installer like NeoForge 26.1.2.122's: {@code version.json} lists FML's loader; {@code install_profile.json}
     * lists the universal jar, an installer tool, tinyfd and LWJGL; the server's class path names FML's loader, tinyfd,
     * LWJGL and a library of Minecraft's own server.
     */
    private static Path installer(Path file) throws IOException {
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(file))) {
            put(
                    out,
                    "version.json",
                    "{\"libraries\": ["
                            + library(
                                    "net.neoforged.fancymodloader:loader:11.0.15",
                                    "net/neoforged/fancymodloader/loader/11.0.15/loader-11.0.15.jar")
                            + "]}");
            put(
                    out,
                    "install_profile.json",
                    "{\"libraries\": ["
                            + library(
                                    "net.neoforged:neoforge:26.1.2.122:universal",
                                    "net/neoforged/neoforge/26.1.2.122/neoforge-26.1.2.122-universal.jar")
                            + ", "
                            + library(
                                    "net.neoforged.installertools:installertools:4.0.12",
                                    "net/neoforged/installertools/installertools/4.0.12/installertools-4.0.12.jar")
                            + ", "
                            + library(
                                    "org.lwjgl:lwjgl-tinyfd:3.4.1",
                                    "org/lwjgl/lwjgl-tinyfd/3.4.1/lwjgl-tinyfd-3.4.1.jar")
                            + ", " + library("org.lwjgl:lwjgl:3.4.1", "org/lwjgl/lwjgl/3.4.1/lwjgl-3.4.1.jar")
                            + "]}");
            put(
                    out,
                    "data/win_args.txt",
                    String.join(
                            "\n",
                            "--add-opens java.base/java.lang.invoke=ALL-UNNAMED",
                            "-DlibraryDirectory=libraries",
                            "-classpath",
                            String.join(
                                    ";",
                                    "libraries/net/neoforged/fancymodloader/loader/11.0.15/loader-11.0.15.jar",
                                    "libraries/org/lwjgl/lwjgl-tinyfd/3.4.1/lwjgl-tinyfd-3.4.1.jar",
                                    "libraries/org/lwjgl/lwjgl/3.4.1/lwjgl-3.4.1.jar",
                                    "libraries/io/netty/netty-common/4.2.15.Final/netty-common-4.2.15.Final.jar"),
                            "net.neoforged.fml.startup.Server"));
        }
        return file;
    }

    private static String library(String name, String path) {
        JsonObject artifact = new JsonObject();
        artifact.addProperty("path", path);
        artifact.addProperty("sha1", "0".repeat(40));
        artifact.addProperty("url", "https://maven.neoforged.net/releases/" + path);
        JsonObject downloads = new JsonObject();
        downloads.add("artifact", artifact);
        JsonObject library = new JsonObject();
        library.addProperty("name", name);
        library.add("downloads", downloads);
        return library.toString();
    }

    private static void put(JarOutputStream out, String name, String text) throws IOException {
        out.putNextEntry(new JarEntry(name));
        out.write(text.getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
    }
}
