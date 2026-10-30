package dev.plexusmodloader.api.lifecycle;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/**
 * Entrypoint listed under {@code entrypoints.client} in {@code poly.mod.json}. Runs only on the
 * client, after every {@link CommonModInitializer}. Same timing and failure rules as
 * {@link CommonModInitializer}.
 */
@ApiStability(StabilityLevel.PREVIEW)
@FunctionalInterface
public interface ClientModInitializer {
    /** Initialises the mod's client-only parts. */
    void onInitializeClient(ModContext context);
}
