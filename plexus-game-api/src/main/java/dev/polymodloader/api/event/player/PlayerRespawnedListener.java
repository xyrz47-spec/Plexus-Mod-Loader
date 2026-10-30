package dev.polymodloader.api.event.player;

import dev.polymodloader.api.entity.PlayerView;
import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;

/** Listener for {@link PlayerEvents#RESPAWNED}. */
@ApiStability(StabilityLevel.PREVIEW)
@FunctionalInterface
public interface PlayerRespawnedListener {

    /**
     * Called on the server thread after the new player object was placed in its world.
     *
     * @param player the new player; valid only during the call
     * @param returnedFromEnd {@code true} when the player left the End through the exit portal (alive),
     *     {@code false} after death
     */
    void onPlayerRespawned(PlayerView player, boolean returnedFromEnd);
}
