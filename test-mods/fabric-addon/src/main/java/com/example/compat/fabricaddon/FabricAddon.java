package com.example.compat.fabricaddon;

import com.example.compat.fabriclib.FabricLib;
import com.example.compat.nativelib.NativeLib;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Reads both libraries; the values prove that both ran first. */
public final class FabricAddon implements ModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("fabric_addon");

    @Override
    public void onInitialize() {
        LOGGER.info("[fabric_addon] fabric_lib says {}, native_lib says {}", FabricLib.greeting(), NativeLib.value());
    }
}
