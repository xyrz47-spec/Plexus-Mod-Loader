package dev.plexusmodloader.api.lifecycle;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/**
 * Entrypoint listed under {@code entrypoints.common} in {@code plexus.mod.json}. Runs on both the
 * client and the dedicated server.
 *
 * <p><b>Timing:</b> called once on the main thread after all mods were discovered, validated, and
 * ordered by their dependencies, and before Minecraft's own {@code main} starts. Minecraft
 * registries and game objects do not exist yet at this point.
 *
 * <p><b>Failure:</b> an exception stops the launch and is reported with the mod ID.
 */
@ApiStability(StabilityLevel.PREVIEW)
@FunctionalInterface
public interface CommonModInitializer {
    /** Initialises the mod's common (side-independent) parts. */
    void onInitializeCommon(ModContext context);
}
