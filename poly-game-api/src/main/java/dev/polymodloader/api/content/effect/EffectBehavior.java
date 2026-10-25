package dev.polymodloader.api.content.effect;

import dev.polymodloader.api.entity.LivingEntityView;
import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;

/** What a mod status effect does while active. */
@ApiStability(StabilityLevel.PREVIEW)
@FunctionalInterface
public interface EffectBehavior {

    /**
     * Called on the server every {@code tickInterval} ticks while the entity has the effect.
     *
     * @param amplifier 0 for level I
     */
    void onTick(LivingEntityView entity, int amplifier);
}
