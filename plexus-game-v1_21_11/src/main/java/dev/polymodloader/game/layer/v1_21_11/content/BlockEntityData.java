package dev.polymodloader.game.layer.v1_21_11.content;

import dev.polymodloader.api.content.block.entity.BlockEntityDefinition;
import dev.polymodloader.api.content.block.entity.BlockEntityView;
import dev.polymodloader.api.data.DataTree;
import dev.polymodloader.api.inventory.InventoryView;
import dev.polymodloader.api.world.BlockPos;
import dev.polymodloader.api.world.WorldView;
import dev.polymodloader.game.layer.v1_21_11.Views;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** The mod data a Poly Mod Loader block entity carries, and the view handed to mods; shared by both classes. */
final class BlockEntityData {

    private static final String KEY = "poly_data";
    private static final String VERSION_KEY = "poly_data_version";

    private final BlockEntity owner;
    private final BlockEntityDefinition definition;
    private DataTree data = DataTree.create();

    BlockEntityData(BlockEntity owner, BlockEntityDefinition definition) {
        this.owner = owner;
        this.definition = definition;
    }

    boolean syncToClients() {
        return definition.syncToClients();
    }

    void save(ValueOutput output) {
        output.store(KEY, CompoundTag.CODEC, Nbt.toTag(data));
        if (definition.dataVersion() > 0) {
            output.putInt(VERSION_KEY, definition.dataVersion());
        }
    }

    void load(ValueInput input) {
        data = input.read(KEY, CompoundTag.CODEC).map(Nbt::fromTag).orElseGet(DataTree::create);
        int version = input.getIntOr(VERSION_KEY, 0);
        if (version < definition.dataVersion() && definition.migration().isPresent()) {
            definition.migration().get().migrate(data, version);
            owner.setChanged();
        }
    }

    void markChanged() {
        owner.setChanged();
        if (definition.syncToClients()
                && owner.getLevel() != null
                && !owner.getLevel().isClientSide()) {
            owner.getLevel()
                    .sendBlockUpdated(
                            owner.getBlockPos(), owner.getBlockState(), owner.getBlockState(), Block.UPDATE_ALL);
        }
    }

    BlockEntityView view() {
        return new View();
    }

    private final class View implements BlockEntityView {

        @Override
        public WorldView world() {
            return Views.world(owner.getLevel());
        }

        @Override
        public BlockPos pos() {
            return Views.pos(owner.getBlockPos());
        }

        @Override
        public DataTree data() {
            return data;
        }

        @Override
        public Optional<InventoryView> inventory() {
            return owner instanceof Container container ? Optional.of(Inventories.view(container)) : Optional.empty();
        }

        @Override
        public void markChanged() {
            BlockEntityData.this.markChanged();
        }
    }
}
