package dev.polymodloader.api.event.entity;

import dev.polymodloader.api.entity.DamageInfo;
import dev.polymodloader.api.entity.LivingEntityView;
import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;

/** Listener for {@link EntityEvents#DIED}. */
@ApiStability(StabilityLevel.PREVIEW)
@FunctionalInterface
public interface DeathListener {

    /** Called on the server thread when death starts, before drops. */
    void onDied(LivingEntityView entity, DamageInfo cause);
}
