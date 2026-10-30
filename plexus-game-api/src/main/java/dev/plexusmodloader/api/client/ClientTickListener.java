package dev.plexusmodloader.api.client;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/** Listener for {@link ClientEvents#TICK}. */
@ApiStability(StabilityLevel.PREVIEW)
@FunctionalInterface
public interface ClientTickListener {

    /** Called at the end of each client tick. */
    void onClientTick(ClientView client);
}
