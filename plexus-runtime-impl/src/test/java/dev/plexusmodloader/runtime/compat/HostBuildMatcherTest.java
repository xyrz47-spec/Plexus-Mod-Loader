package dev.plexusmodloader.runtime.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.plexusmodloader.compat.bridge.BridgeLaunchContext;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HostBuildMatcherTest {

    @TempDir
    Path directory;

    private static BridgeLaunchContext.LoadedMod hostMod(String id, Path jar) {
        return new BridgeLaunchContext.LoadedMod("NeoForge", id, "1.0.0", id, List.of(), jar, null, null, true);
    }

    private Path jar(String name, String... classes) throws IOException {
        Path jar = directory.resolve(name);
        try (OutputStream out = Files.newOutputStream(jar)) {
            out.write(zip(classes));
        }
        return jar;
    }

    private static byte[] zip(String... classes) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
            zip.closeEntry();
            for (String name : classes) {
                zip.putNextEntry(new ZipEntry(name.replace('.', '/') + ".class"));
                zip.write(new byte[] {(byte) 0xCA, (byte) 0xFE});
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    @Test
    void theSameIdIsTheSameMod() throws IOException {
        Path host = jar("host.jar", "a.A");
        HostBuildMatcher matcher = new HostBuildMatcher(List.of(hostMod("balm", host)));

        HostBuildMatcher.Match match =
                matcher.find("balm", List.of(), jar("fabric.jar", "b.B")).orElseThrow();

        assertEquals("balm", match.hostMod().modId());
        assertEquals(HostBuildMatcher.Evidence.SAME_ID, match.evidence());
    }

    @Test
    void theSameIdWithLittleSharedCodeDoesNotStandIn() throws IOException {
        // Forge Config API Port: 22 of the Fabric build's 96 classes are in the NeoForge build; the Fabric build's
        // users (Puzzles Lib for Fabric) call its Fabric API, which the host's build does not have.
        Path host = jar("fcap-neoforge.jar", "fcap.Common", "fcap.neoforge.Api");
        HostBuildMatcher matcher = new HostBuildMatcher(List.of(hostMod("forgeconfigapiport", host)));

        HostBuildMatcher.Match match = matcher.find(
                        "forgeconfigapiport",
                        List.of(),
                        jar("fcap-fabric.jar", "fcap.Common", "fcap.fabric.Api", "fcap.fabric.Impl"))
                .orElseThrow();

        assertEquals(HostBuildMatcher.Evidence.SAME_ID, match.evidence());
        assertEquals(1, match.sharedClasses());
        assertFalse(match.sameCode());
    }

    @Test
    void anIdSpelledForTheOtherLoaderIsTheSameMod() throws IOException {
        // Cloth Config: cloth-config for Fabric, cloth_config for NeoForge, whose IDs cannot have hyphens.
        Path host = jar("cloth-neoforge.jar", "me.shedaniel.autoconfig.AutoConfig");
        HostBuildMatcher matcher = new HostBuildMatcher(List.of(hostMod("cloth_config", host)));

        HostBuildMatcher.Match match = matcher.find(
                        "cloth-config",
                        List.of("cloth-config2"),
                        jar("cloth-fabric.jar", "me.shedaniel.autoconfig.AutoConfig"))
                .orElseThrow();

        assertEquals("cloth_config", match.hostMod().modId());
        assertEquals(HostBuildMatcher.Evidence.SAME_ID_SPELLING, match.evidence());
        assertTrue(match.sameCode());
        assertTrue(match.describe().contains("cloth_config"), match.describe());
    }

    @Test
    void aProvidedIdIsTheSameMod() throws IOException {
        Path host = jar("host.jar", "a.A");
        HostBuildMatcher matcher = new HostBuildMatcher(List.of(hostMod("shared_lib", host)));

        HostBuildMatcher.Match match = matcher.find("shared-lib-fabric", List.of("shared-lib"), jar("fabric.jar"))
                .orElseThrow();

        assertEquals(HostBuildMatcher.Evidence.PROVIDED_ID, match.evidence());
    }

    @Test
    void mostlyTheSameClassesAreTheSameModUnderAnotherId() throws IOException {
        Path host = jar("zoom-neoforge.jar", "z.Zoom", "z.Config", "z.Keys", "z.neoforge.Entry");
        HostBuildMatcher matcher = new HostBuildMatcher(List.of(hostMod("zoom_plus", host)));

        HostBuildMatcher.Match match = matcher.find(
                        "zoomer", List.of(), jar("zoom-fabric.jar", "z.Zoom", "z.Config", "z.Keys", "z.fabric.Entry"))
                .orElseThrow();

        assertEquals(HostBuildMatcher.Evidence.SAME_CLASSES, match.evidence());
        assertEquals(3, match.sharedClasses());
        assertEquals(4, match.classes());
    }

    @Test
    void aFewSharedLibraryClassesAreNotTheSameMod() throws IOException {
        Path host = jar("other.jar", "lib.Shaded", "other.Main");
        HostBuildMatcher matcher = new HostBuildMatcher(List.of(hostMod("other", host)));

        assertTrue(matcher.find("mine", List.of(), jar("mine.jar", "lib.Shaded", "mine.A", "mine.B", "mine.C"))
                .isEmpty());
    }

    @Test
    void aNestedHostModIsReadFromInsideItsContainer() throws IOException {
        Path container = directory.resolve("container.jar");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(container))) {
            zip.putNextEntry(new ZipEntry("META-INF/jarjar/inner.jar"));
            zip.write(zip("inner.Api", "inner.Impl"));
            zip.closeEntry();
        }
        BridgeLaunchContext.LoadedMod nested = new BridgeLaunchContext.LoadedMod(
                "NeoForge",
                "inner_api",
                "2.0.0",
                "Inner",
                List.of(),
                container,
                container,
                "META-INF/jarjar/inner.jar",
                true);
        HostBuildMatcher matcher = new HostBuildMatcher(List.of(nested));

        HostBuildMatcher.Match match = matcher.find(
                        "innerapi", List.of(), jar("inner-fabric.jar", "inner.Api", "inner.Impl"))
                .orElseThrow();

        assertEquals("inner_api", match.hostMod().modId());
        assertEquals(HostBuildMatcher.Evidence.SAME_CLASSES, match.evidence());
    }

    @Test
    void theStandInIdsAreTheLeftOutModsIdsAtTheHostsVersion() throws IOException {
        Path host = jar("host.jar", "a.A");
        HostBuildMatcher matcher = new HostBuildMatcher(List.of(hostMod("cloth_config", host)));
        HostBuildMatcher.Match match = matcher.find("cloth-config", List.of("cloth-config2"), jar("fabric.jar", "a.A"))
                .orElseThrow();

        assertEquals(
                Map.of("cloth-config", "1.0.0", "cloth-config2", "1.0.0"),
                match.standIns("cloth-config", List.of("cloth-config2")));
    }

    @Test
    void zeroClassContainerJarsAreSameCodeWhenMatchedById() throws IOException {
        Path host = jar("host.jar");
        HostBuildMatcher matcher = new HostBuildMatcher(List.of(hostMod("fabric-api", host)));
        HostBuildMatcher.Match match =
                matcher.find("fabric-api", List.of(), jar("fabric.jar")).orElseThrow();

        assertEquals(HostBuildMatcher.Evidence.SAME_ID, match.evidence());
        assertTrue(match.sameCode());
    }
}
