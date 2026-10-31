package dev.plexusmodloader.api.system;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/** Read access to the active implementation of each system. */
@ApiStability(StabilityLevel.PREVIEW)
public interface SystemLookup {

    /**
     * Returns the active implementation of {@code contract}.
     *
     * @throws IllegalArgumentException when no system with that contract is registered
     */
    <T extends PlexusSystem> T get(Class<T> contract);
}
