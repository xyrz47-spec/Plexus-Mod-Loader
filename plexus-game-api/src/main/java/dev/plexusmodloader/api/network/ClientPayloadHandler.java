package dev.plexusmodloader.api.network;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/** Handles a payload the server sent to this client. */
@ApiStability(StabilityLevel.PREVIEW)
@FunctionalInterface
public interface ClientPayloadHandler<T> {

    /** Called on the client thread. */
    void receive(T payload);
}
