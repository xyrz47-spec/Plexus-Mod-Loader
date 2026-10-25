package dev.polymodloader.game.version.layer;

import dev.polymodloader.api.entity.PlayerView;
import dev.polymodloader.api.resource.ResourceId;
import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;

/** Sends encoded payloads through the game's connection. Implemented by the game layer. */
@ApiStability(StabilityLevel.INTERNAL)
public interface PayloadTransport {

    /** Sends to one player; throws {@link IllegalStateException} for a client-side player view. */
    void sendToPlayer(PlayerView player, ResourceId channel, byte[] data);

    /** Sends to the server; throws {@link IllegalStateException} when there is no client connection. */
    void sendToServer(ResourceId channel, byte[] data);
}
