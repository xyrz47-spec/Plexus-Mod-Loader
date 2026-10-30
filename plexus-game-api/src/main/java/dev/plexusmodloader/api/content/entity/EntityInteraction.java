package dev.plexusmodloader.api.content.entity;

import dev.plexusmodloader.api.entity.LivingEntityView;
import dev.plexusmodloader.api.entity.PlayerView;
import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;
import dev.plexusmodloader.api.world.WorldView;

/**
 * A player using (right-clicking) a mod mob.
 *
 * @param world the world, client or server copy
 * @param entity the mob
 * @param player the player
 */
@ApiStability(StabilityLevel.PREVIEW)
public record EntityInteraction(WorldView world, LivingEntityView entity, PlayerView player) {}
