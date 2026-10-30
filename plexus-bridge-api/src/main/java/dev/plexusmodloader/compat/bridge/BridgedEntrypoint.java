package dev.plexusmodloader.compat.bridge;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/**
 * An entrypoint of a bridged mod.
 *
 * @param phase {@code common}, {@code client}, or {@code server}
 * @param className fully qualified class name (or the value the language adapter reads)
 * @param adapter the language adapter that creates it, {@code default} for a plain class
 */
@ApiStability(StabilityLevel.INTERNAL)
public record BridgedEntrypoint(String phase, String className, String adapter) {

    /** An entrypoint created by the default adapter. */
    public BridgedEntrypoint(String phase, String className) {
        this(phase, className, "default");
    }
}
