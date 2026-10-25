package dev.polymodloader.devtools.gradle.minecraft;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalMinecraftFilesTest {

    private static final Logger LOGGER = Logging.getLogger(LocalMinecraftFilesTest.class);
    private static final String VERSION = "99.1";

    @TempDir
    Path local;

    @TempDir
    Path cache;

    @Test
    void matchingClientJarIsCopiedIntoTheCache() throws IOException {
        byte[] jar = "client jar".getBytes(StandardCharsets.UTF_8);
        write(VERSION + "/" + VERSION + ".jar", jar);
        Path target = cache.resolve("versions/" + VERSION + "/client-" + VERSION + ".jar");

        assertTrue(files().seedClientJar(VERSION, sha1(jar), target));

        assertArrayEquals(jar, Files.readAllBytes(target));
    }

    @Test
    void clientJarAliasAndLibrariesAreAccepted() throws IOException {
        byte[] jar = "client jar".getBytes(StandardCharsets.UTF_8);
        byte[] library = "library".getBytes(StandardCharsets.UTF_8);
        write(VERSION + "/client.jar", jar);
        write(VERSION + "/libraries/com/example/lib/1.0/lib-1.0.jar", library);
        Path clientTarget = cache.resolve("client.jar");
        Path libraryTarget = cache.resolve("libraries/com/example/lib/1.0/lib-1.0.jar");

        assertTrue(files().seedClientJar(VERSION, sha1(jar), clientTarget));
        assertTrue(files().seedLibrary(VERSION, "com/example/lib/1.0/lib-1.0.jar", sha1(library), libraryTarget));

        assertArrayEquals(jar, Files.readAllBytes(clientTarget));
        assertArrayEquals(library, Files.readAllBytes(libraryTarget));
    }

    @Test
    void mismatchingJarFailsAndNamesFileAndBothHashes() throws IOException {
        byte[] jar = "damaged".getBytes(StandardCharsets.UTF_8);
        write(VERSION + "/server.jar", jar);
        String expected = sha1("the real server".getBytes(StandardCharsets.UTF_8));
        Path target = cache.resolve("server-bundler.jar");

        IllegalStateException failure = assertThrows(
                IllegalStateException.class, () -> files().seedServerBundler(VERSION, expected, target));

        String message = failure.getMessage();
        assertTrue(message.contains(local.resolve(VERSION).resolve("server.jar").toString()), message);
        assertTrue(message.contains("downloads.server.sha1 is " + expected), message);
        assertTrue(message.contains("SHA-1 is " + sha1(jar)), message);
        assertTrue(message.contains("delete the file to download it"), message);
        assertFalse(Files.exists(target));
    }

    @Test
    void missingFilesFallBackToDownloading() throws IOException {
        Files.createDirectories(local.resolve(VERSION));
        Path target = cache.resolve("client.jar");

        assertFalse(files().seedClientJar(VERSION, "00", target));
        assertFalse(files().seedServerBundler("98.0", "00", target));
        assertFalse(LocalMinecraftFiles.at(null, LOGGER).seedClientJar(VERSION, "00", target));
        assertTrue(files().versionJson(VERSION).isEmpty());
        assertFalse(Files.exists(target));
    }

    @Test
    void foreignOrModdedVersionJsonIsRejected() throws IOException {
        Path wrongId = write("a.json", "{\"id\":\"98.0\"}".getBytes(StandardCharsets.UTF_8));
        Path modded = write("b.json", ("{\"id\":\"" + VERSION + "\",\"inheritsFrom\":\"98.0\"}")
                .getBytes(StandardCharsets.UTF_8));

        String idMessage = assertThrows(
                        IllegalStateException.class,
                        () -> MinecraftDistributionInstaller.checkLocalVersionJson(wrongId, VERSION))
                .getMessage();
        String moddedMessage = assertThrows(
                        IllegalStateException.class,
                        () -> MinecraftDistributionInstaller.checkLocalVersionJson(modded, VERSION))
                .getMessage();

        assertTrue(idMessage.contains("expected \"" + VERSION + "\""), idMessage);
        assertTrue(moddedMessage.contains("modded launcher profile"), moddedMessage);
    }

    @Test
    void pre113VersionJsonFailsClearly() {
        String message = assertThrows(
                        IllegalStateException.class,
                        () -> MinecraftDistributionInstaller.checkClientLaunchFormat(
                                JsonParser.parseString("{\"minecraftArguments\":\"--username x\"}")
                                        .getAsJsonObject(),
                                "1.12.2"))
                .getMessage();

        assertTrue(message.contains("pre-1.13 'minecraftArguments'"), message);
    }

    @Test
    void describeSaysWhichFilesWereFound() throws IOException {
        write(VERSION + "/" + VERSION + ".json", "{}".getBytes(StandardCharsets.UTF_8));
        write(VERSION + "/client.jar", new byte[0]);

        String found = files().describe(VERSION);

        assertTrue(found.contains(VERSION + ".json found, client jar found, server.jar missing"), found);
        assertTrue(files().describe("98.0").startsWith("No local Minecraft files for 98.0"));
    }

    /** The whole server install runs offline when the version JSON and server.jar are local. */
    @Test
    void serverInstallUsesLocalVersionJsonAndBundler() throws IOException {
        byte[] serverJar = "server classes".getBytes(StandardCharsets.UTF_8);
        byte[] bundler = bundler(serverJar);
        write(VERSION + "/server.jar", bundler);
        write(
                VERSION + "/" + VERSION + ".json",
                ("{\"id\":\"" + VERSION + "\",\"downloads\":{\"server\":{\"sha1\":\"" + sha1(bundler)
                                + "\",\"url\":\"https://invalid.example/server.jar\"}}}")
                        .getBytes(StandardCharsets.UTF_8));

        MinecraftLaunchSpec spec =
                new MinecraftDistributionInstaller(cache, files(), LOGGER).installServer(VERSION);

        assertArrayEquals(serverJar, Files.readAllBytes(Path.of(spec.gameJar())));
        assertTrue(Path.of(spec.gameJar()).startsWith(cache));
        assertTrue(Files.isRegularFile(cache.resolve("versions/" + VERSION + "/" + VERSION + ".json")));
        assertEquals(0, spec.libraries().size());
    }

    private LocalMinecraftFiles files() {
        return LocalMinecraftFiles.at(local, LOGGER);
    }

    private Path write(String relative, byte[] content) throws IOException {
        Path file = local.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.write(file, content);
        return file;
    }

    private static byte[] bundler(byte[] serverJar) throws IOException {
        Path jar = Files.createTempFile("bundler", ".jar");
        try {
            try (OutputStream output = Files.newOutputStream(jar);
                    JarOutputStream bundler = new JarOutputStream(output)) {
                entry(bundler, "META-INF/versions.list", (sha256(serverJar) + "\t" + VERSION + "\t" + VERSION
                                + "/server-" + VERSION + ".jar\n")
                        .getBytes(StandardCharsets.UTF_8));
                entry(bundler, "META-INF/libraries.list", new byte[0]);
                entry(bundler, "META-INF/versions/" + VERSION + "/server-" + VERSION + ".jar", serverJar);
            }
            return Files.readAllBytes(jar);
        } finally {
            Files.delete(jar);
        }
    }

    private static void entry(JarOutputStream jar, String name, byte[] content) throws IOException {
        jar.putNextEntry(new ZipEntry(name));
        jar.write(content);
        jar.closeEntry();
    }

    private static String sha1(byte[] content) {
        return digest("SHA-1", content);
    }

    private static String sha256(byte[] content) {
        return digest("SHA-256", content);
    }

    private static String digest(String algorithm, byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
