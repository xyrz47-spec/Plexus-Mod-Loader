package dev.plexusmodloader.api.event.block;

import dev.plexusmodloader.api.entity.PlayerView;
import dev.plexusmodloader.api.resource.ResourceId;
import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;
import dev.plexusmodloader.api.world.BlockPos;
import dev.plexusmodloader.api.world.WorldView;

/** Listener for {@link BlockEvents#PLACED}. */
@ApiStability(StabilityLevel.PREVIEW)
@FunctionalInterface
public interface BlockPlacedListener {

    /**
     * Called on the server thread after the block was placed, its block entity data applied, and the
     * item used up. The views are valid only during the call.
     *
     * @param world the world
     * @param pos where the block is
     * @param block the placed block
     * @param player who placed it
     */
    void onBlockPlaced(WorldView world, BlockPos pos, ResourceId block, PlayerView player);
}
