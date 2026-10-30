package dev.polymodloader.api.worldgen;

import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;

/**
 * Terrain written in code: fills each block column of new chunks. Registered with {@link
 * WorldgenRegistrar#chunkGenerator}, it is used by dimensions in data packs with
 * {@code "generator": {"type": "<mod id>:<path>", "biome_source": {...}}}. Biome features (ores,
 * trees) and structures are added afterwards by the game as for any generator.
 *
 * <p><b>Threads:</b> called on the game's world generation threads, several columns at once; the
 * code must be thread-safe and give the same column for the same position and seed.
 */
@ApiStability(StabilityLevel.PREVIEW)
@FunctionalInterface
public interface TerrainGenerator {

    /** Fills the column at world position {@code x, z}; unset blocks stay air. */
    void fillColumn(int x, int z, ColumnBuilder column);

    /** Sea level of dimensions using this generator; the game spawns water mobs and fish below it. */
    default int seaLevel() {
        return 63;
    }
}
