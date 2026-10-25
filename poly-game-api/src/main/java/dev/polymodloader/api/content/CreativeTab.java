package dev.polymodloader.api.content;

import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;

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
