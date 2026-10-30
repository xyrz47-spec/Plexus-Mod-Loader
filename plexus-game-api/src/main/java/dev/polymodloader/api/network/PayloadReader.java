package dev.polymodloader.api.network;

import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;

/**
 * Reads payload values written by {@link PayloadWriter}. Reading past the end throws
 * {@link IllegalStateException}, which drops the payload ({@code PLEXUS-NETWORK-004}).
 */
@ApiStability(StabilityLevel.PREVIEW)
public interface PayloadReader {

    /** Reads a {@code boolean}. */
    boolean readBoolean();

    /** Reads an {@code int}. */
    int readInt();

    /** Reads a {@code long}. */
    long readLong();

    /** Reads a {@code double}. */
    double readDouble();

    /** Reads a string. */
    String readString();

    /** Reads a byte array. */
    byte[] readBytes();

    /** Bytes not read yet. */
    int remaining();
}
