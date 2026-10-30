package dev.plexusmodloader.api.event.server;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/**
 * Read-only view of a running Minecraft server (dedicated, integrated, or game test). Only valid
 * while the listener that received it runs; do not keep it.
 */
@ApiStability(StabilityLevel.PREVIEW)
public interface ServerView {

    /** Ticks processed so far. */
    int tickCount();

    /** Players currently connected. */
    int playerCount();

    /** Message of the day shown in the server list. */
    String motd();

    /**
     * Whether Minecraft reports this server as dedicated. The client's integrated server and Mojang's
     * game test server report {@code false}.
     */
    boolean dedicated();
}
