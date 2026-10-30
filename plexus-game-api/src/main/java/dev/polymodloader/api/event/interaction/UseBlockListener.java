package dev.polymodloader.api.event.interaction;

import dev.polymodloader.api.entity.Hand;
import dev.polymodloader.api.entity.PlayerView;
import dev.polymodloader.api.inventory.ItemStackRef;
import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;
import dev.polymodloader.api.world.BlockPos;
import dev.polymodloader.api.world.Direction;
import dev.polymodloader.api.world.InteractionResult;

/** Listener for {@link InteractionEvents#USE_BLOCK}. */
@ApiStability(StabilityLevel.PREVIEW)
@FunctionalInterface
public interface UseBlockListener {

    /**
     * Called on the server thread. The views are valid only during the call.
     *
     * @param player who clicked; {@code player.world()} is the world of the block
     * @param hand the hand the game is trying
     * @param stack the live stack in that hand, possibly empty
     * @param pos the clicked block
     * @param face the clicked face of the block
     * @return {@code PASS} to let others and the game handle it; anything else ends the click
     */
    InteractionResult onUseBlock(PlayerView player, Hand hand, ItemStackRef stack, BlockPos pos, Direction face);
}
