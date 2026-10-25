package dev.polymodloader.api.network;

import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;

/**
 * Converts a payload to and from bytes. {@code decode} must read exactly what {@code encode} wrote.
 * Runs on network threads: keep it free of game state.
 *
 * @param <T> payload type
 */
@ApiStability(StabilityLevel.PREVIEW)
public interface PayloadCodec<T> {

    /** Writes {@code value}. */
    void encode(T value, PayloadWriter out);

    /** Reads a value written by {@link #encode}. */
    T decode(PayloadReader in);

    /** A codec for a single UTF-8 string. */
    static PayloadCodec<String> string() {
        return new PayloadCodec<>() {
            @Override
            public void encode(String value, PayloadWriter out) {
                out.writeString(value);
            }

            @Override
            public String decode(PayloadReader in) {
                return in.readString();
            }
        };
    }
}
