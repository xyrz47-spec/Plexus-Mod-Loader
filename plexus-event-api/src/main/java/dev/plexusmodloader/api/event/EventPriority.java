package dev.plexusmodloader.api.event;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/**
 * When a listener runs relative to the other listeners of the same event. Listeners run from
 * {@link #HIGHEST} to {@link #LOWEST}; listeners with the same priority run in registration order
 * (and therefore, for listeners registered from entrypoints, in mod load order).
 *
 * <p>Use a higher priority to decide first in a decision event (for example a protection mod that
 * refuses block breaking in claimed areas), a lower one to see what the others left alone. The
 * levels have the same names and order as NeoForge's {@code EventPriority}; Fabric event phases map
 * onto them (the default phase is {@link #NORMAL}).
 */
@ApiStability(StabilityLevel.PREVIEW)
public enum EventPriority {
    /** Runs first. */
    HIGHEST,
    /** Runs before {@link #NORMAL}. */
    HIGH,
    /** The default for {@link EventRegistry#register(EventKey, Object)}. */
    NORMAL,
    /** Runs after {@link #NORMAL}. */
    LOW,
    /** Runs last. */
    LOWEST
}
