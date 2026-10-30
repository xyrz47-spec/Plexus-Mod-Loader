package dev.plexusmodloader.api.content.block.entity;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/** Server-side code run every tick for each loaded block entity. Keep it cheap. */
@ApiStability(StabilityLevel.PREVIEW)
@FunctionalInterface
public interface BlockEntityTicker {

    /** Called on the server thread; {@code entity} is valid only during the call. */
    void tick(BlockEntityView entity);
}
