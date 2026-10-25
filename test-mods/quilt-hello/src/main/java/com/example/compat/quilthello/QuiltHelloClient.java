package com.example.compat.quilthello;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The {@code client} entrypoint; runs on the client only. */
public final class QuiltHelloClient implements ClientModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("quilt_hello");

    @Override
    public void onInitializeClient() {
        LOGGER.info("[quilt_hello] client entrypoint ran");
    }
}
