package dev.polymodloader.api.content.effect;

import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;

/** Whether an effect helps, harms, or neither (affects icons, milk, and some mob reactions). */
@ApiStability(StabilityLevel.PREVIEW)
public enum EffectCategory {
    BENEFICIAL,
    HARMFUL,
    NEUTRAL
}
