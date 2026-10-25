package dev.polymodloader.api.worldgen;

import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;

/**
 * A density value per block position, used inside the game's noise terrain as a density function
 * ({@link WorldgenRegistrar#densityFunction}). In the game's terrain, positive final density is solid
 * and negative is air or water, so a function can raise, carve or reshape terrain while the game keeps
 * its caves, aquifers, ores, surface rules and biomes.
 *
 * <p>The game computes noise terrain in cells ({@code noise.size_horizontal × 4} by {@code
 * noise.size_vertical × 4} blocks) and blends the final density inside a cell, so shapes smaller than
 * a cell come out smoothed and a surface may be a block off the exact value.
 *
 * <p>Called extremely often from several world generation threads at once: it must be fast, keep no
 * state, and give the same value for the same position every time.
 */
@ApiStability(StabilityLevel.PREVIEW)
@FunctionalInterface
public interface NoiseFunction {

    /** The density at block {@code x, y, z}. */
    double compute(int x, int y, int z);
}
