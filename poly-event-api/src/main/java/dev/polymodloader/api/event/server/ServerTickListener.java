package dev.polymodloader.api.event.server;

import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;

/** Called on the server thread at the start of every server tick (20 times per second). Keep it fast. */
@ApiStability(StabilityLevel.PREVIEW)
@FunctionalInterface
public interface ServerTickListener {
    /** Handles the event. */
    void onServerTick(ServerView server);
}
