package dev.polymodloader.api.content.item;

import dev.polymodloader.api.entity.PlayerView;
import dev.polymodloader.api.inventory.ItemStackRef;
import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;
import dev.polymodloader.api.world.WorldView;

/**
 * A player using (right-clicking) a mod item in the air.
 *
 * @param world the world, client or server copy
 * @param player the player holding the item
 * @param stack the used stack, live
 */
@ApiStability(StabilityLevel.PREVIEW)
public record ItemUse(WorldView world, PlayerView player, ItemStackRef stack) {}
