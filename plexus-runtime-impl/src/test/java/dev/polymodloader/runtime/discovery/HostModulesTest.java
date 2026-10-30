package dev.polymodloader.runtime.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.polymodloader.api.runtime.GameSide;
import dev.polymodloader.compat.bridge.CompatibilityFinding;
import dev.polymodloader.runtime.discovery.ForeignModResolver.ForeignMod;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Poly Mod Loader's host modules (ADR-0031) in place of mods left out for not fitting the host's patch. */
class HostModulesTest {

    private static final String HOST = "NeoForge 26.2.0.93";
    private static final String RESOURCE = "META-INF/polymodloader/host-modules/neoforge-26.2.0.93/block-host.jar";

    @TempDir
    Path directory;

    private URLClassLoader loader;
    private ForeignModResolver bridges;

    /** The test bridge as a service, and an index that lists a host module for {@code fabric-block-api-v1}. */
    @BeforeEach
    void install() throws IOException {
        Path classes = directory.resolve("classes");
        Path services = classes.resolve("META-INF/services/dev.polymodloader.compat.bridge.ModLoaderBridge");
        Files.createDirectories(services.getParent());
        Files.writeString(services, TestJarInJarBridge.class.getName());
        Path index = classes.resolve(HostModules.INDEX);
        Files.createDirectories(index.getParent());
        Files.writeString(index, "neoforge-26.2.0.93.fabric-block-api-v1=" + RESOURCE + "\n");
        Path hostModule = classes.resolve(RESOURCE);
        Files.createDirectories(hostModule.getParent());
        fabricMod(hostModule, "fabric-block-api-v1", "3.1.0+poly.neoforge.26.2");
        loader = new URLClassLoader(
                new URL[] {classes.toUri().toURL()}, getClass().getClassLoader());
        bridges = new ForeignModResolver(loader);
    }

    @AfterEach
    void close() throws IOException {
        loader.close();
    }

    @Test
    void aModLeftOutForTheHostsPatchRunsAsTheHostModule() throws IOException {
        ForeignMod original = leftOut(mod("fabric-block-api-v1", "3.1.0+53515aab9e"));
        ForeignMod other = mod("fabric-api-base", "2.0.4");

        HostModules.Result result = HostModules.replaceLeftOut(
                HOST, List.of(original, other), bridges, GameSide.DEDICATED_SERVER, directory.resolve("out"), loader);

        assertEquals(1, result.replaced().size());
        ForeignMod replacement = result.mods().getFirst();
        assertEquals("fabric-block-api-v1", replacement.assessment().modId());
        assertEquals("3.1.0+poly.neoforge.26.2", replacement.assessment().version());
        assertTrue(
                replacement.jar().startsWith(directory.resolve("out")),
                replacement.jar().toString());
        assertSame(other, result.mods().get(1));
    }

    @Test
    void aModThatFitsTheHostStaysAsItIs() throws IOException {
        ForeignMod fits = mod("fabric-block-api-v1", "3.1.0+53515aab9e");

        HostModules.Result result = HostModules.replaceLeftOut(
                HOST, List.of(fits), bridges, GameSide.DEDICATED_SERVER, directory.resolve("out"), loader);

        assertEquals(List.of(), result.replaced());
        assertSame(fits, result.mods().getFirst());
    }

    @Test
    void anotherHostReleaseHasNoHostModule() throws IOException {
        ForeignMod original = leftOut(mod("fabric-block-api-v1", "3.1.0+53515aab9e"));

        HostModules.Result result = HostModules.replaceLeftOut(
                "NeoForge 26.2.0.99",
                List.of(original),
                bridges,
                GameSide.DEDICATED_SERVER,
                directory.resolve("out"),
                loader);

        assertEquals(List.of(), result.replaced());
        assertSame(original, result.mods().getFirst());
    }

    @Test
    void theHostKeyIsTheLowerCaseNameWithDashes() {
        assertEquals("neoforge-26.2.0.93", HostModules.hostKey(" NeoForge 26.2.0.93 "));
    }

    private ForeignMod mod(String id, String version) throws IOException {
        Path jar = directory.resolve(id + "-" + version + ".jar");
        fabricMod(jar, id, version);
        return bridges.assess(ModFileScanner.classify(jar), GameSide.DEDICATED_SERVER)
                .orElseThrow();
    }

    private static ForeignMod leftOut(ForeignMod mod) {
        return mod.withFindings(List.of(new CompatibilityFinding("PLEXUS-COMPAT-224", true, "mixins", "test")));
    }

    private static void fabricMod(Path jar, String id, String version) throws IOException {
        try (OutputStream file = Files.newOutputStream(jar);
                JarOutputStream out = new JarOutputStream(file)) {
            out.putNextEntry(new JarEntry("fabric.mod.json"));
            out.write(("{\"schemaVersion\": 1, \"id\": \"" + id + "\", \"version\": \"" + version + "\"}")
                    .getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
    }
}
