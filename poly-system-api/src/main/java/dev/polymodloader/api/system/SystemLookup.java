package dev.polymodloader.api.system;

import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;

/** Read access to the active implementation of each system. */
@ApiStability(StabilityLevel.PREVIEW)
public interface SystemLookup {

    /**
     * Returns the active implementation of {@code contract}.
     *
     * @throws IllegalArgumentException when no system with that contract is registered
     */
    <T extends PolySystem> T get(Class<T> contract);
}
