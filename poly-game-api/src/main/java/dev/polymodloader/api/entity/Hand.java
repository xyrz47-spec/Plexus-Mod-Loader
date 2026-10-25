package dev.polymodloader.api.entity;

import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;

/** One of a player's or mob's two hands. */
@ApiStability(StabilityLevel.PREVIEW)
public enum Hand {
    /** The main hand: the selected hotbar slot for players. */
    MAIN_HAND,
    /** The off hand (shield slot). */
    OFF_HAND
}
