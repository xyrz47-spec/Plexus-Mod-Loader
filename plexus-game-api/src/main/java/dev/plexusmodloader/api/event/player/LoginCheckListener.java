package dev.plexusmodloader.api.event.player;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;
import java.util.Optional;
import java.util.UUID;

/** Listener for {@link PlayerEvents#LOGIN_CHECK}. */
@ApiStability(StabilityLevel.PREVIEW)
@FunctionalInterface
public interface LoginCheckListener {

    /**
     * Returns a reason to refuse the player, shown on their disconnect screen, or empty to let them
     * in. Runs after the game's own ban and whitelist checks passed, before the player exists.
     */
    Optional<String> checkLogin(UUID playerId, String playerName);
}
