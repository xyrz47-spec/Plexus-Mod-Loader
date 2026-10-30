package dev.plexusmodloader.game.layer.v26_2.content;

import dev.plexusmodloader.api.content.block.entity.MenuLayout;
import dev.plexusmodloader.api.data.DataTree;
import java.util.function.Supplier;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The menu of a mod block entity with a {@link MenuLayout}: the layout's slots, the player's
 * inventory, and two synchronised values per progress bar.
 */
public final class PlexusMenu extends AbstractContainerMenu {

    private final Container container;
    private final MenuLayout layout;
    private final ContainerData values;

    PlexusMenu(
            MenuType<?> type,
            int id,
            Inventory inventory,
            Container container,
            ContainerData values,
            MenuLayout layout) {
        super(type, id);
        this.container = container;
        this.layout = layout;
        this.values = values;
        checkContainerSize(container, layout.slots().size());
        container.startOpen(inventory.player);
        for (int index = 0; index < layout.slots().size(); index++) {
            MenuLayout.MenuSlot slot = layout.slots().get(index);
            addSlot(
                    slot.outputOnly()
                            ? new OutputSlot(container, index, slot.x(), slot.y())
                            : new Slot(container, index, slot.x(), slot.y()));
        }
        addStandardInventorySlots(inventory, layout.inventoryX(), layout.inventoryY());
        addDataSlots(values);
    }

    /** The client's copy, created from the open-menu packet. */
    static PlexusMenu forClient(MenuType<?> type, int id, Inventory inventory, MenuLayout layout) {
        return new PlexusMenu(
                type,
                id,
                inventory,
                new SimpleContainer(layout.slots().size()),
                new SimpleContainerData(layout.progressBars().size() * 2),
                layout);
    }

    /** Values of the progress bars read from block entity data, clamped to what the game sends. */
    static ContainerData values(MenuLayout layout, Supplier<DataTree> data) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                MenuLayout.ProgressBar bar = layout.progressBars().get(index / 2);
                int value = data.get().getInt(index % 2 == 0 ? bar.valueKey() : bar.maxKey(), 0);
                return Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value));
            }

            @Override
            public void set(int index, int value) {}

            @Override
            public int getCount() {
                return layout.progressBars().size() * 2;
            }
        };
    }

    public MenuLayout layout() {
        return layout;
    }

    /** How full progress bar {@code index} is, 0–1. */
    public float progress(int index) {
        int max = values.get(index * 2 + 1);
        return max <= 0 ? 0 : Math.max(0, Math.min(1, values.get(index * 2) / (float) max));
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        container.stopOpen(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = slots.get(slotIndex);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack moved = stack.copy();
        int own = layout.slots().size();
        if (slotIndex < own) {
            if (!moveItemStackTo(stack, own, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            boolean any = false;
            for (int index = 0; index < own && !stack.isEmpty(); index++) {
                if (!layout.slots().get(index).outputOnly()) {
                    any |= moveItemStackTo(stack, index, index + 1, false);
                }
            }
            if (!any) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return moved;
    }

    /** A slot players can only take from. */
    private static final class OutputSlot extends Slot {

        OutputSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }
}
