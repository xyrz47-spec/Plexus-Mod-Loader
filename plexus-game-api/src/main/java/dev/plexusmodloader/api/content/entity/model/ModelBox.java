package dev.plexusmodloader.api.content.entity.model;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/**
 * A textured cuboid of a model part, in model pixels (1/16 block) relative to the part's pivot.
 *
 * @param u texture x of the box's net (the game's cube unwrap layout)
 * @param v texture y of the box's net
 * @param x lowest corner x
 * @param y lowest corner y (y grows downwards in models)
 * @param z lowest corner z
 * @param width size along x
 * @param height size along y
 * @param depth size along z
 * @param mirror mirrors the texture horizontally
 */
@ApiStability(StabilityLevel.PREVIEW)
public record ModelBox(
        int u, int v, float x, float y, float z, float width, float height, float depth, boolean mirror) {

    public ModelBox {
        if (width < 0 || height < 0 || depth < 0) {
            throw new IllegalArgumentException("Box sizes must be >= 0");
        }
    }
}
