package dev.polymodloader.compat.forge.modlauncher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.polymodloader.compat.bridge.BridgeLaunchContext;
import dev.polymodloader.compat.forge.ForeignModIndex;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import net.minecraftforge.forgespi.language.IModFileInfo;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.forgespi.locating.IModFile;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** ADR-0025: Poly Mods and other loaders' mods as forgespi mod files, what Forge mods read from FML's mod list. */
class ForeignModListTest {

    @TempDir
    Path directory;

    @AfterEach
    void uninstall() {
        ForeignModList.install(ForeignModIndex.EMPTY);
    }

    private Path sodiumJar() throws IOException {
        Path path = directory.resolve("sodium-fabric-0.9.2+mc26.2.jar");
        try (OutputStream file = Files.newOutputStream(path);
                JarOutputStream jar = new JarOutputStream(file)) {
            jar.putNextEntry(new JarEntry("fabric.mod.json"));
            jar.write("""
                    {"schemaVersion": 1, "id": "sodium", "version": "0.9.2+mc26.2", "name": "Sodium"}
                    """.getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
            jar.putNextEntry(new JarEntry("assets/sodium/lang/en_us.json"));
            jar.write("{}".getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
        }
        return path;
    }

    @Test
    void aFabricModIsAModFileWithItsIdNameVersionAndJar() throws IOException {
        Path jar = sodiumJar();
        ForeignModList.install(ForeignModIndex.of(
                List.of(new BridgeLaunchContext.LoadedMod(
                        "Fabric", "sodium", "0.9.2+mc26.2", "sodium", List.of("embeddium_alias"), jar, null, null)),
                List.of()));

        IModFileInfo file = ForeignModList.getModFileById("sodium");
        assertEquals("0.9.2+mc26.2", file.versionString(), "Physics Mod compares this with Maven versions");
        IModInfo mod = file.getMods().getFirst();
        assertEquals("sodium", mod.getModId());
        assertEquals("Sodium", mod.getDisplayName());
        assertEquals(new DefaultArtifactVersion("0.9.2+mc26.2"), mod.getVersion());
        assertEquals("sodium", mod.getNamespace());
        assertSame(file, mod.getOwningFile());
        assertEquals(List.of(), mod.getDependencies());
        assertFalse(file.showAsResourcePack(), "Fabric serves its resources; Forge must not load them again");
        assertFalse(file.showAsDataPack());
        assertEquals(List.of(), file.requiredLanguageLoaders());
        assertTrue(file.getConfig().getConfigElement("displayName").isEmpty());

        IModFile modFile = file.getFile();
        assertEquals(jar, modFile.getFilePath());
        assertEquals("sodium-fabric-0.9.2+mc26.2.jar", modFile.getFileName());
        assertEquals(IModFile.Type.MOD, modFile.getType());
        assertSame(file, modFile.getModFileInfo());
        assertEquals(file.getMods(), modFile.getModInfos());
        assertEquals("{}", Files.readString(modFile.findResource("assets", "sodium", "lang", "en_us.json")));
        assertFalse(Files.exists(modFile.findResource("META-INF", "mods.toml")));
        UnsupportedOperationException notFml = assertThrows(UnsupportedOperationException.class, modFile::getSecureJar);
        assertTrue(notFml.getMessage().contains("Fabric mod sodium"), notFml.getMessage());
        assertThrows(UnsupportedOperationException.class, modFile::getScanResult);
        assertThrows(UnsupportedOperationException.class, modFile::getProvider);
    }

    @Test
    void lookupsAnswerForOwnAndProvidedIdsOnly() throws IOException {
        Path jar = sodiumJar();
        ForeignModList.install(ForeignModIndex.of(
                List.of(new BridgeLaunchContext.LoadedMod(
                        "Fabric", "sodium", "0.9.2+mc26.2", "Sodium", List.of("embeddium_alias"), jar, null, null)),
                List.of()));

        assertTrue(ForeignModList.isLoaded("sodium"));
        assertTrue(ForeignModList.isLoaded("embeddium_alias"));
        assertSame(ForeignModList.getModFileById("sodium"), ForeignModList.getModFileById("embeddium_alias"));
        assertEquals(
                "sodium",
                ForeignModList.getModFileById("embeddium_alias")
                        .getMods()
                        .getFirst()
                        .getModId());
        assertFalse(ForeignModList.isLoaded("rubidium"));
        assertNull(ForeignModList.getModFileById("rubidium"));
        assertFalse(ForeignModList.isLoaded(null));
        assertNull(ForeignModList.getModFileById(null));
    }
}
