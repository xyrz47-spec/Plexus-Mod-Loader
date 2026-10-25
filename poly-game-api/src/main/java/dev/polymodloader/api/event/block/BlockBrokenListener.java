package dev.polymodloader.api.event.block;

import dev.polymodloader.api.entity.PlayerView;
import dev.polymodloader.api.resource.ResourceId;
import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;
import dev.polymodloader.api.world.BlockPos;
import dev.polymodloader.api.world.WorldView;

/** Listener for {@link BlockEvents#BROKEN}. */
@ApiStability(StabilityLevel.PREVIEW)
@FunctionalInterface
public interface BlockBrokenListener {

    /**
     * Called on the server thread after the block was removed and its drops spawned.
     *
     * @param world the world the block was in
     * @param pos where it was
     * @param block the block that was broken
     * @param player who broke it
     */
    void onBlockBroken(WorldView world, BlockPos pos, ResourceId block, PlayerView player);
}
