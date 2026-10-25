package com.example.compat.fabricold;

import net.fabricmc.api.ModInitializer;

/** Never runs: the mod is for another Minecraft version. */
public final class FabricOld implements ModInitializer {

    @Override
    public void onInitialize() {
        throw new IllegalStateException("fabric_old must not run on this Minecraft version");
    }
}
