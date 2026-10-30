package dev.plexusmodloader.api.content.effect;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/** Whether an effect helps, harms, or neither (affects icons, milk, and some mob reactions). */
@ApiStability(StabilityLevel.PREVIEW)
public enum EffectCategory {
    BENEFICIAL,
    HARMFUL,
    NEUTRAL
}
