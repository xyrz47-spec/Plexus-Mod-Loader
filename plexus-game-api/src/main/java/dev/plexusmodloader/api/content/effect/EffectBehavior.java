package dev.plexusmodloader.api.content.effect;

import dev.plexusmodloader.api.entity.LivingEntityView;
import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

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
