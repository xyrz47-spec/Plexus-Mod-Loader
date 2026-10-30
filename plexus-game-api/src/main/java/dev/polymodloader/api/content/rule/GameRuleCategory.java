package dev.polymodloader.api.content.rule;

import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;

/** The section a game rule is listed under in the game's rule screen. */
@ApiStability(StabilityLevel.PREVIEW)
public enum GameRuleCategory {
    PLAYER,
    MOBS,
    SPAWNING,
    DROPS,
    UPDATES,
    CHAT,
    MISC
}
