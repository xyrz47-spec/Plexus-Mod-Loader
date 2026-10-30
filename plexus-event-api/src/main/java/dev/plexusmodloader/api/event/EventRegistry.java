package dev.plexusmodloader.api.event;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/** Registers event listeners for the calling mod. Obtained from {@code ModContext.events()}. */
@ApiStability(StabilityLevel.PREVIEW)
public interface EventRegistry {

    /**
     * Registers {@code listener} for {@code event} with {@link EventPriority#NORMAL}.
     *
     * @see #register(EventKey, EventPriority, Object)
     */
    default <L> void register(EventKey<L> event, L listener) {
        register(event, EventPriority.NORMAL, listener);
    }

    /**
     * Registers {@code listener} for {@code event}. Listeners run from {@link EventPriority#HIGHEST}
     * to {@link EventPriority#LOWEST}; listeners with the same priority run in registration order. In
     * decision events the first deciding listener ends the event (see {@link EventResult}).
     *
     * <p><b>Thread:</b> any; registration is thread-safe. <b>When:</b> at any time, usually in an
     * entrypoint; a listener registered while the event is being fired runs from the next firing on.
     *
     * <p><b>Failure:</b> a listener that throws is reported with the mod ID and disabled
     * ({@code PLEXUS-EVENT-001}); other listeners and the game keep running.
     *
     * @throws IllegalArgumentException when {@code listener} does not implement the event's listener
     *     type
     * @throws NullPointerException when an argument is {@code null}
     */
    <L> void register(EventKey<L> event, EventPriority priority, L listener);
}
