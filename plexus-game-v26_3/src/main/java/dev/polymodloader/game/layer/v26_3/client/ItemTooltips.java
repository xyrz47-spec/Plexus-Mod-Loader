package dev.polymodloader.game.layer.v26_3.client;

import dev.polymodloader.api.client.ClientEvents;
import dev.polymodloader.api.client.TooltipLines;
import dev.polymodloader.api.inventory.ItemStackRef;
import dev.polymodloader.game.layer.v26_3.LayerState;
import dev.polymodloader.game.layer.v26_3.content.ItemComponents;
import dev.polymodloader.game.version.layer.GameLayerContext;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** Fires {@link ClientEvents#ITEM_TOOLTIP} over the game's tooltip lines. Client thread only. */
public final class ItemTooltips {

    private ItemTooltips() {}

    /**
     * Before the return of {@code ItemStack.getTooltipLines}: a copy of the game's lines that
     * listeners changed, or the game's own list when nobody listens or the tooltip is hidden.
     */
    public static List<Component> onTooltipLines(
            ItemStack stack, Item.TooltipContext context, Player player, TooltipFlag flag, List<Component> returned) {
        GameLayerContext layer = LayerState.context().orElse(null);
        if (layer == null || !layer.events().hasListeners(ClientEvents.ITEM_TOOLTIP)) {
            return returned;
        }
        // The game's early return for hidden tooltips (TooltipDisplay.hideTooltip outside creative).
        if (!flag.isCreative()
                && stack.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT)
                        .hideTooltip()) {
            return returned;
        }
        List<Component> lines = new ArrayList<>(returned);
        TooltipLines view = new ComponentLines(lines);
        ItemStackRef ref = ItemComponents.ref(stack, () -> {});
        boolean advanced = flag.isAdvanced();
        layer.events().publish(ClientEvents.ITEM_TOOLTIP, listener -> listener.onItemTooltip(ref, view, advanced));
        return lines;
    }

    /** {@link TooltipLines} over the game's components; new lines are literal text. */
    static final class ComponentLines implements TooltipLines {

        private final List<Component> lines;

        ComponentLines(List<Component> lines) {
            this.lines = lines;
        }

        @Override
        public int size() {
            return lines.size();
        }

        @Override
        public String get(int index) {
            return lines.get(index).getString();
        }

        @Override
        public void add(String line) {
            lines.add(Component.literal(line));
        }

        @Override
        public void add(int index, String line) {
            lines.add(index, Component.literal(line));
        }

        @Override
        public void remove(int index) {
            lines.remove(index);
        }
    }
}
