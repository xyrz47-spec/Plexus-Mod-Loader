package dev.plexusmodloader.api.event.server;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/** Called on the server thread once, before the server's first tick. */
@ApiStability(StabilityLevel.PREVIEW)
@FunctionalInterface
public interface ServerStartedListener {
    /** Handles the event. */
    void onServerStarted(ServerView server);
}
