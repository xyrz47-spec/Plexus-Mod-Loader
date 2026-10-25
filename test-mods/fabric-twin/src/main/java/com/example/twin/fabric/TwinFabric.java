package com.example.twin.fabric;

import com.example.twin.TwinConfig;
import net.fabricmc.api.ModInitializer;

/** The Fabric build's initialisation: registers the configuration the NeoForge build registers too. */
public final class TwinFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        TwinConfig.register("example", "Fabric");
    }
}
