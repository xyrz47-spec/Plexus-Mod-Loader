package dev.polymodloader.runtime.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModFileScannerTest {

    @TempDir
    Path mods;

    private Path jar(String name, Manifest manifest, String entry, String text) throws IOException {
        Path path = mods.resolve(name);
        try (OutputStream file = Files.newOutputStream(path);
                JarOutputStream jar =
                        manifest == null ? new JarOutputStream(file) : new JarOutputStream(file, manifest)) {
            if (entry != null) {
                jar.putNextEntry(new JarEntry(entry));
                jar.write(text.getBytes(StandardCharsets.UTF_8));
                jar.closeEntry();
            }
        }
        return path;
    }

    private Path jar(String name, String... entries) throws IOException {
        Path path = mods.resolve(name);
        try (OutputStream file = Files.newOutputStream(path);
                JarOutputStream jar = new JarOutputStream(file)) {
            for (String entry : entries) {
                jar.putNextEntry(new JarEntry(entry));
                jar.closeEntry();
            }
        }
        return path;
    }

    private List<DiscoveredModFile> scan() {
        return new ModFileScanner().scan(mods, List.of());
    }

    @Test
    void classifiesByMetadataFile() throws IOException {
        jar("a.jar", null, "poly.mod.json", "{}");
        jar("b.jar", null, "fabric.mod.json", "{}");
        jar("c.jar", null, "META-INF/neoforge.mods.toml", "");
        jar("d.jar", null, "META-INF/mods.toml", "");
        jar("e.jar", null, "readme.txt", "hi");

        assertEquals(
                List.of(
                        ModFileKind.POLY_NATIVE,
                        ModFileKind.FABRIC,
                        ModFileKind.NEOFORGE,
                        ModFileKind.FORGE,
                        ModFileKind.UNKNOWN),
                scan().stream().map(DiscoveredModFile::kind).toList());
    }

    @Test
    void classifiesPlexusModJson() throws IOException {
        jar("plexus.jar", null, "plexus.mod.json", "{\"id\": \"test\"}");

        List<DiscoveredModFile> files = scan();
        assertEquals(1, files.size());
        assertEquals(ModFileKind.POLY_NATIVE, files.getFirst().kind());
        assertTrue(files.getFirst().nativeMetadataJson().orElseThrow().contains("test"));
    }

    @Test
    void aJarWithAnFmlModTypeIsAnFmlLibrary() throws IOException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue("FMLModType", "LANGPROVIDER");
        jar("kotlinforforge.jar", manifest, "kotlin/Unit.class", "");

        DiscoveredModFile file = scan().getFirst();
        assertEquals(ModFileKind.FML_LIBRARY, file.kind());
        assertEquals("LANGPROVIDER", file.detail().orElseThrow());
    }

    /** One jar for every loader: its kind is the first in ModFileKind's order, and it remembers the others. */
    @Test
    void aJarForSeveralLoadersRemembersEachLoader() throws IOException {
        jar("all.jar", "fabric.mod.json", "META-INF/neoforge.mods.toml", "META-INF/mods.toml");
        jar("forge-and-neoforge.jar", "META-INF/neoforge.mods.toml", "META-INF/mods.toml");
        jar("fabric.jar", "fabric.mod.json");

        List<DiscoveredModFile> files = scan();
        assertEquals(ModFileKind.FABRIC, files.get(0).kind());
        assertEquals(
                Set.of(ModFileKind.NEOFORGE, ModFileKind.FORGE), files.get(0).alsoFor());
        assertEquals(ModFileKind.FABRIC, files.get(1).kind());
        assertEquals(Set.of(), files.get(1).alsoFor());
        assertEquals(ModFileKind.NEOFORGE, files.get(2).kind());
        assertTrue(files.get(2).isFor(ModFileKind.FORGE));
    }

    /** CustomSkinLoader's jar: fabric.mod.json and a ModLauncher transformation service, which Forge runs. */
    @Test
    void aModLauncherTransformationServiceIsForForgeToo() throws IOException {
        jar("skins.jar", "fabric.mod.json", "META-INF/services/cpw.mods.modlauncher.api.ITransformationService");

        DiscoveredModFile file = scan().getFirst();
        assertEquals(ModFileKind.FABRIC, file.kind());
        assertTrue(file.isFor(ModFileKind.FORGE));
    }

    /** A damaged download used to throw out of discovery and stop the launch without naming other mods. */
    @Test
    void aFileThatIsNotAJarIsUnreadableNotFatal() throws IOException {
        Files.writeString(mods.resolve("cut-off.jar"), "this download was cut off");
        jar("good.jar", null, "fabric.mod.json", "{}");

        List<DiscoveredModFile> files = scan();
        assertEquals(
                List.of(ModFileKind.UNREADABLE, ModFileKind.FABRIC),
                files.stream().map(DiscoveredModFile::kind).toList());
        assertTrue(
                files.getFirst().detail().orElseThrow().contains("ZipException"),
                files.getFirst().detail().toString());
    }

    @Test
    void aQuiltModIsQuiltAndAJarWithBothFabricAndQuiltMetadataIsAFabricModAlsoForQuilt() throws IOException {
        jar("quilt.jar", "quilt.mod.json");
        jar("dual.jar", "quilt.mod.json", "fabric.mod.json");
        jar("json5.jar", "quilt.mod.json5");

        List<DiscoveredModFile> files = scan();

        assertEquals(ModFileKind.FABRIC, files.get(0).kind());
        assertEquals(Set.of(ModFileKind.QUILT), files.get(0).alsoFor());
        assertTrue(files.get(0).isFor(ModFileKind.QUILT));
        assertEquals(ModFileKind.UNKNOWN, files.get(1).kind());
        assertEquals(ModFileKind.QUILT, files.get(2).kind());
        assertEquals(Set.of(), files.get(2).alsoFor());
    }
}
