package dev.polymodloader.api.content.block.entity;

import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;

/** Server-side code run every tick for each loaded block entity. Keep it cheap. */
@ApiStability(StabilityLevel.PREVIEW)
@FunctionalInterface
public interface BlockEntityTicker {

    /** Called on the server thread; {@code entity} is valid only during the call. */
    void tick(BlockEntityView entity);
}
