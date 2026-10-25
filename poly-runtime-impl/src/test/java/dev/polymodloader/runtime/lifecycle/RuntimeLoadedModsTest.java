package dev.polymodloader.runtime.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.polymodloader.api.lifecycle.LoadedMods;
import dev.polymodloader.compat.bridge.BridgeLaunchContext;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class RuntimeLoadedModsTest {

    private static final LoadedMods MODS = RuntimeLoadedMods.of(List.of(
            mod("MinecraftForge", "forge_hello", "1.0.0", "forge_hello", List.of(), true),
            mod("Poly Mod Loader", "native_lib", "2.1.0", "Native Library", List.of(), false),
            mod("Fabric", "fabric_lib", "1.2.0", "fabric_lib", List.of("fabric_lib_alias"), false)));

    @Test
    void answersForTheModsOfEveryLoader() {
        assertTrue(MODS.isLoaded("forge_hello"));
        assertTrue(MODS.isLoaded("native_lib"));
        assertTrue(MODS.isLoaded("fabric_lib"));
        assertFalse(MODS.isLoaded("sodium"));
        assertEquals(3, MODS.all().size());
    }

    @Test
    void namesEachModsLoaderInLowerCase() {
        assertEquals("minecraftforge", MODS.find("forge_hello").orElseThrow().loader());
        assertEquals("poly", MODS.find("native_lib").orElseThrow().loader());
        LoadedMods.Mod fabric = MODS.find("fabric_lib").orElseThrow();
        assertEquals("fabric", fabric.loader());
        assertEquals("1.2.0", fabric.version());
    }

    @Test
    void findsAModByAnIdItProvides() {
        assertTrue(MODS.isLoaded("fabric_lib_alias"));
        assertEquals("fabric_lib", MODS.find("fabric_lib_alias").orElseThrow().modId());
    }

    @Test
    void aModsOwnIdComesBeforeAnotherModsProvidedId() {
        LoadedMods mods = RuntimeLoadedMods.of(List.of(
                mod("Fabric", "wrapper", "1", "wrapper", List.of("lib"), false),
                mod("Poly Mod Loader", "lib", "2", "Lib", List.of(), false)));

        assertEquals("poly", mods.find("lib").orElseThrow().loader());
    }

    private static BridgeLaunchContext.LoadedMod mod(
            String loader, String id, String version, String name, List<String> provides, boolean runByHost) {
        return new BridgeLaunchContext.LoadedMod(
                loader, id, version, name, provides, Path.of(id + ".jar"), null, null, runByHost);
    }
}
