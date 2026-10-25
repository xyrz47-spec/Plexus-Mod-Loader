package dev.polymodloader.api.event.world;

import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;
import dev.polymodloader.api.world.WorldView;

/** Listener for {@link WorldEvents#LOADED}, {@link WorldEvents#UNLOADED}, and {@link WorldEvents#SAVING}. */
@ApiStability(StabilityLevel.PREVIEW)
@FunctionalInterface
public interface WorldListener {

    /** Called on the server thread; {@code world} is valid only during the call. */
    void onWorld(WorldView world);
}
