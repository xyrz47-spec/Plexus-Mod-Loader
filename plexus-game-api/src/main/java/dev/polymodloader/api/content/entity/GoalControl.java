package dev.polymodloader.api.content.entity;

import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;

/**
 * What an {@link AiGoal} controls. Two running goals never share a control; a goal with a better
 * (lower) priority takes it from a worse one.
 */
@ApiStability(StabilityLevel.PREVIEW)
public enum GoalControl {
    /** Walking and pathfinding. */
    MOVE,
    /** Where the head looks. */
    LOOK,
    /** Jumping. */
    JUMP,
    /** Choosing an attack target (target goals). */
    TARGET
}
