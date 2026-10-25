package com.example.compat.dual;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The entrypoint {@code quilt.mod.json} names. Quilt Loader would run it; Poly Mod Loader must not. */
public final class DualQuilt implements ModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("fabric_quilt_dual");

    @Override
    public void onInitialize() {
        LOGGER.info("[fabric_quilt_dual] Quilt entrypoint ran");
    }
}
