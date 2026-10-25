package com.example.compat.networkprobe;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** The probe's own play-phase payload, registered with Fabric API's networking both ways: one string. */
public record FabricEcho(String text) implements CustomPacketPayload {

    public static final Type<FabricEcho> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("fabric_network_probe", "echo"));
    public static final StreamCodec<ByteBuf, FabricEcho> CODEC =
            ByteBufCodecs.STRING_UTF8.map(FabricEcho::new, FabricEcho::text);

    @Override
    public Type<FabricEcho> type() {
        return TYPE;
    }
}
