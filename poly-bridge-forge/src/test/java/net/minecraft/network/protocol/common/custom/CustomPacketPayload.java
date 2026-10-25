package net.minecraft.network.protocol.common.custom;

/** Test stand-in for Minecraft's {@code CustomPacketPayload}: a payload's {@code type()} and the type's {@code id()}. */
public interface CustomPacketPayload {

    Type type();

    /** A payload type with its id. */
    record Type(String id) {}
}
