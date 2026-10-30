package dev.plexusmodloader.api.network;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/** Writes payload values in big-endian order. */
@ApiStability(StabilityLevel.PREVIEW)
public interface PayloadWriter {

    /** Writes a {@code boolean} as one byte. */
    PayloadWriter writeBoolean(boolean value);

    /** Writes a 4-byte {@code int}. */
    PayloadWriter writeInt(int value);

    /** Writes an 8-byte {@code long}. */
    PayloadWriter writeLong(long value);

    /** Writes an 8-byte {@code double}. */
    PayloadWriter writeDouble(double value);

    /** Writes a length-prefixed UTF-8 string (at most 32767 characters). */
    PayloadWriter writeString(String value);

    /** Writes a length-prefixed byte array. */
    PayloadWriter writeBytes(byte[] value);
}
