package dev.plexusmodloader.api.content.item;

import dev.plexusmodloader.api.entity.PlayerView;
import dev.plexusmodloader.api.inventory.ItemStackRef;
import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;
import dev.plexusmodloader.api.world.WorldView;

/**
 * A player using (right-clicking) a mod item in the air.
 *
 * @param world the world, client or server copy
 * @param player the player holding the item
 * @param stack the used stack, live
 */
@ApiStability(StabilityLevel.PREVIEW)
public record ItemUse(WorldView world, PlayerView player, ItemStackRef stack) {}
