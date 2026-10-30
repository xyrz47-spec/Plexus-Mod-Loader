package dev.plexusmodloader.api.content.entity.model;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/** How a model part moves on its own while the mob is drawn. */
@ApiStability(StabilityLevel.PREVIEW)
public enum PartAnimation {
    /** Keeps its pose. */
    NONE,
    /** Turns with the mob's head (look direction). */
    HEAD,
    /** Swings forward and back while walking (a leg or arm). */
    WALK_SWING,
    /** Swings opposite to {@link #WALK_SWING}. */
    WALK_SWING_OPPOSITE,
    /** Moves gently up and down all the time. */
    BOB,
    /** Spins around its vertical axis all the time. */
    SPIN
}
