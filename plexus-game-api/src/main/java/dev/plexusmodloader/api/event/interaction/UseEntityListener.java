package dev.plexusmodloader.api.event.interaction;

import dev.plexusmodloader.api.entity.EntityView;
import dev.plexusmodloader.api.entity.Hand;
import dev.plexusmodloader.api.entity.PlayerView;
import dev.plexusmodloader.api.inventory.ItemStackRef;
import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;
import dev.plexusmodloader.api.world.InteractionResult;

/** Listener for {@link InteractionEvents#USE_ENTITY}. */
@ApiStability(StabilityLevel.PREVIEW)
@FunctionalInterface
public interface UseEntityListener {

    /**
     * Called on the server thread. The views are valid only during the call.
     *
     * @param player who clicked
     * @param hand the hand the game is trying
     * @param stack the live stack in that hand, possibly empty
     * @param target the clicked entity
     * @return {@code PASS} to let others and the game handle it; anything else ends the click, so
     *     the entity's reaction (trading, mounting, shearing) does not happen
     */
    InteractionResult onUseEntity(PlayerView player, Hand hand, ItemStackRef stack, EntityView target);
}
