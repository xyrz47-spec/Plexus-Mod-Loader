package dev.plexusmodloader.api.content.item;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/**
 * Makes an item edible.
 *
 * @param nutrition hunger points restored (bread: 5)
 * @param saturation saturation modifier (bread: 0.6)
 * @param alwaysEdible whether it can be eaten with a full hunger bar
 */
@ApiStability(StabilityLevel.PREVIEW)
public record FoodDefinition(int nutrition, float saturation, boolean alwaysEdible) {

    public FoodDefinition {
        if (nutrition < 0) {
            throw new IllegalArgumentException("nutrition must be >= 0: " + nutrition);
        }
        if (saturation < 0) {
            throw new IllegalArgumentException("saturation must be >= 0: " + saturation);
        }
    }
}
