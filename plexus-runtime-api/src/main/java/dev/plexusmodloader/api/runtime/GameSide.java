package dev.plexusmodloader.api.runtime;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/** The kind of Minecraft process Plexus Mod Loader is running in. */
@ApiStability(StabilityLevel.PREVIEW)
public enum GameSide {
    /** The game client, which also hosts the integrated server in single player. */
    CLIENT,
    /** A dedicated server without client classes. */
    DEDICATED_SERVER
}
