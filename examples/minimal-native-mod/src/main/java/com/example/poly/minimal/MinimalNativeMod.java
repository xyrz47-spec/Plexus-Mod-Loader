package com.example.poly.minimal;

import dev.polymodloader.api.event.server.ServerEvents;
import dev.polymodloader.api.lifecycle.CommonModInitializer;
import dev.polymodloader.api.lifecycle.ModContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Common entrypoint of the smallest possible Poly Mod: it only proves it was called. */
public final class MinimalNativeMod implements CommonModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("minimal_native_mod");

    @Override
    public void onInitializeCommon(ModContext context) {
        LOGGER.info(
                "[{}] Hello from {} {} on {}", context.modId(), context.modId(), context.modVersion(), context.side());
        context.onShutdown("say goodbye", () -> LOGGER.info("[{}] Shutdown task ran", context.modId()));
        context.events()
                .register(
                        ServerEvents.STARTED,
                        server -> LOGGER.info(
                                "[{}] Server started (dedicated={}, tick={}, players={})",
                                context.modId(),
                                server.dedicated(),
                                server.tickCount(),
                                server.playerCount()));
        context.events()
                .register(
                        ServerEvents.STOPPING,
                        server -> LOGGER.info(
                                "[{}] Server stopping after {} ticks", context.modId(), server.tickCount()));
    }
}
