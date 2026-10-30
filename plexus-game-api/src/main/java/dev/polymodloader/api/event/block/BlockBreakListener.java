package dev.polymodloader.api.event.block;

import dev.polymodloader.api.entity.PlayerView;
import dev.polymodloader.api.event.EventResult;
import dev.polymodloader.api.resource.ResourceId;
import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;
import dev.polymodloader.api.world.BlockPos;
import dev.polymodloader.api.world.WorldView;

/** Listener for {@link BlockEvents#BREAK}. */
@ApiStability(StabilityLevel.PREVIEW)
@FunctionalInterface
public interface BlockBreakListener {

    /**
     * Called on the server thread before the block is removed. The views are valid only during the
     * call.
     *
     * @param world the world the block is in
     * @param pos where it is
     * @param block the block about to be broken
     * @param player who breaks it
     * @return {@code DENY} to keep the block (the player's client is told the block is still there);
     *     {@code PASS} or {@code ALLOW} to let the game break it
     */
    EventResult onBlockBreak(WorldView world, BlockPos pos, ResourceId block, PlayerView player);
}
