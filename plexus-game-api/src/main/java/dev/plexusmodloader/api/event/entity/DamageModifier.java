package dev.plexusmodloader.api.event.entity;

import dev.plexusmodloader.api.entity.DamageInfo;
import dev.plexusmodloader.api.entity.LivingEntityView;
import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/** Listener for {@link EntityEvents#MODIFY_DAMAGE}. */
@ApiStability(StabilityLevel.PREVIEW)
@FunctionalInterface
public interface DamageModifier {

    /**
     * Returns the damage to deal instead of {@code damage.amount()}; return it unchanged to keep it.
     * Listeners run in registration order and each sees the previous result. A result of 0 or less
     * cancels the damage.
     */
    float modifyDamage(LivingEntityView entity, DamageInfo damage);
}
