package dev.polymodloader.game.version.layer;

import dev.polymodloader.api.entity.PlayerView;
import dev.polymodloader.api.resource.ResourceId;
import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;

/**
 * The runtime side of a declared payload channel: the game layer hands it received bytes, which it
 * decodes and passes to the mod's handler.
 */
@ApiStability(StabilityLevel.INTERNAL)
public interface ChannelEndpoint {

    /** The channel ID. */
    ResourceId id();

    /** The mod that declared the channel. */
    String ownerModId();

    /** Decodes and handles bytes a client sent; call on the server thread. */
    void receiveOnServer(byte[] data, PlayerView sender);

    /** Decodes and handles bytes the server sent; call on the client thread. */
    void receiveOnClient(byte[] data);
}
