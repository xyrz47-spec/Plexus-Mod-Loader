package dev.polymodloader.api.lifecycle;

import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;

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
