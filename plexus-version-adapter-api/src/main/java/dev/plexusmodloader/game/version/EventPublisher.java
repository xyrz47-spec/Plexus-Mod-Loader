package dev.plexusmodloader.game.version;

import dev.plexusmodloader.api.event.EventKey;
import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;
import java.util.function.Consumer;
import java.util.function.Function;

/** Lets version adapter hooks fire events to mod listeners. Implemented by the Plexus Mod Loader runtime. */
@ApiStability(StabilityLevel.INTERNAL)
public interface EventPublisher {

    /**
     * Calls {@code invocation} for every enabled listener of {@code event}, in priority order and,
     * within one priority, registration order. Listener failures are isolated and reported; this
     * method does not throw them.
     */
    <L> void publish(EventKey<L> event, Consumer<L> invocation);

    /**
     * Fires a decision event: calls {@code invocation} for the enabled listeners in the same order as
     * {@link #publish} until one returns something other than {@code undecided}, and returns that
     * result; later listeners are not called. Returns {@code undecided} when no listener decided. A
     * listener that throws or returns {@code null} is disabled and counts as undecided.
     */
    <L, R> R publishUntilDecided(EventKey<L> event, Function<L, R> invocation, R undecided);

    /** Whether anyone listens to {@code event}; lets hot hooks skip building event data. */
    boolean hasListeners(EventKey<?> event);
}
