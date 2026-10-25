package com.example.compat.apievents;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Uses Fabric API as a Fabric mod does, where a host loader's patch moved the code Fabric API hooks (ADR-0021 G8): once
 * the server has started, a fake player breaks blocks (survival, with a block entity, cancelled by a listener,
 * creative) with {@code PlayerBlockBreakEvents} listeners, the mod's data pack has recipes behind Fabric resource
 * conditions, and an item's crafting remainder is read through the stack. Each result is one log line.
 */
public final class ApiEvents implements ModInitializer {

    private static final String PREFIX = "[fabric_api_events] ";

    @Override
    public void onInitialize() {
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
            System.out.println(PREFIX + "PlayerBlockBreakEvents.BEFORE " + describe(state, blockEntity));
            // The cancel case: a listener refuses obsidian.
            return !state.is(Blocks.OBSIDIAN);
        });
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) ->
                System.out.println(PREFIX + "PlayerBlockBreakEvents.AFTER " + describe(state, blockEntity)));
        PlayerBlockBreakEvents.CANCELED.register((level, player, pos, state, blockEntity) ->
                System.out.println(PREFIX + "PlayerBlockBreakEvents.CANCELED " + describe(state, blockEntity)));
        ServerLifecycleEvents.SERVER_STARTED.register(ApiEvents::onServerStarted);
    }

    private static void onServerStarted(MinecraftServer server) {
        ServerLevel level = server.overworld();
        level.getChunk(0, 0);
        ServerPlayer player = fakePlayer(level);
        BlockPos pos = new BlockPos(1, level.getMinY() + 200, 1);
        breakBlock(level, player, pos, Blocks.STONE);
        breakBlock(level, player, pos, Blocks.CHEST);
        breakBlock(level, player, pos, Blocks.OBSIDIAN);
        player.setGameMode(GameType.CREATIVE);
        breakBlock(level, player, pos, Blocks.DIRT);
        level.removeBlock(pos, false);

        System.out.println(PREFIX + "recipe behind a met Fabric condition loaded: "
                + server.getRecipeManager().byKey(recipe("condition_met")).isPresent());
        System.out.println(PREFIX + "recipe behind an unmet Fabric condition loaded: "
                + server.getRecipeManager().byKey(recipe("condition_unmet")).isPresent());

        // The game's own crafting code (Fabric API redirects it to the stack-aware remainder; Forge's patch asks the
        // stack itself).
        try {
            ItemStack remainder = CraftingRecipe.defaultCraftingReminder(
                            CraftingInput.of(1, 1, List.of(new ItemStack(Items.LAVA_BUCKET))))
                    .getFirst();
            System.out.println(PREFIX + "crafting remainder of a lava bucket: "
                    + BuiltInRegistries.ITEM.getKey(remainder.getItem()));
        } catch (LinkageError e) {
            System.out.println(PREFIX + "crafting remainder of a lava bucket failed: " + e);
        }
    }

    private static void breakBlock(ServerLevel level, ServerPlayer player, BlockPos pos, Block block) {
        level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL);
        boolean destroyed = player.gameMode.destroyBlock(pos);
        System.out.println(PREFIX + player.gameMode.getGameModeForPlayer().getName() + " destroyBlock("
                + BuiltInRegistries.BLOCK.getKey(block) + ") = " + destroyed + ", block left: "
                + BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()));
    }

    private static String describe(BlockState state, BlockEntity blockEntity) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()) + ", block entity "
                + (blockEntity == null ? "none" : BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType()));
    }

    private static ResourceKey<net.minecraft.world.item.crafting.Recipe<?>> recipe(String name) {
        return ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath("fabric_api_events", name));
    }

    /** Fabric API's {@code FakePlayer.get(ServerLevel)}, through reflection: no stub of a game class subclass. */
    private static ServerPlayer fakePlayer(ServerLevel level) {
        try {
            return (ServerPlayer) Class.forName("net.fabricmc.fabric.api.entity.FakePlayer")
                    .getMethod("get", ServerLevel.class)
                    .invoke(null, level);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("FakePlayer.get failed", e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Fabric API has no FakePlayer.get(ServerLevel)", e);
        }
    }
}
