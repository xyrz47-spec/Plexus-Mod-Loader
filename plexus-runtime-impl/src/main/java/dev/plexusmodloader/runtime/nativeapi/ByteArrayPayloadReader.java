package dev.plexusmodloader.runtime.nativeapi;

import dev.plexusmodloader.api.network.PayloadReader;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/** {@link PayloadReader} over a byte array written by {@link ByteArrayPayloadWriter}. */
final class ByteArrayPayloadReader implements PayloadReader {

    private final ByteBuffer buffer;

    ByteArrayPayloadReader(byte[] data) {
        this.buffer = ByteBuffer.wrap(data);
    }

    @Override
    public boolean readBoolean() {
        return guard(() -> buffer.get() != 0);
    }

    @Override
    public int readInt() {
        return guard(buffer::getInt);
    }

    @Override
    public long readLong() {
        return guard(buffer::getLong);
    }

    @Override
    public double readDouble() {
        return guard(buffer::getDouble);
    }

    @Override
    public String readString() {
        return new String(readBytes(), StandardCharsets.UTF_8);
    }

    @Override
    public byte[] readBytes() {
        int length = readInt();
        if (length < 0 || length > buffer.remaining()) {
            throw new IllegalStateException(
                    "Byte array length " + length + " exceeds the " + buffer.remaining() + " remaining byte(s)");
        }
        byte[] value = new byte[length];
        buffer.get(value);
        return value;
    }

    @Override
    public int remaining() {
        return buffer.remaining();
    }

    private static <T> T guard(java.util.function.Supplier<T> read) {
        try {
            return read.get();
        } catch (BufferUnderflowException e) {
            throw new IllegalStateException("Read past the end of the payload", e);
        }
    }
}
