package com.example.compat.fabricstale;

import net.fabricmc.api.ModInitializer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.RemovedBlock;

/** Would throw NoSuchMethodError and NoClassDefFoundError if it ran. */
public final class FabricStale implements ModInitializer {

    @Override
    public void onInitialize() {
        System.out.println(MinecraftServer.removedInThisVersion() + " " + new RemovedBlock());
    }
}
