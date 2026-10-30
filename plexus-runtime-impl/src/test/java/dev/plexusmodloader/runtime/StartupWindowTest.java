package dev.plexusmodloader.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.plexusmodloader.game.version.layer.LoadedModCounts;
import org.junit.jupiter.api.Test;

class StartupWindowTest {

    @Test
    void modsLineIsTheGameLayersLine() {
        // PolyLoadingOverlay.countsLine writes the same line on the game's loading screen.
        assertEquals("", StartupWindow.modsLine(LoadedModCounts.NONE));
        assertEquals(
                "Mods: Fabric 34  ·  NeoForge 53  ·  Plexus 2",
                StartupWindow.modsLine(new LoadedModCounts(2, 34, 0, 0, 53)));
        assertEquals(
                "Mods: Fabric 1  ·  Quilt 2  ·  Forge 3", StartupWindow.modsLine(new LoadedModCounts(0, 1, 3, 2, 0)));
    }

    @Test
    void withoutWindowEveryStepDoesNothing() {
        StartupWindow startup = StartupWindow.none();
        startup.hostStarting("NeoForge");
        startup.discovered(3);
        startup.resolving();
        startup.modsLoaded(new LoadedModCounts(1, 0, 0, 0, 0));
        startup.preparing();
        startup.initialisingMods(1);
        startup.opening();
        startup.shown();
        startup.startingGame("26.2");
        startup.gameEnded();
        assertEquals(false, startup.shows());
    }
}
