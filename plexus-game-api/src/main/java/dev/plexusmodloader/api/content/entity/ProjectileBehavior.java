package dev.plexusmodloader.api.content.entity;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/** Code that runs when a mod projectile hits something; on the server thread. */
@ApiStability(StabilityLevel.PREVIEW)
public interface ProjectileBehavior {

    /** The projectile hit an entity, after its damage was dealt. */
    default void onHitEntity(ProjectileHit hit) {}

    /** The projectile hit a block. */
    default void onHitBlock(ProjectileHit hit) {}
}
