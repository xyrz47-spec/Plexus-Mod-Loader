package dev.plexusmodloader.api.lifecycle;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/**
 * Entrypoint listed under {@code entrypoints.server} in {@code poly.mod.json}. Runs only on a
 * dedicated server, after every {@link CommonModInitializer}. Same timing and failure rules as
 * {@link CommonModInitializer}.
 */
@ApiStability(StabilityLevel.PREVIEW)
@FunctionalInterface
public interface DedicatedServerModInitializer {
    /** Initialises the mod's dedicated-server-only parts. */
    void onInitializeDedicatedServer(ModContext context);
}
