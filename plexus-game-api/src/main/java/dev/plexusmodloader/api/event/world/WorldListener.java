package dev.plexusmodloader.api.event.world;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;
import dev.plexusmodloader.api.world.WorldView;

/** Listener for {@link WorldEvents#LOADED}, {@link WorldEvents#UNLOADED}, and {@link WorldEvents#SAVING}. */
@ApiStability(StabilityLevel.PREVIEW)
@FunctionalInterface
public interface WorldListener {

    /** Called on the server thread; {@code world} is valid only during the call. */
    void onWorld(WorldView world);
}
