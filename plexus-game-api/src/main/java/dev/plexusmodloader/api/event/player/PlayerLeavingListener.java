package dev.plexusmodloader.api.event.player;

import dev.plexusmodloader.api.entity.PlayerView;
import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/** Listener for {@link PlayerEvents#LEAVING}. */
@ApiStability(StabilityLevel.PREVIEW)
@FunctionalInterface
public interface PlayerLeavingListener {

    /** Called on the server thread before the player is removed; {@code player} is valid only during the call. */
    void onPlayerLeaving(PlayerView player);
}
