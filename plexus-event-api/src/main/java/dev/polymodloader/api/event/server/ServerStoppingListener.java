package dev.polymodloader.api.event.server;

import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;

/** Called on the server thread when the server begins to stop, before worlds are saved. */
@ApiStability(StabilityLevel.PREVIEW)
@FunctionalInterface
public interface ServerStoppingListener {
    /** Handles the event. */
    void onServerStopping(ServerView server);
}
