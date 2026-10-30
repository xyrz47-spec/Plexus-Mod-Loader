package dev.plexusmodloader.api.event.world;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;
import dev.plexusmodloader.api.world.WorldView;

/** Listener for {@link WorldEvents#READY}. */
@ApiStability(StabilityLevel.PREVIEW)
@FunctionalInterface
public interface WorldReadyListener {

    /** Called on the server thread at the start of the dimension's first tick. */
    void onWorldReady(WorldView world);
}
