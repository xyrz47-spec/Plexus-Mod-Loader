package dev.polymodloader.api.event.lifecycle;

import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;

/**
 * Called once on the game's main thread after every mod, of every loader, has been initialised. See {@link
 * LifecycleEvents#MODS_INITIALIZED} for when exactly.
 *
 * <p><b>Failure:</b> a listener that throws is reported with the mod ID and disabled ({@code PLEXUS-EVENT-001}); the
 * game keeps starting.
 */
@ApiStability(StabilityLevel.PREVIEW)
@FunctionalInterface
public interface ModsInitializedListener {
    /** Handles the event. */
    void onModsInitialized();
}
