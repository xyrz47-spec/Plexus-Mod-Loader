package dev.plexusmodloader.game.layer.v26_3.content;

import dev.plexusmodloader.api.content.block.entity.BlockEntityDefinition;
import dev.plexusmodloader.api.content.block.entity.BlockEntityView;
import dev.plexusmodloader.api.content.block.entity.MenuLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A mod block entity with an inventory. As a game container it works with hoppers and comparators,
 * and the game drops its contents when the block is removed.
 */
final class PlexusContainerBlockEntity extends BaseContainerBlockEntity implements ModBlockEntity {

    private final BlockEntityData data;
    private final BlockEntityDefinition definition;
    private NonNullList<ItemStack> items;

    PlexusContainerBlockEntity(
            BlockEntityType<?> type, BlockPos pos, BlockState state, BlockEntityDefinition definition) {
        super(type, pos, state);
        this.definition = definition;
        this.data = new BlockEntityData(this, definition);
        this.items = NonNullList.withSize(definition.inventorySize(), ItemStack.EMPTY);
    }

    @Override
    public BlockEntityView view() {
        return data.view();
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable(definition.menuTitle());
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> newItems) {
        this.items = newItems;
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        if (definition.menu().isPresent() && getBlockState().getBlock() instanceof PlexusEntityBlock block) {
            MenuLayout layout = definition.menu().get();
            return new PlexusMenu(
                    block.menuType(),
                    id,
                    inventory,
                    this,
                    PlexusMenu.values(layout, () -> data.view().data()),
                    layout);
        }
        int rows = definition.inventoryRows();
        return new ChestMenu(PlexusEntityBlockMenus.type(rows), id, inventory, this, rows);
    }

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        data.save(output);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        data.load(input);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return data.syncToClients() ? ClientboundBlockEntityDataPacket.create(this) : null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return data.syncToClients() ? saveCustomOnly(registries) : super.getUpdateTag(registries);
    }
}
