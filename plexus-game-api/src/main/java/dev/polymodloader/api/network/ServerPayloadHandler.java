package dev.polymodloader.api.network;

import dev.polymodloader.api.entity.PlayerView;
import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;

/** Handles a payload a client sent to the server. Validate it: clients are not trusted. */
@ApiStability(StabilityLevel.PREVIEW)
@FunctionalInterface
public interface ServerPayloadHandler<T> {

    /** Called on the server thread. */
    void receive(T payload, PlayerView sender);
}
