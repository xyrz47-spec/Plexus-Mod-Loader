package com.example.neoforgegametest;

import net.minecraft.gametest.framework.GameTestTicker;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.server.loading.ServerModLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test harness, not a Poly Mod Loader feature: NeoForge 26.2.0.93 patches {@code MinecraftServer.tickChildren} to tick
 * Mojang's {@code GameTestTicker} only when {@code GameTestHooks.isGametestEnabled()}, which is false whenever FML runs
 * in production (an installed NeoForge, under NeoForge's own launcher too). Mojang's game test server then never starts
 * a test. On the game test server, and only while NeoForge does not tick the tests itself, this mod ticks them at the
 * end of each server tick, so that a world can be created, tested and saved (NEOFORGE_FULL_SUPPORT.md, N6).
 */
@Mod(NeoForgeGameTestHarness.MOD_ID)
public final class NeoForgeGameTestHarness {

    static final String MOD_ID = "neoforge_gametest_harness";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public NeoForgeGameTestHarness() {
        if (!ServerModLoader.isGameTestServer() || GameTestHooks.isGametestEnabled()) {
            LOGGER.info("[{}] NeoForge ticks game tests itself here (or this is no game test server); idle", MOD_ID);
            return;
        }
        LOGGER.info("[{}] NeoForge runs in production and does not tick game tests; the harness ticks them", MOD_ID);
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, event -> {
            if (event.getServer().tickRateManager().runsNormally()) {
                GameTestTicker.SINGLETON.tick();
            }
        });
    }
}
