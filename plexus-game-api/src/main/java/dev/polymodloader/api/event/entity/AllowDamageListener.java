package dev.polymodloader.api.event.entity;

import dev.polymodloader.api.entity.DamageInfo;
import dev.polymodloader.api.entity.LivingEntityView;
import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;

/** Listener for {@link EntityEvents#ALLOW_DAMAGE}. */
@ApiStability(StabilityLevel.PREVIEW)
@FunctionalInterface
public interface AllowDamageListener {

    /** Returns {@code false} to cancel the damage. Called on the server thread before armour applies. */
    boolean allowDamage(LivingEntityView entity, DamageInfo damage);
}
