package com.example.fabrichello;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A Fabric main entrypoint written exactly as for Fabric Loader. */
public final class FabricHello implements ModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("fabric_hello");

    @Override
    public void onInitialize() {
        LOGGER.info(
                "[fabric_hello] Fabric main entrypoint ran (loaded by {})",
                getClass().getClassLoader().getName());
    }
}
