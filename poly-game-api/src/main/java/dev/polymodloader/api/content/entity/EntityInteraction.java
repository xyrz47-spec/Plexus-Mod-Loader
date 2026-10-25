package dev.polymodloader.api.content.entity;

import dev.polymodloader.api.entity.LivingEntityView;
import dev.polymodloader.api.entity.PlayerView;
import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;
import dev.polymodloader.api.world.WorldView;

/**
 * A player using (right-clicking) a mod mob.
 *
 * @param world the world, client or server copy
 * @param entity the mob
 * @param player the player
 */
@ApiStability(StabilityLevel.PREVIEW)
public record EntityInteraction(WorldView world, LivingEntityView entity, PlayerView player) {}
