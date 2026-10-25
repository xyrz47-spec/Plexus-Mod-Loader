package com.example.compat.nativeaddon;

import com.example.compat.fabriclib.FabricLib;
import dev.polymodloader.api.event.lifecycle.LifecycleEvents;
import dev.polymodloader.api.lifecycle.CommonModInitializer;
import dev.polymodloader.api.lifecycle.ModContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads the Fabric library it requires. Poly Mods initialise before the game starts and Fabric mods where Fabric Loader
 * initialises them, after Minecraft's bootstrap (ADR-0020), so this sees the library's classes but "not ready". Once
 * every mod is initialised ({@link LifecycleEvents#MODS_INITIALIZED}, ADR-0022) it sees the library ready.
 */
public final class NativeAddon implements CommonModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("native_addon");

    @Override
    public void onInitializeCommon(ModContext context) {
        LOGGER.info("[native_addon] fabric_lib says {}", FabricLib.greeting());
        context.events()
                .register(
                        LifecycleEvents.MODS_INITIALIZED,
                        () -> LOGGER.info(
                                "[native_addon] after every mod initialised, fabric_lib says {}",
                                FabricLib.greeting()));
    }
}
