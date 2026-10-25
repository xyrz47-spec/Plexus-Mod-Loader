package dev.polymodloader.api.event.world;

import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;
import dev.polymodloader.api.world.WorldView;

/** Listener for {@link WorldEvents#READY}. */
@ApiStability(StabilityLevel.PREVIEW)
@FunctionalInterface
public interface WorldReadyListener {

    /** Called on the server thread at the start of the dimension's first tick. */
    void onWorldReady(WorldView world);
}
