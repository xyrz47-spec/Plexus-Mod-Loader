package dev.plexusmodloader.game.layer.v1_21_11.content;

import dev.plexusmodloader.api.content.block.BlockDefinition;
import dev.plexusmodloader.api.content.block.BlockUse;
import dev.plexusmodloader.api.content.block.entity.BlockEntityDefinition;
import dev.plexusmodloader.api.content.block.entity.BlockEntityTicker;
import dev.plexusmodloader.game.layer.v1_21_11.Views;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** A mod block with a block entity. */
final class PlexusEntityBlock extends PlexusBlock implements EntityBlock {

    private BlockEntityType<? extends BlockEntity> type;
    private MenuType<?> menuType;

    PlexusEntityBlock(Properties properties, BlockDefinition definition) {
        super(properties, definition);
    }

    void setType(BlockEntityType<? extends BlockEntity> type) {
        this.type = type;
    }

    void setMenuType(MenuType<?> menuType) {
        this.menuType = menuType;
    }

    MenuType<?> menuType() {
        return menuType;
    }

    BlockEntityDefinition entityDefinition() {
        return definition().blockEntity().orElseThrow();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return entityDefinition().inventorySize() > 0
                ? new PlexusContainerBlockEntity(type, pos, state, entityDefinition())
                : new PlexusBlockEntity(type, pos, state, entityDefinition());
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> requested) {
        if (level.isClientSide()
                || requested != type
                || entityDefinition().ticker().isEmpty()) {
            return null;
        }
        BlockEntityTicker ticker = entityDefinition().ticker().get();
        return (tickLevel, pos, tickState, entity) -> ticker.tick(((ModBlockEntity) entity).view());
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        dev.plexusmodloader.api.world.InteractionResult result =
                behavior().onUse(new BlockUse(Views.world(level), Views.pos(pos), Views.player(player)));
        if (result != dev.plexusmodloader.api.world.InteractionResult.PASS) {
            return Interactions.toGame(result, () -> InteractionResult.PASS);
        }
        if (entityDefinition().inventorySize() == 0) {
            return super.useWithoutItem(state, level, pos, player, hit);
        }
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof PlexusContainerBlockEntity container) {
            player.openMenu(container);
        }
        return InteractionResult.SUCCESS;
    }
}
