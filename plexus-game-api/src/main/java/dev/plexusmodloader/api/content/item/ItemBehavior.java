package dev.plexusmodloader.api.content.item;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;
import dev.plexusmodloader.api.world.InteractionResult;

/**
 * Code that runs when a mod item is used. Same thread and failure rules as
 * {@link dev.plexusmodloader.api.content.block.BlockBehavior}.
 */
@ApiStability(StabilityLevel.PREVIEW)
public interface ItemBehavior {

    /**
     * The item was used in the air. Returning {@link InteractionResult#PASS} keeps the game's default
     * (for example eating, for food items).
     */
    default InteractionResult onUse(ItemUse use) {
        return InteractionResult.PASS;
    }
}
