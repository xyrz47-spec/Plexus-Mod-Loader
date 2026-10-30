package dev.polymodloader.api.network;

import dev.polymodloader.api.entity.PlayerView;
import dev.polymodloader.api.resource.ResourceId;
import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;

/**
 * A two-way channel for one payload type. Encoded payloads are limited to 32 KiB towards the server
 * and 1 MiB towards clients; larger payloads throw {@link IllegalArgumentException}
 * ({@code PLEXUS-NETWORK-003}).
 *
 * @param <T> payload type
 */
@ApiStability(StabilityLevel.PREVIEW)
public interface PayloadChannel<T> {

    /** The channel ID. */
    ResourceId id();

    /**
     * Sets what the server does with payloads from clients. Runs on the server thread; {@code sender}
     * is valid only during the call. Replaces an earlier handler.
     */
    void onServerReceive(ServerPayloadHandler<T> handler);

    /** Sets what the client does with payloads from the server. Runs on the client thread. */
    void onClientReceive(ClientPayloadHandler<T> handler);

    /**
     * Sends a payload to one player. Call on the server thread.
     *
     * @throws IllegalStateException when {@code player} is not a server-side player
     */
    void sendToPlayer(PlayerView player, T payload);

    /**
     * Sends a payload to the server. Call on the client thread while connected.
     *
     * @throws IllegalStateException on a dedicated server or when not connected
     */
    void sendToServer(T payload);
}
