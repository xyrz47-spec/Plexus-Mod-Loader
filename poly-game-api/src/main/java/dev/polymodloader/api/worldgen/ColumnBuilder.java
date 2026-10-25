package dev.polymodloader.api.worldgen;

import dev.polymodloader.api.resource.ResourceId;
import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;

/** One block column being generated; valid only during {@link TerrainGenerator#fillColumn}. */
@ApiStability(StabilityLevel.PREVIEW)
public interface ColumnBuilder {

    /** Lowest Y of the dimension. */
    int minY();

    /** Highest Y of the dimension (inclusive). */
    int maxY();

    /** A number derived from the world seed and the generator ID, the same for every column. */
    long seed();

    /**
     * Sets the default state of {@code block} at {@code y}; positions outside the dimension are ignored.
     *
     * @throws IllegalArgumentException for an unknown block
     */
    void set(int y, ResourceId block);

    /** Sets {@code block} from {@code fromY} to {@code toY}, both inclusive. */
    default void fill(int fromY, int toY, ResourceId block) {
        for (int y = Math.max(fromY, minY()); y <= Math.min(toY, maxY()); y++) {
            set(y, block);
        }
    }
}
