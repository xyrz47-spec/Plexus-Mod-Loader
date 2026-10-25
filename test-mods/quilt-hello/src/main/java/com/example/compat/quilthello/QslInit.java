package com.example.compat.quilthello;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Declared under QSL's {@code init} key. QSL runs that key and has no release for Minecraft 26.x, so nothing runs it,
 * under Quilt Loader or Poly Mod Loader (POLY-COMPAT-303). It is a Fabric initializer only so that running it by
 * mistake would show in the log.
 */
public final class QslInit implements ModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("quilt_hello");

    @Override
    public void onInitialize() {
        LOGGER.info("[quilt_hello] QSL init entrypoint ran");
    }
}
