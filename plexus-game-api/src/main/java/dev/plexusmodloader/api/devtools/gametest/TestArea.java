package dev.plexusmodloader.api.devtools.gametest;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/**
 * The empty space a game test gets, in blocks; {@code (0, 0, 0)} is its lowest corner. The game
 * keeps a few blocks between neighbouring test areas, so a test that builds or moves things beyond
 * one block needs an area that covers them, or it may disturb its neighbours.
 *
 * @param width size along x, 1–48
 * @param height size along y, 1–48
 * @param depth size along z, 1–48
 */
@ApiStability(StabilityLevel.PREVIEW)
public record TestArea(int width, int height, int depth) {

    /** One block, the default. */
    public static final TestArea SINGLE_BLOCK = new TestArea(1, 1, 1);

    public TestArea {
        if (width < 1 || height < 1 || depth < 1 || width > 48 || height > 48 || depth > 48) {
            throw new IllegalArgumentException(
                    "Test areas are 1-48 blocks per side: " + width + "x" + height + "x" + depth);
        }
    }

    /** A {@code size × size × size} cube. */
    public static TestArea cube(int size) {
        return new TestArea(size, size, size);
    }
}
