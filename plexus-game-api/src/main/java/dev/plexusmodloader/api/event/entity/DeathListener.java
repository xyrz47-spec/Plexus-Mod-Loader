package dev.plexusmodloader.api.event.entity;

import dev.plexusmodloader.api.entity.DamageInfo;
import dev.plexusmodloader.api.entity.LivingEntityView;
import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/** Listener for {@link EntityEvents#DIED}. */
@ApiStability(StabilityLevel.PREVIEW)
@FunctionalInterface
public interface DeathListener {

    /** Called on the server thread when death starts, before drops. */
    void onDied(LivingEntityView entity, DamageInfo cause);
}
