package dev.plexusmodloader.api.event.interaction;

import dev.plexusmodloader.api.entity.Hand;
import dev.plexusmodloader.api.entity.PlayerView;
import dev.plexusmodloader.api.inventory.ItemStackRef;
import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;
import dev.plexusmodloader.api.world.InteractionResult;

/** Listener for {@link InteractionEvents#USE_ITEM}. */
@ApiStability(StabilityLevel.PREVIEW)
@FunctionalInterface
public interface UseItemListener {

    /**
     * Called on the server thread, also for an empty hand. The views are valid only during the call.
     *
     * @param player who used the item
     * @param hand the hand holding it
     * @param stack the live stack in that hand, possibly empty
     * @return {@code PASS} to let others and the game handle it; anything else ends the use, so the
     *     item's own behaviour (eating, throwing, drawing a bow) does not start
     */
    InteractionResult onUseItem(PlayerView player, Hand hand, ItemStackRef stack);
}
