package dev.plexusmodloader.game.version.layer;

import dev.plexusmodloader.api.entity.PlayerView;
import dev.plexusmodloader.api.resource.ResourceId;
import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/** Sends encoded payloads through the game's connection. Implemented by the game layer. */
@ApiStability(StabilityLevel.INTERNAL)
public interface PayloadTransport {

    /** Sends to one player; throws {@link IllegalStateException} for a client-side player view. */
    void sendToPlayer(PlayerView player, ResourceId channel, byte[] data);

    /** Sends to the server; throws {@link IllegalStateException} when there is no client connection. */
    void sendToServer(ResourceId channel, byte[] data);
}
