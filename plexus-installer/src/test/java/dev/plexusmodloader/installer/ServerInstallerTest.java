package dev.plexusmodloader.installer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ServerInstallerTest {

    private static final byte[] RUNTIME_JAR = "runtime bytes".getBytes(StandardCharsets.UTF_8);
    private static final byte[] SERVER_JAR = "server classes".getBytes(StandardCharsets.UTF_8);
    private static final byte[] GSON_JAR = "gson classes".getBytes(StandardCharsets.UTF_8);
    private static final URI VERSION_URL = URI.create("https://piston-meta.example/v1/26.2.json");
    private static final URI SERVER_URL = URI.create("https://piston-data.example/v1/server.jar");

    @TempDir
    Path folder;

    private final Map<URI, byte[]> responses = new HashMap<>();
    private final List<URI> downloaded = new ArrayList<>();

    @Test
    void installsTheServerPolyModLoaderAndStartScripts() throws Exception {
        mojang(bundler(SERVER_JAR));
        Path server = folder.resolve("my server");

        List<String> warnings = installer().install(server, "26.2", JavaMemory.parse("4G", "1G"), line -> {});

        assertEquals(List.of(), warnings);
        assertEquals("server classes", Files.readString(server.resolve("versions/26.2/server-26.2.jar")));
        assertEquals(
                "gson classes",
                Files.readString(server.resolve("libraries/com/google/code/gson/gson/2.14.0/gson-2.14.0.jar")));
        assertEquals(
                "runtime bytes",
                Files.readString(server.resolve(
                        "libraries/dev/polymodloader/poly-runtime-impl/0.1.0/poly-runtime-impl-0.1.0.jar")));
        try (JarFile launch =
                new JarFile(server.resolve(ServerInstaller.LAUNCH_JAR).toFile())) {
            Attributes attributes = launch.getManifest().getMainAttributes();
            assertEquals(ServerInstaller.SERVER_MAIN_CLASS, attributes.getValue(Attributes.Name.MAIN_CLASS));
            // Poly Mod Loader first, then Minecraft's libraries, then the server jar Poly Mod Loader finds by its
            // version.json.
            assertEquals(
                    "libraries/dev/polymodloader/poly-runtime-impl/0.1.0/poly-runtime-impl-0.1.0.jar"
                            + " libraries/com/google/code/gson/gson/2.14.0/gson-2.14.0.jar"
                            + " versions/26.2/server-26.2.jar",
                    attributes.getValue(Attributes.Name.CLASS_PATH));
            // java -jar starts Poly Mod Loader's agent from that class path (ADR-0024); no flag, no extra jar.
            assertEquals(
                    "dev.plexusmodloader.bootstrap.SystemClassPathAgent", attributes.getValue("Launcher-Agent-Class"));
        }
        String batch = Files.readString(server.resolve("run.bat"));
        assertTrue(batch.contains("java -Xms1G -Xmx4G "), batch);
        assertTrue(batch.contains("-jar polymodloader-server.jar nogui"), batch);
        assertTrue(batch.contains("Needs Java 25 or newer"), batch);
        assertTrue(Files.readString(server.resolve("run.sh")).startsWith("#!/usr/bin/env sh\n"));
        assertTrue(Files.isDirectory(server.resolve("mods")));
        // The owner accepts Minecraft's EULA, never the installer.
        assertFalse(Files.exists(server.resolve("eula.txt")));
    }

    @Test
    void installingAgainKeepsVerifiedFilesAndTheServersOwnFiles() throws Exception {
        mojang(bundler(SERVER_JAR));
        installer().install(folder, "26.2", JavaMemory.LAUNCHER_DEFAULT, line -> {});
        Files.writeString(folder.resolve("eula.txt"), "eula=true\n");
        downloaded.clear();

        installer().install(folder, "26.2", JavaMemory.LAUNCHER_DEFAULT, line -> {});

        // Only the version list is fetched again; the version JSON and server jar still match their SHA-1.
        assertEquals(List.of(ServerInstaller.VERSION_MANIFEST), downloaded);
        assertEquals("eula=true\n", Files.readString(folder.resolve("eula.txt")));
        assertFalse(Files.readString(folder.resolve("run.bat")).contains("-Xmx"));
    }

    @Test
    void aDamagedServerDownloadStopsTheInstall() throws Exception {
        mojang(bundler(SERVER_JAR));
        responses.put(SERVER_URL, "cut off".getBytes(StandardCharsets.UTF_8));

        InstallerException problem = assertThrows(
                InstallerException.class,
                () -> installer().install(folder, "26.2", JavaMemory.LAUNCHER_DEFAULT, line -> {}));

        assertTrue(problem.getMessage().contains("is damaged"), problem.getMessage());
        assertFalse(Files.exists(folder.resolve(ServerInstaller.LAUNCH_JAR)));
        assertFalse(Files.exists(folder.resolve("versions/26.2/server-bundler-26.2.jar")));
    }

    @Test
    void unsupportedVersionsAreRefusedBeforeAnyDownload() {
        InstallerException problem = assertThrows(
                InstallerException.class,
                () -> installer().install(folder, "1.21.4", JavaMemory.LAUNCHER_DEFAULT, line -> {}));

        assertTrue(problem.getMessage().contains("does not support Minecraft 1.21.4"), problem.getMessage());
        assertEquals(List.of(), downloaded);
    }

    private ServerInstaller installer() {
        return new ServerInstaller(payload(), (uri, target) -> {
            byte[] body = responses.get(uri);
            if (body == null) {
                throw new IOException("HTTP 404 " + uri);
            }
            downloaded.add(uri);
            Files.write(target, body);
        });
    }

    /** Mojang's version list, the 26.2 version JSON and its server bundler. */
    private void mojang(byte[] bundler) {
        byte[] version = ("{\"id\": \"26.2\", \"javaVersion\": {\"majorVersion\": 25}, \"downloads\": {\"server\":"
                        + " {\"url\": \"" + SERVER_URL + "\", \"sha1\": \"" + hash(bundler, "SHA-1") + "\"}}}")
                .getBytes(StandardCharsets.UTF_8);
        byte[] manifest = ("{\"versions\": [{\"id\": \"26.1\", \"url\": \"https://piston-meta.example/v1/26.1.json\","
                        + " \"sha1\": \"0\"}, {\"id\": \"26.2\", \"url\": \"" + VERSION_URL + "\", \"sha1\": \""
                        + hash(version, "SHA-1") + "\"}]}")
                .getBytes(StandardCharsets.UTF_8);
        responses.put(ServerInstaller.VERSION_MANIFEST, manifest);
        responses.put(VERSION_URL, version);
        responses.put(SERVER_URL, bundler);
    }

    /** A server bundler as Mojang ships it: lists of {@code <sha256>\t<id>\t<path>} and the files they name. */
    private static byte[] bundler(byte[] serverJar) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            entry(zip, "META-INF/versions.list", hash(serverJar, "SHA-256") + "\t26.2\t26.2/server-26.2.jar\n");
            entry(zip, "META-INF/versions/26.2/server-26.2.jar", serverJar);
            entry(
                    zip,
                    "META-INF/libraries.list",
                    hash(GSON_JAR, "SHA-256")
                            + "\tcom.google.code.gson:gson:2.14.0\tcom/google/code/gson/gson/2.14.0/gson-2.14.0.jar\n");
            entry(zip, "META-INF/libraries/com/google/code/gson/gson/2.14.0/gson-2.14.0.jar", GSON_JAR);
        }
        return bytes.toByteArray();
    }

    private static void entry(ZipOutputStream zip, String name, String content) throws IOException {
        entry(zip, name, content.getBytes(StandardCharsets.UTF_8));
    }

    private static void entry(ZipOutputStream zip, String name, byte[] content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content);
        zip.closeEntry();
    }

    private static InstallerPayload payload() {
        return new InstallerPayload(
                "0.1.0",
                List.of("26.2"),
                "dev.plexusmodloader.bootstrap.PlexusLauncherMain",
                List.of(new InstallerPayload.Library(
                        "dev.plexusmodloader:plexus-runtime-impl:0.1.0",
                        "dev/polymodloader/poly-runtime-impl/0.1.0/poly-runtime-impl-0.1.0.jar",
                        hash(RUNTIME_JAR, "SHA-1"),
                        RUNTIME_JAR.length)),
                // The agent class lives in this test's one library.
                Optional.of(new InstallerPayload.Agent(
                        "dev.plexusmodloader:plexus-runtime-impl:0.1.0",
                        "dev.plexusmodloader.bootstrap.SystemClassPathAgent")),
                path -> new ByteArrayInputStream(RUNTIME_JAR));
    }

    private static String hash(byte[] bytes, String algorithm) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
