package dev.plexusmodloader.api.content.block;

import dev.plexusmodloader.api.entity.PlayerView;
import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;
import dev.plexusmodloader.api.world.BlockPos;
import dev.plexusmodloader.api.world.WorldView;

/**
 * A player using (right-clicking) a block with an empty hand.
 *
 * @param world the world, client or server copy
 * @param pos the block's position
 * @param player the player
 */
@ApiStability(StabilityLevel.PREVIEW)
public record BlockUse(WorldView world, BlockPos pos, PlayerView player) {}
