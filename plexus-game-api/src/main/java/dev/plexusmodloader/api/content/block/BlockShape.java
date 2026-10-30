package dev.plexusmodloader.api.content.block;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;
import dev.plexusmodloader.api.world.Direction;
import java.util.ArrayList;
import java.util.List;

/**
 * The solid part of a block: boxes in pixels, where a full block spans {@code 0..16} on each axis,
 * like the {@code from}/{@code to} of a block model element. Used for the outline the player aims at
 * and for what entities collide with.
 *
 * @param boxes the boxes; empty for no shape at all
 */
@ApiStability(StabilityLevel.PREVIEW)
public record BlockShape(List<Box> boxes) {

    /** The whole block. */
    public static final BlockShape FULL = box(0, 0, 0, 16, 16, 16);

    /** Nothing: the block cannot be aimed at or collided with. */
    public static final BlockShape EMPTY = new BlockShape(List.of());

    public BlockShape {
        boxes = List.copyOf(boxes);
    }

    /**
     * A box from {@code (minX, minY, minZ)} to {@code (maxX, maxY, maxZ)} in pixels.
     *
     * @param minX lowest x, {@code 0..16}
     * @param minY lowest y
     * @param minZ lowest z (north side)
     * @param maxX highest x, greater than {@code minX}
     * @param maxY highest y
     * @param maxZ highest z (south side)
     */
    public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        public Box {
            check(minX, maxX, "x");
            check(minY, maxY, "y");
            check(minZ, maxZ, "z");
        }

        private static void check(double min, double max, String axis) {
            if (min < 0 || max > 16 || min >= max) {
                throw new IllegalArgumentException(
                        "Box " + axis + " must satisfy 0 <= min < max <= 16, got " + min + ".." + max);
            }
        }
    }

    /** One box, in pixels. */
    public static BlockShape box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return new BlockShape(List.of(new Box(minX, minY, minZ, maxX, maxY, maxZ)));
    }

    /** This shape together with {@code other}. */
    public BlockShape or(BlockShape other) {
        List<Box> joined = new ArrayList<>(boxes);
        joined.addAll(other.boxes);
        return new BlockShape(joined);
    }

    /**
     * This shape, drawn facing north, turned around the block's vertical axis to face {@code facing}.
     * For example a box along the north edge ends up along the east edge for {@link Direction#EAST}.
     *
     * @throws IllegalArgumentException if {@code facing} is up or down
     */
    public BlockShape facing(Direction facing) {
        if (!facing.isHorizontal()) {
            throw new IllegalArgumentException("A shape can only be turned to a horizontal direction: " + facing);
        }
        List<Box> turned = new ArrayList<>(boxes.size());
        for (Box box : boxes) {
            turned.add(
                    switch (facing) {
                        case EAST -> new Box(16 - box.maxZ, box.minY, box.minX, 16 - box.minZ, box.maxY, box.maxX);
                        case SOUTH ->
                            new Box(16 - box.maxX, box.minY, 16 - box.maxZ, 16 - box.minX, box.maxY, 16 - box.minZ);
                        case WEST -> new Box(box.minZ, box.minY, 16 - box.maxX, box.maxZ, box.maxY, 16 - box.minX);
                        default -> box;
                    });
        }
        return new BlockShape(turned);
    }
}
