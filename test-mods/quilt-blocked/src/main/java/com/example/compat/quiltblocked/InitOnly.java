package com.example.compat.quiltblocked;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Declared under QSL's {@code init} key only. Without QSL nothing runs it, under Quilt Loader too (POLY-COMPAT-303).
 * It is a Fabric initializer only so that running it by mistake would show in the log.
 */
public final class InitOnly implements ModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("quilt_init_only");

    @Override
    public void onInitialize() {
        LOGGER.info("[quilt_init_only] QSL init entrypoint ran");
    }
}
