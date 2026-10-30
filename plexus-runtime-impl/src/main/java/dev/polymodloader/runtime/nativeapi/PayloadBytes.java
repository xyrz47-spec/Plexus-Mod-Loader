package dev.polymodloader.runtime.nativeapi;

import dev.polymodloader.api.network.PayloadCodec;
import dev.polymodloader.api.resource.ResourceId;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Encodes and decodes payloads with a mod's codec, checking size limits and leftover bytes. */
final class PayloadBytes {

    private static final Logger LOGGER = LoggerFactory.getLogger("Poly Mod Loader");

    private PayloadBytes() {}

    /** Encodes {@code payload}; {@code PLEXUS-NETWORK-003} when it is longer than {@code limit} bytes. */
    static <T> byte[] encode(PayloadCodec<T> codec, T payload, int limit, ResourceId id) {
        Objects.requireNonNull(payload, "payload");
        ByteArrayPayloadWriter writer = new ByteArrayPayloadWriter();
        codec.encode(payload, writer);
        byte[] bytes = writer.toByteArray();
        if (bytes.length > limit) {
            throw new IllegalArgumentException("PLEXUS-NETWORK-003 payload on " + id + " is " + bytes.length
                    + " bytes; the limit in this direction is " + limit);
        }
        return bytes;
    }

    /** Decodes {@code data}, or logs {@code PLEXUS-NETWORK-004} and returns empty when it is malformed. */
    static <T> Optional<T> decode(PayloadCodec<T> codec, byte[] data, ResourceId id, String ownerModId) {
        try {
            ByteArrayPayloadReader reader = new ByteArrayPayloadReader(data);
            T payload = codec.decode(reader);
            if (reader.remaining() != 0) {
                throw new IllegalStateException(reader.remaining() + " byte(s) left unread");
            }
            return Optional.of(payload);
        } catch (RuntimeException e) {
            LOGGER.warn(
                    "[Poly Mod Loader] PLEXUS-NETWORK-004 dropped a malformed payload on {} (mod {}): {}",
                    id,
                    ownerModId,
                    e.toString());
            return Optional.empty();
        }
    }
}
