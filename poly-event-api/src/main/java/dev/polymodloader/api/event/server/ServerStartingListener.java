package dev.polymodloader.api.event.server;

import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;

/**
 * Called on the server thread once per server start, after registries and data packs are loaded and
 * before the worlds are created. Worlds are not available yet; use {@code WorldEvents.LOADED} for them.
 */
@ApiStability(StabilityLevel.PREVIEW)
@FunctionalInterface
public interface ServerStartingListener {
    /** Handles the event. */
    void onServerStarting(ServerView server);
}
