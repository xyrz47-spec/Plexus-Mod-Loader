package dev.polymodloader.api.client;

import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;

/** Listener for {@link ClientEvents#TICK}. */
@ApiStability(StabilityLevel.PREVIEW)
@FunctionalInterface
public interface ClientTickListener {

    /** Called at the end of each client tick. */
    void onClientTick(ClientView client);
}
