package dev.plexusmodloader.api.event;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/**
 * The answer of a listener in a <em>decision event</em>, an event whose listeners may cancel or
 * force what the game is about to do (for example {@code BlockEvents.BREAK}).
 *
 * <p><b>Convention</b> (the same for every decision event): listeners run in {@link EventPriority}
 * order; the first listener that returns {@link #ALLOW} or {@link #DENY} decides, and the listeners
 * after it are not called. When every listener returns {@link #PASS}, the game does what it would
 * have done without Poly Mod Loader. A listener that throws counts as {@link #PASS} and is disabled
 * ({@code PLEXUS-EVENT-001}); a listener that returns {@code null} is treated the same way
 * ({@code PLEXUS-EVENT-003}).
 *
 * <p>Interaction events use {@code dev.plexusmodloader.api.world.InteractionResult} instead, with the
 * same rule: {@code PASS} lets the next listener run, anything else decides.
 */
@ApiStability(StabilityLevel.PREVIEW)
public enum EventResult {
    /** No opinion: the next listener runs, and without a decision the game goes on as usual. */
    PASS,
    /** Lets the action happen and skips the remaining listeners (the game's own checks still apply). */
    ALLOW,
    /** Cancels the action and skips the remaining listeners. */
    DENY;

    /** Whether this result decides the event, i.e. is not {@link #PASS}. */
    public boolean isDecided() {
        return this != PASS;
    }
}
