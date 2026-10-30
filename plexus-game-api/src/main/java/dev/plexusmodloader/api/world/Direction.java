package dev.plexusmodloader.api.world;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/** The six block faces; the first four horizontal ones are {@link #NORTH} to {@link #EAST}. */
@ApiStability(StabilityLevel.PREVIEW)
public enum Direction {
    DOWN,
    UP,
    NORTH,
    SOUTH,
    WEST,
    EAST;

    /** Whether this is one of the four horizontal directions. */
    public boolean isHorizontal() {
        return this != DOWN && this != UP;
    }

    /** The direction pointing the other way. */
    public Direction opposite() {
        return switch (this) {
            case DOWN -> UP;
            case UP -> DOWN;
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case WEST -> EAST;
            case EAST -> WEST;
        };
    }
}
