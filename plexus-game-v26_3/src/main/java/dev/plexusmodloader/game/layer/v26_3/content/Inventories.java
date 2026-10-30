package dev.plexusmodloader.game.layer.v26_3.content;

import dev.plexusmodloader.api.inventory.InventoryView;
import dev.plexusmodloader.api.inventory.ItemStackRef;
import dev.plexusmodloader.api.inventory.ItemStackView;
import dev.plexusmodloader.api.resource.ResourceId;
import dev.plexusmodloader.game.layer.v26_3.Views;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** {@link InventoryView} over any game container. */
public final class Inventories {

    private Inventories() {}

    public static InventoryView view(Container container) {
        return new ContainerView(container);
    }

    static ItemStackView stack(ItemStack stack) {
        return ItemComponents.snapshot(stack);
    }

    private record ContainerView(Container container) implements InventoryView {

        @Override
        public int size() {
            return container.getContainerSize();
        }

        @Override
        public ItemStackView get(int slot) {
            checkSlot(slot);
            return stack(container.getItem(slot));
        }

        @Override
        public void set(int slot, ResourceId item, int count) {
            checkSlot(slot);
            if (count == 0) {
                container.setItem(slot, ItemStack.EMPTY);
                container.setChanged();
                return;
            }
            Item gameItem = Views.item(item);
            if (count < 0 || count > gameItem.getDefaultMaxStackSize()) {
                throw new IllegalArgumentException(
                        "count must be 0-" + gameItem.getDefaultMaxStackSize() + " for " + item + ": " + count);
            }
            container.setItem(slot, new ItemStack(gameItem, count));
            container.setChanged();
        }

        @Override
        public int add(ResourceId item, int count) {
            Item gameItem = Views.item(item);
            if (count < 0) {
                throw new IllegalArgumentException("count must be >= 0: " + count);
            }
            int left = count;
            int max = gameItem.getDefaultMaxStackSize();
            for (int slot = 0; slot < size() && left > 0; slot++) {
                ItemStack stack = container.getItem(slot);
                if (!stack.isEmpty() && stack.getItem() == gameItem && stack.getCount() < max) {
                    int moved = Math.min(left, max - stack.getCount());
                    stack.grow(moved);
                    left -= moved;
                }
            }
            for (int slot = 0; slot < size() && left > 0; slot++) {
                if (container.getItem(slot).isEmpty()) {
                    int moved = Math.min(left, max);
                    container.setItem(slot, new ItemStack(gameItem, moved));
                    left -= moved;
                }
            }
            container.setChanged();
            return left;
        }

        @Override
        public int count(ResourceId item) {
            Item gameItem = Views.item(item);
            int total = 0;
            for (int slot = 0; slot < size(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (stack.getItem() == gameItem) {
                    total += stack.getCount();
                }
            }
            return total;
        }

        @Override
        public ItemStackRef stackAt(int slot) {
            checkSlot(slot);
            return ItemComponents.ref(container.getItem(slot), container::setChanged);
        }

        @Override
        public void clear() {
            container.clearContent();
            container.setChanged();
        }

        private void checkSlot(int slot) {
            if (slot < 0 || slot >= size()) {
                throw new IndexOutOfBoundsException("slot " + slot + " of " + size());
            }
        }
    }
}
