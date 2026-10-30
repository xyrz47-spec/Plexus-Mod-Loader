package dev.plexusmodloader.api.network;

import dev.plexusmodloader.api.entity.PlayerView;
import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/** Handles a payload a client sent to the server. Validate it: clients are not trusted. */
@ApiStability(StabilityLevel.PREVIEW)
@FunctionalInterface
public interface ServerPayloadHandler<T> {

    /** Called on the server thread. */
    void receive(T payload, PlayerView sender);
}
