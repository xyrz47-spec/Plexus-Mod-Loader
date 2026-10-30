package dev.polymodloader.api.event.server;

import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;

/** Called on the server thread once, before the server's first tick. */
@ApiStability(StabilityLevel.PREVIEW)
@FunctionalInterface
public interface ServerStartedListener {
    /** Handles the event. */
    void onServerStarted(ServerView server);
}
