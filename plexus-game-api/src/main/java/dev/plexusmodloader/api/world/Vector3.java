package dev.plexusmodloader.api.world;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/**
 * A direction or velocity in blocks (per tick for velocities).
 *
 * @param x east (+) / west (-)
 * @param y up (+) / down (-)
 * @param z south (+) / north (-)
 */
@ApiStability(StabilityLevel.PREVIEW)
public record Vector3(double x, double y, double z) {

    /** No movement. */
    public static final Vector3 ZERO = new Vector3(0, 0, 0);

    /** Length of the vector. */
    public double length() {
        return Math.sqrt(x * x + y * y + z * z);
    }
}
