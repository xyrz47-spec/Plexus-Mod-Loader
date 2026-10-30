package dev.plexusmodloader.api.content.item;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/** Name colour of an item. */
@ApiStability(StabilityLevel.PREVIEW)
public enum ItemRarity {
    COMMON,
    UNCOMMON,
    RARE,
    EPIC
}
