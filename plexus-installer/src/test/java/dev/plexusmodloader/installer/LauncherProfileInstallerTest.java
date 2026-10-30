package dev.plexusmodloader.installer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LauncherProfileInstallerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-21T12:00:00Z"), ZoneOffset.UTC);
    private static final byte[] RUNTIME_JAR = "runtime bytes".getBytes(StandardCharsets.UTF_8);
    private static final byte[] ASM_JAR = "asm bytes".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path minecraft;

    @Test
    void installsLibrariesVersionAndProfileAndKeepsOtherProfiles() throws Exception {
        Files.writeString(minecraft.resolve("launcher_profiles.json"), """
                {"profiles": {"vanilla": {"name": "Latest release", "type": "latest-release"}},
                 "settings": {"locale": "ko_kr"}, "version": 3}
                """);
        List<String> progress = new ArrayList<>();

        List<String> warnings =
                installer(payload()).install(minecraft, "26.2", true, JavaMemory.LAUNCHER_DEFAULT, progress::add);

        Path runtime =
                minecraft.resolve("libraries/dev/polymodloader/poly-runtime-impl/0.1.0/poly-runtime-impl-0.1.0.jar");
        assertTrue(Files.isRegularFile(runtime));
        assertEquals(new String(RUNTIME_JAR, StandardCharsets.UTF_8), Files.readString(runtime));
        // The vanilla version was never played in this folder.
        assertEquals(1, warnings.size(), warnings.toString());

        JsonObject version = json(minecraft.resolve("versions/polymodloader-0.1.0-26.2/polymodloader-0.1.0-26.2.json"));
        assertEquals("26.2", version.get("inheritsFrom").getAsString());
        assertEquals(
                "dev.plexusmodloader.bootstrap.PolyLauncherMain",
                version.get("mainClass").getAsString());
        assertEquals(
                "-Dpoly.launchTarget=client",
                version.getAsJsonObject("arguments")
                        .getAsJsonArray("jvm")
                        .get(0)
                        .getAsString());
        JsonArray libraries = version.getAsJsonArray("libraries");
        assertEquals(2, libraries.size());
        JsonObject artifact =
                libraries.get(0).getAsJsonObject().getAsJsonObject("downloads").getAsJsonObject("artifact");
        assertEquals(
                "dev/polymodloader/poly-runtime-impl/0.1.0/poly-runtime-impl-0.1.0.jar",
                artifact.get("path").getAsString());
        assertEquals(sha1(RUNTIME_JAR), artifact.get("sha1").getAsString());
        assertEquals("", artifact.get("url").getAsString());
        try (JarFile placeholder = new JarFile(minecraft
                .resolve("versions/polymodloader-0.1.0-26.2/polymodloader-0.1.0-26.2.jar")
                .toFile())) {
            assertTrue(placeholder.getManifest() != null);
        }

        JsonObject profiles = json(minecraft.resolve("launcher_profiles.json"));
        assertEquals("ko_kr", profiles.getAsJsonObject("settings").get("locale").getAsString());
        assertEquals(3, profiles.get("version").getAsInt());
        JsonObject all = profiles.getAsJsonObject("profiles");
        assertEquals(
                "Latest release", all.getAsJsonObject("vanilla").get("name").getAsString());
        JsonObject poly = all.getAsJsonObject("polymodloader-26.2");
        assertEquals("Poly Mod Loader 26.2", poly.get("name").getAsString());
        assertEquals("custom", poly.get("type").getAsString());
        assertEquals("polymodloader-0.1.0-26.2", poly.get("lastVersionId").getAsString());
        assertEquals("2026-10-21T12:00:00Z", poly.get("created").getAsString());
        assertTrue(poly.get("icon").getAsString().startsWith("data:image/png;base64,"));
        assertTrue(Files.isRegularFile(minecraft.resolve("launcher_profiles.json.polymodloader-backup")));
        assertTrue(Files.isDirectory(minecraft.resolve("mods")));
    }

    @Test
    void installingAgainKeepsTheProfileAndSkipsUnchangedLibraries() throws Exception {
        Files.writeString(minecraft.resolve("launcher_profiles.json"), "{\"profiles\": {}}");
        installer(payload()).install(minecraft, "26.2", true, JavaMemory.LAUNCHER_DEFAULT, line -> {});
        JsonObject first = json(minecraft.resolve("launcher_profiles.json"));
        List<String> progress = new ArrayList<>();

        installer(payload()).install(minecraft, "26.2", true, JavaMemory.LAUNCHER_DEFAULT, progress::add);

        assertFalse(progress.stream().anyMatch(line -> line.startsWith("Installed library")), progress.toString());
        assertEquals(
                first.getAsJsonObject("profiles").keySet(),
                json(minecraft.resolve("launcher_profiles.json"))
                        .getAsJsonObject("profiles")
                        .keySet());
    }

    @Test
    void updatesTheMicrosoftStoreLauncherFileToo() throws Exception {
        Files.writeString(minecraft.resolve("launcher_profiles.json"), "{\"profiles\": {}}");
        Files.writeString(minecraft.resolve("launcher_profiles_microsoft_store.json"), "{\"profiles\": {}}");

        installer(payload()).install(minecraft, "26.2", true, JavaMemory.LAUNCHER_DEFAULT, line -> {});

        assertTrue(json(minecraft.resolve("launcher_profiles_microsoft_store.json"))
                .getAsJsonObject("profiles")
                .has("polymodloader-26.2"));
    }

    @Test
    void replacesADamagedLibrary() throws Exception {
        Path runtime =
                minecraft.resolve("libraries/dev/polymodloader/poly-runtime-impl/0.1.0/poly-runtime-impl-0.1.0.jar");
        Files.createDirectories(runtime.getParent());
        Files.writeString(runtime, "half a download");

        installer(payload()).install(minecraft, "26.2", false, JavaMemory.LAUNCHER_DEFAULT, line -> {});

        assertEquals(sha1(RUNTIME_JAR), LauncherProfileInstaller.sha1(runtime));
    }

    @Test
    void refusesAPayloadWhoseJarDoesNotMatchItsChecksum() {
        InstallerPayload damaged = new InstallerPayload(
                "0.1.0",
                List.of("26.2"),
                "dev.plexusmodloader.bootstrap.PolyLauncherMain",
                List.of(new InstallerPayload.Library(
                        "dev.plexusmodloader:poly-runtime-impl:0.1.0",
                        "dev/polymodloader/poly-runtime-impl/0.1.0/poly-runtime-impl-0.1.0.jar",
                        sha1("something else".getBytes(StandardCharsets.UTF_8)),
                        RUNTIME_JAR.length)),
                path -> new ByteArrayInputStream(RUNTIME_JAR));

        InstallerException problem = assertThrows(
                InstallerException.class,
                () -> installer(damaged).install(minecraft, "26.2", false, JavaMemory.LAUNCHER_DEFAULT, line -> {}));

        assertTrue(problem.getMessage().contains("is damaged"), problem.getMessage());
        assertFalse(Files.exists(
                minecraft.resolve("libraries/dev/polymodloader/poly-runtime-impl/0.1.0/poly-runtime-impl-0.1.0.jar")));
    }

    @Test
    void refusesAnUnsupportedVersionAMissingFolderAndBrokenProfiles() throws IOException {
        LauncherProfileInstaller installer = installer(payload());

        InstallerException version = assertThrows(
                InstallerException.class,
                () -> installer.install(minecraft, "1.21.11", true, JavaMemory.LAUNCHER_DEFAULT, line -> {}));
        assertTrue(version.getMessage().contains("does not support Minecraft 1.21.11"), version.getMessage());

        InstallerException folder = assertThrows(
                InstallerException.class,
                () -> installer.install(
                        minecraft.resolve("missing"), "26.2", true, JavaMemory.LAUNCHER_DEFAULT, line -> {}));
        assertTrue(folder.getMessage().contains("does not exist"), folder.getMessage());

        InstallerException noProfiles = assertThrows(
                InstallerException.class,
                () -> installer.install(minecraft, "26.2", true, JavaMemory.LAUNCHER_DEFAULT, line -> {}));
        assertTrue(noProfiles.getMessage().contains("Start the Minecraft Launcher once"), noProfiles.getMessage());

        Files.writeString(minecraft.resolve("launcher_profiles.json"), "{\"profiles\": {");
        InstallerException broken = assertThrows(
                InstallerException.class,
                () -> installer.install(minecraft, "26.2", true, JavaMemory.LAUNCHER_DEFAULT, line -> {}));
        assertTrue(broken.getMessage().contains("is not valid JSON"), broken.getMessage());
        assertEquals("{\"profiles\": {", Files.readString(minecraft.resolve("launcher_profiles.json")));
    }

    @Test
    void uninstallRemovesVersionProfileAndPolyModLoaderLibrariesOnly() throws Exception {
        Files.writeString(minecraft.resolve("launcher_profiles.json"), "{\"profiles\": {\"vanilla\": {}}}");
        installer(payload()).install(minecraft, "26.2", true, JavaMemory.LAUNCHER_DEFAULT, line -> {});
        Files.writeString(minecraft.resolve("mods/my-mod.jar"), "mine");

        installer(payload()).uninstall(minecraft, "26.2", line -> {});

        assertFalse(Files.exists(minecraft.resolve("versions/polymodloader-0.1.0-26.2")));
        assertFalse(Files.exists(minecraft.resolve("libraries/dev")));
        assertTrue(Files.isRegularFile(minecraft.resolve("libraries/org/ow2/asm/asm/9.11.1/asm-9.11.1.jar")));
        JsonObject profiles = json(minecraft.resolve("launcher_profiles.json")).getAsJsonObject("profiles");
        assertEquals(List.of("vanilla"), List.copyOf(profiles.keySet()));
        assertTrue(Files.isRegularFile(minecraft.resolve("mods/my-mod.jar")));
    }

    @Test
    void commandLineInstallsAndReportsUsageErrors() throws IOException {
        Files.writeString(minecraft.resolve("launcher_profiles.json"), "{\"profiles\": {}}");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        InstallerCommandLine commandLine = new InstallerCommandLine(
                payload(),
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));

        assertEquals(0, commandLine.run(List.of("install", "--dir", minecraft.toString(), "--minecraft", "26.2")));
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("is installed"), out.toString(StandardCharsets.UTF_8));
        assertEquals(2, commandLine.run(List.of("install", "--bogus")));
        assertEquals(2, commandLine.run(List.of("install", "--dir")));
        assertEquals(1, commandLine.run(List.of("install", "--dir", minecraft.toString(), "--minecraft", "9.9")));
        assertEquals(0, commandLine.run(List.of("--version")));
    }

    @Test
    void writesTheMemoryIntoTheProfileAndKeepsOtherJavaArguments() throws Exception {
        Files.writeString(minecraft.resolve("launcher_profiles.json"), """
                {"profiles": {"polymodloader-26.2": {"name": "Poly Mod Loader 26.2", "javaArgs": "-Xmx2G -XX:+UseZGC"}}}
                """);

        installer(payload()).install(minecraft, "26.2", true, JavaMemory.parse("4G", "1G"), line -> {});
        JsonObject poly = json(minecraft.resolve("launcher_profiles.json"))
                .getAsJsonObject("profiles")
                .getAsJsonObject("polymodloader-26.2");
        assertEquals("-XX:+UseZGC -Xms1G -Xmx4G", poly.get("javaArgs").getAsString());

        // The launcher default leaves the player's arguments alone.
        installer(payload()).install(minecraft, "26.2", true, JavaMemory.LAUNCHER_DEFAULT, line -> {});
        poly = json(minecraft.resolve("launcher_profiles.json"))
                .getAsJsonObject("profiles")
                .getAsJsonObject("polymodloader-26.2");
        assertEquals("-XX:+UseZGC -Xms1G -Xmx4G", poly.get("javaArgs").getAsString());

        List<String> warnings =
                installer(payload()).install(minecraft, "26.2", false, JavaMemory.parse("4G", ""), line -> {});
        assertTrue(
                warnings.stream().anyMatch(warning -> warning.contains("memory setting was not written")),
                warnings.toString());
    }

    @Test
    void commandLineTakesMemoryOptions() throws IOException {
        Files.writeString(minecraft.resolve("launcher_profiles.json"), "{\"profiles\": {}}");
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        InstallerCommandLine commandLine = new InstallerCommandLine(
                payload(),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));

        assertEquals(
                0,
                commandLine.run(
                        List.of("install", "--dir", minecraft.toString(), "--max-memory", "3G", "--min-memory", "1G")));
        assertEquals(
                "-Xms1G -Xmx3G",
                json(minecraft.resolve("launcher_profiles.json"))
                        .getAsJsonObject("profiles")
                        .getAsJsonObject("polymodloader-26.2")
                        .get("javaArgs")
                        .getAsString());
        assertEquals(2, commandLine.run(List.of("install", "--dir", minecraft.toString(), "--xmx", "lots")));
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("not a size"), err.toString(StandardCharsets.UTF_8));
    }

    /**
     * Poly Mod Loader's Java agent (ADR-0024) is the only addition for Forge mods: one JVM argument the launcher
     * resolves, and no Forge library downloaded for every player.
     */
    @Test
    void startsTheProfileWithPolyModLoadersJavaAgent() throws Exception {
        Files.writeString(minecraft.resolve("launcher_profiles.json"), "{\"profiles\": {}}");

        installer(payloadWithAgent()).install(minecraft, "26.2", true, JavaMemory.LAUNCHER_DEFAULT, line -> {});

        JsonObject version = json(minecraft.resolve("versions/polymodloader-0.1.0-26.2/polymodloader-0.1.0-26.2.json"));
        List<String> jvm = new ArrayList<>();
        version.getAsJsonObject("arguments").getAsJsonArray("jvm").forEach(argument -> jvm.add(argument.getAsString()));
        assertEquals(
                List.of(
                        "-Dpoly.launchTarget=client",
                        "-javaagent:${library_directory}/dev/polymodloader/poly-bootstrap/0.1.0/poly-bootstrap-0.1.0.jar"),
                jvm);
        for (var library : version.getAsJsonArray("libraries")) {
            String name = library.getAsJsonObject().get("name").getAsString();
            assertFalse(name.startsWith("net.minecraftforge:"), name);
        }
        assertTrue(Files.isRegularFile(
                minecraft.resolve("libraries/dev/polymodloader/poly-bootstrap/0.1.0/poly-bootstrap-0.1.0.jar")));
    }

    @Test
    void anAgentMustBeOneOfThePayloadsLibraries() {
        IllegalArgumentException problem = assertThrows(
                IllegalArgumentException.class,
                () -> new InstallerPayload(
                        "0.1.0",
                        List.of("26.2"),
                        "dev.plexusmodloader.bootstrap.PolyLauncherMain",
                        List.of(),
                        Optional.of(new InstallerPayload.Agent(
                                "dev.plexusmodloader:poly-bootstrap:0.1.0",
                                "dev.plexusmodloader.bootstrap.SystemClassPathAgent")),
                        path -> new ByteArrayInputStream(new byte[0])));

        assertTrue(problem.getMessage().contains("poly-bootstrap"), problem.getMessage());
    }

    @Test
    void readsTheAgentOfTheIndex() {
        InstallerPayload parsed =
                InstallerPayload.parse(new StringReader("""
                {"polyVersion": "0.1.0", "minecraftVersions": ["26.2"],
                 "mainClass": "dev.plexusmodloader.bootstrap.PolyLauncherMain",
                 "agent": {"library": "dev.plexusmodloader:poly-bootstrap:0.1.0",
                           "class": "dev.plexusmodloader.bootstrap.SystemClassPathAgent"},
                 "libraries": [{"name": "dev.plexusmodloader:poly-bootstrap:0.1.0",
                                "path": "dev/polymodloader/poly-bootstrap/0.1.0/poly-bootstrap-0.1.0.jar",
                                "sha1": "00", "size": 3}]}
                """), path -> new ByteArrayInputStream(new byte[0]));

        assertEquals(
                Optional.of(new InstallerPayload.Agent(
                        "dev.plexusmodloader:poly-bootstrap:0.1.0",
                        "dev.plexusmodloader.bootstrap.SystemClassPathAgent")),
                parsed.agent());
        assertEquals(
                "dev/polymodloader/poly-bootstrap/0.1.0/poly-bootstrap-0.1.0.jar",
                parsed.agentLibrary().orElseThrow().path());
    }

    @Test
    void readsTheIndexTheBuildWrites() {
        InstallerPayload parsed =
                InstallerPayload.parse(new StringReader("""
                        {"polyVersion": "0.1.0", "minecraftVersions": ["26.1", "26.2"],
                         "mainClass": "dev.plexusmodloader.bootstrap.PolyLauncherMain",
                         "libraries": [{"name": "org.ow2.asm:asm:9.11.1", "path": "org/ow2/asm/asm/9.11.1/asm-9.11.1.jar",
                                        "sha1": "00", "size": 3}]}
                        """), path -> new ByteArrayInputStream(new byte[0]));

        assertEquals("26.2", parsed.newestMinecraftVersion());
        assertEquals("org.ow2.asm:asm:9.11.1", parsed.libraries().get(0).name());
        assertEquals(3, parsed.libraries().get(0).size());
        // An index without an agent (written before ADR-0024) still installs, without the agent.
        assertEquals(Optional.empty(), parsed.agent());
    }

    private static LauncherProfileInstaller installer(InstallerPayload payload) {
        return new LauncherProfileInstaller(payload, CLOCK);
    }

    private static InstallerPayload payload() {
        Map<String, byte[]> jars = Map.of(
                "dev/polymodloader/poly-runtime-impl/0.1.0/poly-runtime-impl-0.1.0.jar", RUNTIME_JAR,
                "org/ow2/asm/asm/9.11.1/asm-9.11.1.jar", ASM_JAR);
        return new InstallerPayload(
                "0.1.0",
                List.of("26.2"),
                "dev.plexusmodloader.bootstrap.PolyLauncherMain",
                List.of(
                        new InstallerPayload.Library(
                                "dev.plexusmodloader:poly-runtime-impl:0.1.0",
                                "dev/polymodloader/poly-runtime-impl/0.1.0/poly-runtime-impl-0.1.0.jar",
                                sha1(RUNTIME_JAR),
                                RUNTIME_JAR.length),
                        new InstallerPayload.Library(
                                "org.ow2.asm:asm:9.11.1",
                                "org/ow2/asm/asm/9.11.1/asm-9.11.1.jar",
                                sha1(ASM_JAR),
                                ASM_JAR.length)),
                path -> new ByteArrayInputStream(jars.get(path)));
    }

    /** {@link #payload()} plus poly-bootstrap as the agent library. */
    private static InstallerPayload payloadWithAgent() {
        InstallerPayload base = payload();
        byte[] bootstrap = "bootstrap bytes".getBytes(StandardCharsets.UTF_8);
        String path = "dev/polymodloader/poly-bootstrap/0.1.0/poly-bootstrap-0.1.0.jar";
        List<InstallerPayload.Library> libraries = new ArrayList<>(base.libraries());
        libraries.add(new InstallerPayload.Library(
                "dev.plexusmodloader:poly-bootstrap:0.1.0", path, sha1(bootstrap), bootstrap.length));
        return new InstallerPayload(
                base.polyVersion(),
                base.minecraftVersions(),
                base.mainClass(),
                libraries,
                Optional.of(new InstallerPayload.Agent(
                        "dev.plexusmodloader:poly-bootstrap:0.1.0",
                        "dev.plexusmodloader.bootstrap.SystemClassPathAgent")),
                requested -> requested.equals(path)
                        ? new ByteArrayInputStream(bootstrap)
                        : base.source().open(requested));
    }

    private static JsonObject json(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    }

    private static String sha1(byte[] bytes) {
        try {
            return HexFormat.of()
                    .formatHex(java.security.MessageDigest.getInstance("SHA-1").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
