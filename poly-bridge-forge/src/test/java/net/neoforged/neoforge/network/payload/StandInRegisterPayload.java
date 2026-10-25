package net.neoforged.neoforge.network.payload;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Test stand-in for NeoForge's {@code MinecraftRegisterPayload}: NeoForge's own object for a built-in id. */
public record StandInRegisterPayload() implements CustomPacketPayload {

    @Override
    public Type type() {
        return new Type("minecraft:register");
    }
}
