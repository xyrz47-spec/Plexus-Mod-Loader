package dev.plexusmodloader.api.content;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/** The game's creative inventory tabs that mod items can be listed in. Items are appended at the end. */
@ApiStability(StabilityLevel.PREVIEW)
public enum CreativeTab {
    BUILDING_BLOCKS,
    COLORED_BLOCKS,
    NATURAL_BLOCKS,
    FUNCTIONAL_BLOCKS,
    REDSTONE_BLOCKS,
    TOOLS_AND_UTILITIES,
    COMBAT,
    FOOD_AND_DRINKS,
    INGREDIENTS,
    SPAWN_EGGS
}
