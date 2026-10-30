package dev.plexusmodloader.api.event.server;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/** Called on the server thread at the start of every server tick (20 times per second). Keep it fast. */
@ApiStability(StabilityLevel.PREVIEW)
@FunctionalInterface
public interface ServerTickListener {
    /** Handles the event. */
    void onServerTick(ServerView server);
}
