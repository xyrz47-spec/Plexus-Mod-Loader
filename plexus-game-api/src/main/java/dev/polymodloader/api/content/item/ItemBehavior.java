package dev.polymodloader.api.content.item;

import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;
import dev.polymodloader.api.world.InteractionResult;

/**
 * Code that runs when a mod item is used. Same thread and failure rules as
 * {@link dev.polymodloader.api.content.block.BlockBehavior}.
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
