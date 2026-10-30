package dev.plexusmodloader.api.content.entity;

import dev.plexusmodloader.api.entity.EntityView;
import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;
import dev.plexusmodloader.api.world.BlockPos;
import dev.plexusmodloader.api.world.WorldView;
import java.util.Optional;

/**
 * What a projectile hit.
 *
 * @param world the server world
 * @param projectile the projectile, removed after the callback
 * @param owner who launched it, if known
 * @param entity the hit entity, for entity hits
 * @param block the hit block, for block hits
 */
@ApiStability(StabilityLevel.PREVIEW)
public record ProjectileHit(
        WorldView world,
        EntityView projectile,
        Optional<EntityView> owner,
        Optional<EntityView> entity,
        Optional<BlockPos> block) {}
