package dev.polymodloader.api.event.player;

import dev.polymodloader.api.entity.PlayerView;
import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;

/** Listener for {@link PlayerEvents#JOINED}. */
@ApiStability(StabilityLevel.PREVIEW)
@FunctionalInterface
public interface PlayerJoinedListener {

    /** Called on the server thread; {@code player} is valid only during the call. */
    void onPlayerJoined(PlayerView player);
}
