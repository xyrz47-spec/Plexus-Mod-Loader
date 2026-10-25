package com.example.compat.fabriclib;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Sets its greeting in its entrypoint, so a dependent that reads it too early sees "not ready". */
public final class FabricLib implements ModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("fabric_lib");
    private static volatile String greeting = "not ready";

    /** "hello-from-fabric-lib" once this mod's entrypoint has run. */
    public static String greeting() {
        return greeting;
    }

    @Override
    public void onInitialize() {
        greeting = "hello-from-fabric-lib";
        LOGGER.info("[fabric_lib] initialised");
    }
}
