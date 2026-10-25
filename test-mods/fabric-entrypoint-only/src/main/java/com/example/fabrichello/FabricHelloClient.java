package com.example.fabrichello;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A Fabric client entrypoint. */
public final class FabricHelloClient implements ClientModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("fabric_hello");

    @Override
    public void onInitializeClient() {
        LOGGER.info("[fabric_hello] Fabric client entrypoint ran");
    }
}
