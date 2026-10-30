package dev.polymodloader.api.content.block;

import dev.polymodloader.api.entity.PlayerView;
import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;
import dev.polymodloader.api.world.BlockPos;
import dev.polymodloader.api.world.WorldView;

/**
 * A player using (right-clicking) a block with an empty hand.
 *
 * @param world the world, client or server copy
 * @param pos the block's position
 * @param player the player
 */
@ApiStability(StabilityLevel.PREVIEW)
public record BlockUse(WorldView world, BlockPos pos, PlayerView player) {}
