package dev.plexusmodloader.api.event.player;

import dev.plexusmodloader.api.entity.PlayerView;
import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/** Listener for {@link PlayerEvents#JOINED}. */
@ApiStability(StabilityLevel.PREVIEW)
@FunctionalInterface
public interface PlayerJoinedListener {

    /** Called on the server thread; {@code player} is valid only during the call. */
    void onPlayerJoined(PlayerView player);
}
