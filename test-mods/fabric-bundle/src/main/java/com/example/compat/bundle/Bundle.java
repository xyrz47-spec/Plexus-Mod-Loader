package com.example.compat.bundle;

import com.example.compat.nestedlib.NestedLib;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Uses its nested library; the status proves the library's entrypoint ran first. */
public final class Bundle implements ModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("fabric_bundle");

    @Override
    public void onInitialize() {
        LOGGER.info("[fabric_bundle] nested library is {}", NestedLib.status());
    }
}
