package dev.polymodloader.api.runtime;

import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;

/** The kind of Minecraft process Poly Mod Loader is running in. */
@ApiStability(StabilityLevel.PREVIEW)
public enum GameSide {
    /** The game client, which also hosts the integrated server in single player. */
    CLIENT,
    /** A dedicated server without client classes. */
    DEDICATED_SERVER
}
