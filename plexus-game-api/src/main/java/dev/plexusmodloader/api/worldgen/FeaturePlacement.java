package dev.plexusmodloader.api.worldgen;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/**
 * Where a {@link CodeFeature} is tried in each chunk: how often, and at which height. Poly Mod Loader writes it as
 * the placed feature with the feature's ID, the same as one from a data pack.
 *
 * @param countPerChunk tries per chunk, each at a random x and z in the chunk
 * @param rarity each try happens in 1 of {@code rarity} chunks; 1 for every chunk
 * @param onSurface whether tries start on the terrain surface; otherwise at a random y in {@code
 *     minY..maxY}
 * @param minY lowest y for tries below the surface
 * @param maxY highest y for tries below the surface
 */
@ApiStability(StabilityLevel.PREVIEW)
public record FeaturePlacement(int countPerChunk, int rarity, boolean onSurface, int minY, int maxY) {

    public FeaturePlacement {
        if (countPerChunk < 1 || countPerChunk > 256) {
            throw new IllegalArgumentException("countPerChunk must be 1-256: " + countPerChunk);
        }
        if (rarity < 1) {
            throw new IllegalArgumentException("rarity must be >= 1: " + rarity);
        }
        if (minY > maxY) {
            throw new IllegalArgumentException("minY must be <= maxY: " + minY + " > " + maxY);
        }
    }

    /** {@code countPerChunk} tries on the surface of each chunk. */
    public static FeaturePlacement onSurface(int countPerChunk) {
        return new FeaturePlacement(countPerChunk, 1, true, 0, 0);
    }

    /** {@code countPerChunk} tries at random heights from {@code minY} to {@code maxY} in each chunk. */
    public static FeaturePlacement atHeight(int countPerChunk, int minY, int maxY) {
        return new FeaturePlacement(countPerChunk, 1, false, minY, maxY);
    }

    /** This placement in only 1 of {@code rarity} chunks. */
    public FeaturePlacement rarity(int rarity) {
        return new FeaturePlacement(countPerChunk, rarity, onSurface, minY, maxY);
    }
}
