package com.example.multiloaderfallback;

import net.fabricmc.api.ModInitializer;

/** The jar's Fabric side; it runs when NeoForge refuses the jar's NeoForge side. */
public final class FabricSide implements ModInitializer {

    @Override
    public void onInitialize() {
        System.out.println("[multi_loader_fallback] Fabric side initialised: NeoForge refused the NeoForge side");
    }
}
