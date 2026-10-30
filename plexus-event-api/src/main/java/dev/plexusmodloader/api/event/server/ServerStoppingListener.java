package dev.plexusmodloader.api.event.server;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/** Called on the server thread when the server begins to stop, before worlds are saved. */
@ApiStability(StabilityLevel.PREVIEW)
@FunctionalInterface
public interface ServerStoppingListener {
    /** Handles the event. */
    void onServerStopping(ServerView server);
}
