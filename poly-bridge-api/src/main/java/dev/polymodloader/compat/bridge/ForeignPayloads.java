package dev.polymodloader.compat.bridge;

import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;
import java.util.Objects;

/**
 * Custom payloads of more than one loader on one connection (ADR-0026 40). Each loader's networking keeps the payload
 * ids it registers, and a host loader that runs the game with another loader's mods tells the game layer which payloads
 * are its own: the game layer encodes a host's payload with the host's codec even where the other loader's codec owns
 * that id on the wire (the same plugin-channel format, such as {@code minecraft:register}), and the host's per-connection
 * channel checks leave other loaders' payloads to those loaders. Without a host, nothing changes.
 *
 * <p>Objects are the game's own ({@code CustomPacketPayload}, {@code StreamCodec}, {@code Identifier}, {@code
 * ConnectionProtocol}); this module does not know Minecraft's types.
 *
 * <p>Thread: {@link #installHost} once while the host prepares, before connections; the queries from any network thread.
 */
@ApiStability(StabilityLevel.INTERNAL)
public final class ForeignPayloads {

    /** What a host knows about its own payloads. */
    public interface Host {

        /**
         * The codec to encode {@code payload} with, given that the game chose {@code chosen} for its id: the host's
         * own codec when {@code payload} is the host's and {@code chosen} is not, otherwise {@code chosen}.
         */
        Object encoderFor(Object payload, Object chosen);

        /**
         * Whether {@code payload}, sent or received in {@code protocol}, is the host's: an id the host registered for
         * that phase, or one of the host's built-in ids carried by the host's own payload object.
         */
        boolean owns(Object payload, Object protocol);
    }

    private static volatile Host host;

    private ForeignPayloads() {}

    /** Installs the host's view; a later call replaces it. */
    public static void installHost(Host value) {
        host = Objects.requireNonNull(value, "value");
    }

    /** The codec to encode {@code payload} with ({@link Host#encoderFor}); {@code chosen} without a host. */
    public static Object encoderFor(Object payload, Object chosen) {
        Host current = host;
        return current == null ? chosen : current.encoderFor(payload, chosen);
    }

    /**
     * Whether {@code payload} belongs to a loader other than the host, so the host's channel checks leave it alone;
     * false without a host.
     */
    public static boolean foreign(Object payload, Object protocol) {
        Host current = host;
        return current != null && !current.owns(payload, protocol);
    }
}
