package dev.plexusmodloader.api.event.player;

import dev.plexusmodloader.api.entity.PlayerView;
import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/** Listener for {@link PlayerEvents#COPY}. */
@ApiStability(StabilityLevel.PREVIEW)
@FunctionalInterface
public interface PlayerCopyListener {

    /**
     * Called on the server thread while the game builds the new player object for a respawn, after
     * the game copied what it keeps (inventory with {@code keepInventory}, everything when returning
     * from the End) and after Plexus Mod Loader copied {@code EntityDataKey} data. Copy anything else the mod
     * keeps per player object here.
     *
     * @param oldPlayer the player object being replaced; valid only during the call
     * @param newPlayer the new player object, not yet in a world; valid only during the call
     * @param returnedFromEnd {@code true} when leaving the End (alive), {@code false} after death
     */
    void onPlayerCopy(PlayerView oldPlayer, PlayerView newPlayer, boolean returnedFromEnd);
}
