package com.example.compat.nestedlib;

import com.example.compat.nesteddeep.DeepLib;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Ready once its entrypoint ran; reads the jar nested inside its own jar. */
public final class NestedLib implements ModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("fabric_nested_lib");
    private static volatile boolean ready;

    public static String status() {
        return ready ? "ready" : "not ready";
    }

    @Override
    public void onInitialize() {
        ready = true;
        LOGGER.info("[fabric_nested_lib] initialised, deep library is {}", DeepLib.depth());
    }
}
