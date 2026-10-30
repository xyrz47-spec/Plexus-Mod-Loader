package dev.polymodloader.api.world;

import dev.polymodloader.api.data.DataTree;
import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;

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
