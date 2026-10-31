package dev.plexusmodloader.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.plexusmodloader.game.version.layer.LoadedModCounts;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LoadedModCounterTest {

    @Test
    void countsEachLoadersModsSeparately() {
        assertEquals(
                new LoadedModCounts(2, 3, 1),
                LoadedModCounter.count(
                        List.of("minimal_native_mod", "native_lib"),
                        Map.of(LoadedModCounter.FABRIC, List.of("fabric_hello", "fabric_lib", "fabric_addon")),
                        List.of("forge_hello")));
    }

    /** Mods nested in an installed mod's jar are loaded mods too; the bridge lists them like the others. */
    @Test
    void bundledModsCountLikeInstalledOnes() {
        assertEquals(
                new LoadedModCounts(0, 4, 0),
                LoadedModCounter.count(
                        List.of(),
                        Map.of(
                                LoadedModCounter.FABRIC,
                                List.of("fabric-api", "fabric-api-base", "fabric-lifecycle-events-v1", "mixinextras")),
                        List.of()));
    }

    @Test
    void loaderRuntimesAndStandInModsDoNotCount() {
        assertEquals(
                new LoadedModCounts(1, 1, 2),
                LoadedModCounter.count(
                        List.of("plexus", "native_lib"),
                        Map.of(LoadedModCounter.FABRIC, List.of("fabricloader", "minecraft", "java", "sodium")),
                        List.of("forge", "minecraft", "forge_hello", "forge_at")));
    }

    @Test
    void aModIdCountsOnce() {
        assertEquals(
                new LoadedModCounts(1, 1, 1),
                LoadedModCounter.count(
                        List.of("native_lib", "native_lib"),
                        Map.of(LoadedModCounter.FABRIC, List.of("fabric_lib", "fabric_lib")),
                        List.of("forge_hello", "forge_hello")));
    }

    /** Quilt mods count apart from Fabric mods; Quilt Loader's own entry ({@code quilt_loader}) is not a mod. */
    @Test
    void quiltModsCountApart() {
        assertEquals(
                new LoadedModCounts(0, 1, 0, 2),
                LoadedModCounter.count(
                        List.of(),
                        Map.of(
                                LoadedModCounter.FABRIC,
                                List.of("fabric_lib"),
                                LoadedModCounter.QUILT,
                                List.of("quilt_hello", "quilt_nested_lib", "quilt_loader", "quilt_hello")),
                        List.of()));
    }

    /** NeoForge's mods (FML runs them as the host, ADR-0026) count apart from Forge's; NeoForge's own entry is no mod. */
    @Test
    void neoForgeModsCountApart() {
        assertEquals(
                new LoadedModCounts(1, 0, 0, 0, 2),
                LoadedModCounter.count(
                        List.of("native_lib"),
                        Map.of(),
                        List.of(),
                        List.of("neoforge", "minecraft", "neoforge_hello", "jei", "jei")));
    }

    /** Only the Fabric and Quilt bridges load mods themselves; other bridges' mods are never loaded through them. */
    @Test
    void otherBridgesDoNotCount() {
        assertEquals(
                LoadedModCounts.NONE,
                LoadedModCounter.count(List.of(), Map.of("NeoForge", List.of("blocked_example")), List.of()));
    }
}
