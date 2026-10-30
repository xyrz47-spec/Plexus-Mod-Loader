package dev.polymodloader.api.content.item;

import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;

/** Name colour of an item. */
@ApiStability(StabilityLevel.PREVIEW)
public enum ItemRarity {
    COMMON,
    UNCOMMON,
    RARE,
    EPIC
}
