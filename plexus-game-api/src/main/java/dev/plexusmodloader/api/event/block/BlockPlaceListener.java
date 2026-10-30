package dev.plexusmodloader.api.event.block;

import dev.plexusmodloader.api.entity.PlayerView;
import dev.plexusmodloader.api.event.EventResult;
import dev.plexusmodloader.api.resource.ResourceId;
import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;
import dev.plexusmodloader.api.world.BlockPos;
import dev.plexusmodloader.api.world.WorldView;

/** Listener for {@link BlockEvents#PLACE}. */
@ApiStability(StabilityLevel.PREVIEW)
@FunctionalInterface
public interface BlockPlaceListener {

    /**
     * Called on the server thread after the game checked that the block fits, before it is set. The
     * views are valid only during the call.
     *
     * @param world the world
     * @param pos where the block goes
     * @param block the block about to be placed
     * @param player who places it
     * @return {@code DENY} to refuse the placement (the item is not used up and the player's client
     *     is resynchronised); {@code PASS} or {@code ALLOW} to let it happen
     */
    EventResult onBlockPlace(WorldView world, BlockPos pos, ResourceId block, PlayerView player);
}
