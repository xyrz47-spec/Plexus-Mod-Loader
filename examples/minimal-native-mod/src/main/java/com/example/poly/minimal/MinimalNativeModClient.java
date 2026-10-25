package com.example.poly.minimal;

import dev.polymodloader.api.lifecycle.ClientModInitializer;
import dev.polymodloader.api.lifecycle.ModContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Client entrypoint of the minimal mod; runs only on the client, after all common entrypoints. */
public final class MinimalNativeModClient implements ClientModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("minimal_native_mod");

    @Override
    public void onInitializeClient(ModContext context) {
        LOGGER.info("[{}] Client entrypoint called", context.modId());
    }
}
