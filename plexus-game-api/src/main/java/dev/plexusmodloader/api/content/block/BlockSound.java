package dev.plexusmodloader.api.content.block;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/** Sounds a block makes when placed, broken, and walked on; each maps to the game's sound type. */
@ApiStability(StabilityLevel.PREVIEW)
public enum BlockSound {
    STONE,
    WOOD,
    GRAVEL,
    GRASS,
    METAL,
    GLASS,
    WOOL,
    SAND,
    COPPER,
    AMETHYST
}
