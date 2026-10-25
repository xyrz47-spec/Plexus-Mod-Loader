package dev.polymodloader.api.network;

import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;

/** Handles a payload the server sent to this client. */
@ApiStability(StabilityLevel.PREVIEW)
@FunctionalInterface
public interface ClientPayloadHandler<T> {

    /** Called on the client thread. */
    void receive(T payload);
}
