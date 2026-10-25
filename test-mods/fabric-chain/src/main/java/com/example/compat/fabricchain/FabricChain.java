package com.example.compat.fabricchain;

import net.fabricmc.api.ModInitializer;

/** Never runs: its dependencies are not loaded. */
public final class FabricChain implements ModInitializer {

    @Override
    public void onInitialize() {
        throw new IllegalStateException("fabric_chain must not run without its dependencies");
    }
}
