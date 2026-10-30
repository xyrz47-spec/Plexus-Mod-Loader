package dev.polymodloader.api.event.player;

import dev.polymodloader.api.entity.PlayerView;
import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;

/** Listener for {@link PlayerEvents#LEAVING}. */
@ApiStability(StabilityLevel.PREVIEW)
@FunctionalInterface
public interface PlayerLeavingListener {

    /** Called on the server thread before the player is removed; {@code player} is valid only during the call. */
    void onPlayerLeaving(PlayerView player);
}
