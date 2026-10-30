package dev.plexusmodloader.api.world;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/** Outcome of a block or item interaction, mapped onto the game's interaction results. */
@ApiStability(StabilityLevel.PREVIEW)
public enum InteractionResult {
    /** The interaction happened; the player's arm swings. */
    SUCCESS,
    /** The interaction happened without an arm swing. */
    CONSUME,
    /** Nothing happened; the game continues with its default behaviour. */
    PASS,
    /** The interaction was refused; the game does not try anything else. */
    FAIL
}
