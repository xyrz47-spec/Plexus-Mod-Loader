package dev.polymodloader.api.content.effect;

import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;
import java.util.Objects;

/**
 * A mod status effect. Its name comes from {@code effect.<mod id>.<path>} and its icon from
 * {@code textures/mob_effect/<path>.png} in the mod's resource pack.
 *
 * @param category helpful, harmful, or neutral
 * @param color particle colour as {@code 0xRRGGBB}
 * @param tickInterval ticks between {@link EffectBehavior#onTick} calls, at least 1
 * @param behavior what the effect does
 */
@ApiStability(StabilityLevel.PREVIEW)
public record EffectDefinition(EffectCategory category, int color, int tickInterval, EffectBehavior behavior) {

    public EffectDefinition {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(behavior, "behavior");
        if (tickInterval < 1) {
            throw new IllegalArgumentException("tickInterval must be >= 1: " + tickInterval);
        }
        if (color < 0 || color > 0xFFFFFF) {
            throw new IllegalArgumentException("color must be 0xRRGGBB: " + Integer.toHexString(color));
        }
    }
}
