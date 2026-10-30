package dev.plexusmodloader.api.event.server;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/**
 * Called on the server thread once when the server has stopped: players were saved and removed,
 * worlds were saved and closed. A good moment to release what the mod kept for this server; on the
 * client the next single-player world starts a new server.
 */
@ApiStability(StabilityLevel.PREVIEW)
@FunctionalInterface
public interface ServerStoppedListener {
    /** Handles the event. */
    void onServerStopped(ServerView server);
}
