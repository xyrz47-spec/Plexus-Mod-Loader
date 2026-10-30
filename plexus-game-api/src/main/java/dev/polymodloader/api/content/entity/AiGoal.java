package dev.polymodloader.api.content.entity;

import dev.polymodloader.api.entity.MobView;
import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;
import java.util.EnumSet;
import java.util.Set;

/**
 * One behaviour of a mod mob, run by the game's goal scheduler on the server thread: every tick the
 * scheduler starts the best-priority goals that {@link #canStart} and whose controls are free, ticks
 * running goals, and stops those that no longer {@link #shouldContinue}. Each mob gets its own goal
 * object from the factory given to {@link EntityDefinition.Builder#goal}, so goals may keep state in
 * fields.
 */
@ApiStability(StabilityLevel.PREVIEW)
public interface AiGoal {

    /** The controls this goal needs while it runs; default {@link GoalControl#MOVE}. */
    default Set<GoalControl> controls() {
        return EnumSet.of(GoalControl.MOVE);
    }

    /** Whether the goal should start now. Called often; keep it cheap. */
    boolean canStart(MobView mob);

    /** Whether a running goal keeps running; default {@link #canStart}. */
    default boolean shouldContinue(MobView mob) {
        return canStart(mob);
    }

    /** The goal started. */
    default void start(MobView mob) {}

    /** Every tick while running. */
    default void tick(MobView mob) {}

    /** The goal stopped or was interrupted. */
    default void stop(MobView mob) {}
}
