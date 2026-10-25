package dev.polymodloader.api.worldgen;

import dev.polymodloader.api.resource.ResourceId;
import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;
import dev.polymodloader.api.world.BlockPos;
import java.util.random.RandomGenerator;

/**
 * The box a {@link StructureBuilder} builds in, valid only during {@link StructureBuilder#build}.
 * Coordinates are relative to the box's lowest corner, from {@code 0} to {@code size - 1} on each
 * axis; blocks outside the box are ignored.
 *
 * <p>A structure larger than one chunk is built chunk by chunk: the builder is called once for each
 * chunk the box touches, with the same {@link #random()} sequence each time, and only the blocks in
 * that chunk are written. Build the same structure on every call.
 */
@ApiStability(StabilityLevel.PREVIEW)
public interface StructurePieceContext {

    /** World position of the box's lowest corner. */
    BlockPos origin();

    /** Box size along x. */
    int sizeX();

    /** Box size along y. */
    int sizeY();

    /** Box size along z. */
    int sizeZ();

    /** Randomness for this structure; the same on every call for the same structure. */
    RandomGenerator random();

    /** The block at a box position, without properties. */
    ResourceId blockAt(int x, int y, int z);

    /**
     * Sets the default state of {@code block} at a box position.
     *
     * @throws IllegalArgumentException if {@code block} is not a block
     */
    void setBlock(int x, int y, int z, ResourceId block);

    /** Fills the box positions from {@code (x1, y1, z1)} to {@code (x2, y2, z2)}, both included. */
    default void fill(int x1, int y1, int z1, int x2, int y2, int z2, ResourceId block) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    setBlock(x, y, z, block);
                }
            }
        }
    }
}
