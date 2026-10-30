package dev.plexusmodloader.api.world;

import dev.plexusmodloader.api.data.DataTree;
import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/**
 * Mod data saved with one dimension of a world, for state that belongs to no block or entity
 * (counters, quest progress, claimed areas). Server side only.
 */
@ApiStability(StabilityLevel.PREVIEW)
public interface StoredData {

    /** The data; call {@link #markChanged()} after changing it so it is saved. */
    DataTree data();

    /** Marks the data for saving with the next world save. */
    void markChanged();
}
