package dev.polymodloader.api.worldgen;

import dev.polymodloader.api.resource.ResourceId;
import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;
import dev.polymodloader.api.world.BlockPos;
import java.util.random.RandomGenerator;

/**
 * Where and with what a {@link CodeFeature} places blocks, valid only during {@link CodeFeature#place}.
 * The world being generated is the chunk of the origin and its neighbours; keep blocks within 16
 * blocks of the origin horizontally. Positions are world coordinates.
 */
@ApiStability(StabilityLevel.PREVIEW)
public interface FeatureContext {

    /** The position the placement chose, for example on the surface or at a random height. */
    BlockPos origin();

    /** Randomness for this placement; the same world seed and position give the same values. */
    RandomGenerator random();

    /** The block at {@code pos}, without properties. */
    ResourceId blockAt(BlockPos pos);

    /** Whether {@code pos} is air. */
    boolean isAir(BlockPos pos);

    /**
     * Sets the default state of {@code block} at {@code pos}.
     *
     * @throws IllegalArgumentException if {@code block} is not a block
     */
    void setBlock(BlockPos pos, ResourceId block);

    /** The y of the first air block above the terrain at {@code x, z}, as generation sees it so far. */
    int surfaceY(int x, int z);

    /** The lowest block y of the world. */
    int minY();

    /** The highest block y of the world. */
    int maxY();
}
