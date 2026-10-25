package dev.polymodloader.api.client;

import dev.polymodloader.api.inventory.ItemStackRef;
import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;

/** Listener for {@link ClientEvents#ITEM_TOOLTIP}. */
@ApiStability(StabilityLevel.PREVIEW)
@FunctionalInterface
public interface ItemTooltipListener {

    /**
     * Called on the client thread whenever the game builds an item's tooltip (every frame the tooltip
     * is shown, and for the creative search), after the game's own lines. Keep it cheap.
     *
     * @param stack the item stack; read it, do not change it (it is the client's copy)
     * @param lines the tooltip lines to read and change
     * @param advanced whether advanced tooltips are on ({@code F3+H})
     */
    void onItemTooltip(ItemStackRef stack, TooltipLines lines, boolean advanced);
}
