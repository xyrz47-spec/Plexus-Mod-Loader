package dev.plexusmodloader.api.world;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/**
 * Integer block coordinates. Inside a game test the coordinates are relative to the test structure;
 * everywhere else they are absolute world coordinates.
 *
 * @param x east (+) / west (-)
 * @param y up (+) / down (-)
 * @param z south (+) / north (-)
 */
@ApiStability(StabilityLevel.PREVIEW)
public record BlockPos(int x, int y, int z) {

    /** The position {@code (0, 0, 0)}. */
    public static final BlockPos ORIGIN = new BlockPos(0, 0, 0);

    /** Creates a position. */
    public static BlockPos of(int x, int y, int z) {
        return new BlockPos(x, y, z);
    }

    /** This position moved by the given amounts. */
    public BlockPos offset(int dx, int dy, int dz) {
        return new BlockPos(x + dx, y + dy, z + dz);
    }

    /** The position one block up. */
    public BlockPos above() {
        return offset(0, 1, 0);
    }

    /** The position one block down. */
    public BlockPos below() {
        return offset(0, -1, 0);
    }
}
