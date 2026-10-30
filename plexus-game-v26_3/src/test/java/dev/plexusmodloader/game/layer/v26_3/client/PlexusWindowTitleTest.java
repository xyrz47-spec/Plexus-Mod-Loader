package dev.plexusmodloader.game.layer.v26_3.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.plexusmodloader.game.version.layer.LoadedModCounts;
import org.junit.jupiter.api.Test;

/** Plexus Mod Loader's name in the window title, and its mods line on the loading screen. */
class PlexusWindowTitleTest {

    @Test
    void everyLoadersTitleNamesPolyModLoader() {
        assertEquals("Minecraft* Plexus 26.2", PlexusWindowTitle.named("Minecraft* 26.2", "26.2"));
        assertEquals("Minecraft* Plexus 26.2", PlexusWindowTitle.named("Minecraft* Forge 26.2", "26.2"));
        assertEquals("Minecraft* Plexus 26.2", PlexusWindowTitle.named("Minecraft NeoForge* 26.2", "26.2"));
        assertEquals(
                "Minecraft* Plexus 26.2 - Singleplayer",
                PlexusWindowTitle.named("Minecraft* Forge 26.2 - Singleplayer", "26.2"));
        assertEquals("Minecraft Plexus 26.2", PlexusWindowTitle.named("Minecraft 26.2", "26.2"));
    }

    @Test
    void aTitleWithoutTheVersionStaysAsItIs() {
        assertEquals("Something else", PlexusWindowTitle.named("Something else", "26.2"));
    }

    @Test
    void theLoadingScreenNamesTheLoadersWhoseModsRun() {
        assertEquals("", PlexusLoadingOverlay.countsLine(LoadedModCounts.NONE));
        assertEquals(
                "Mods: Fabric 34  ·  NeoForge 53  ·  Plexus 2",
                PlexusLoadingOverlay.countsLine(new LoadedModCounts(2, 34, 0, 0, 53)));
        assertEquals(17 * 6 - 1, PlexusLoadingOverlay.pixelWidth("Plexus Mod Loader"));
    }
}
