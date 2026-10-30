package dev.plexusmodloader.api.network;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/**
 * Declares a mod's network channels. A channel ID is {@code <mod id>:<path>} and carries one payload
 * type in both directions.
 *
 * <p><b>When:</b> inside common entrypoints, so both sides know the channel. Payloads on channels the
 * receiver does not know are dropped by the game, like any unknown custom payload.
 */
@ApiStability(StabilityLevel.PREVIEW)
public interface NetworkRegistrar {

    /**
     * Declares a channel.
     *
     * @throws IllegalArgumentException for an invalid or already used path ({@code PLEXUS-NETWORK-001})
     * @throws IllegalStateException after registration closed ({@code PLEXUS-NETWORK-002})
     */
    <T> PayloadChannel<T> channel(String path, PayloadCodec<T> codec);

    /**
     * Declares configuration exchange {@code <mod id>:<path>}: a question the server asks each joining
     * client before the player enters the world. Declare it in the common entrypoint, on both sides,
     * and set its parts with the returned handle.
     *
     * @throws IllegalArgumentException for an invalid or used path ({@code PLEXUS-NETWORK-001})
     */
    <T> ConfigurationExchange<T> configurationExchange(String path, PayloadCodec<T> codec);
}
