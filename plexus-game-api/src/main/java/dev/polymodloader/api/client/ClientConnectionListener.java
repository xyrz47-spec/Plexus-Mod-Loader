package dev.polymodloader.api.client;

import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;

/** Listener for {@link ClientEvents#JOINED_WORLD} and {@link ClientEvents#LEFT_WORLD}. */
@ApiStability(StabilityLevel.PREVIEW)
@FunctionalInterface
public interface ClientConnectionListener {

    /** Called on the client thread when the client joins or leaves a world. */
    void onConnection(ClientView view);
}
