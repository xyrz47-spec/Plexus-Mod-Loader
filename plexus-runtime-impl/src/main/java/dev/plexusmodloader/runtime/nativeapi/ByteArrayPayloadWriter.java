package dev.plexusmodloader.runtime.nativeapi;

import dev.plexusmodloader.api.network.PayloadWriter;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** {@link PayloadWriter} over a growing byte array; big-endian like {@link DataOutputStream}. */
final class ByteArrayPayloadWriter implements PayloadWriter {

    static final int MAX_STRING_LENGTH = 32767;

    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final DataOutputStream out = new DataOutputStream(bytes);

    @Override
    public PayloadWriter writeBoolean(boolean value) {
        return write(() -> out.writeBoolean(value));
    }

    @Override
    public PayloadWriter writeInt(int value) {
        return write(() -> out.writeInt(value));
    }

    @Override
    public PayloadWriter writeLong(long value) {
        return write(() -> out.writeLong(value));
    }

    @Override
    public PayloadWriter writeDouble(double value) {
        return write(() -> out.writeDouble(value));
    }

    @Override
    public PayloadWriter writeString(String value) {
        if (value.length() > MAX_STRING_LENGTH) {
            throw new IllegalArgumentException("Strings are limited to " + MAX_STRING_LENGTH + " characters");
        }
        return writeBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public PayloadWriter writeBytes(byte[] value) {
        return write(() -> {
            out.writeInt(value.length);
            out.write(value);
        });
    }

    byte[] toByteArray() {
        return bytes.toByteArray();
    }

    private PayloadWriter write(IoAction action) {
        try {
            action.run();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return this;
    }

    @FunctionalInterface
    private interface IoAction {
        void run() throws IOException;
    }
}
