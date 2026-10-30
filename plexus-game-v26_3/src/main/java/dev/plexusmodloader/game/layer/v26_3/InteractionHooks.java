package dev.plexusmodloader.game.layer.v26_3;

import dev.plexusmodloader.api.entity.PlayerView;
import dev.plexusmodloader.api.event.EventKey;
import dev.plexusmodloader.api.event.EventResult;
import dev.plexusmodloader.api.event.block.BlockEvents;
import dev.plexusmodloader.api.event.interaction.InteractionEvents;
import dev.plexusmodloader.api.inventory.ItemStackRef;
import dev.plexusmodloader.game.layer.v26_3.content.BlockStates;
import dev.plexusmodloader.game.layer.v26_3.content.Interactions;
import dev.plexusmodloader.game.layer.v26_3.content.ItemComponents;
import dev.plexusmodloader.game.version.layer.GameLayerContext;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Hooks for the player interaction events ({@link InteractionEvents}) and the cancellable block
 * events ({@link BlockEvents#BREAK}, {@link BlockEvents#PLACE}, {@link BlockEvents#PLACED}). The
 * transformation rules are in {@code Minecraft26_3Adapter}; each parameter list mirrors its target
 * (verified with {@code javap} against the 26.3 client jar). All events fire on the server only.
 */
public final class InteractionHooks {

    private static final MethodHandle GAME_MODE_PLAYER;
    private static final MethodHandle GAME_MODE_LEVEL;
    private static final MethodHandle PLACE_BLOCK;

    static {
        try {
            // ServerPlayerGameMode keeps its player and level protected; BlockItem.placeBlock is
            // protected and overridden (beds, doors), so it is called virtually.
            MethodHandles.Lookup gameMode =
                    MethodHandles.privateLookupIn(ServerPlayerGameMode.class, MethodHandles.lookup());
            GAME_MODE_PLAYER = gameMode.findGetter(ServerPlayerGameMode.class, "player", ServerPlayer.class);
            GAME_MODE_LEVEL = gameMode.findGetter(ServerPlayerGameMode.class, "level", ServerLevel.class);
            PLACE_BLOCK = MethodHandles.privateLookupIn(BlockItem.class, MethodHandles.lookup())
                    .findVirtual(
                            BlockItem.class,
                            "placeBlock",
                            MethodType.methodType(boolean.class, BlockPlaceContext.class, BlockState.class));
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /** A block a player placed on this thread, between placeBlock and the return of BlockItem.place. */
    private static final ThreadLocal<PendingPlace> PENDING_PLACE = new ThreadLocal<>();

    private record PendingPlace(net.minecraft.core.BlockPos pos, BlockState state, ServerPlayer player) {}

    private InteractionHooks() {}

    /**
     * Head of {@code ServerPlayerGameMode.useItemOn}: a decided {@code USE_BLOCK} result ends the
     * click; null lets the game go on.
     */
    public static InteractionResult onUseItemOn(
            ServerPlayerGameMode gameMode,
            ServerPlayer player,
            Level level,
            ItemStack stack,
            InteractionHand hand,
            BlockHitResult hit) {
        GameLayerContext context = listening(InteractionEvents.USE_BLOCK);
        if (context == null) {
            return null;
        }
        PlayerView view = Views.player(player);
        ItemStackRef ref = stackRef(player, stack);
        dev.plexusmodloader.api.world.BlockPos pos = Views.pos(hit.getBlockPos());
        dev.plexusmodloader.api.world.Direction face = BlockStates.fromGame(hit.getDirection());
        dev.plexusmodloader.api.entity.Hand polyHand = Interactions.hand(hand);
        return decided(
                player,
                context.events()
                        .publishUntilDecided(
                                InteractionEvents.USE_BLOCK,
                                listener -> listener.onUseBlock(view, polyHand, ref, pos, face),
                                dev.plexusmodloader.api.world.InteractionResult.PASS));
    }

    /** Head of {@code ServerPlayerGameMode.useItem}: a decided {@code USE_ITEM} result ends the use. */
    public static InteractionResult onUseItem(
            ServerPlayerGameMode gameMode, ServerPlayer player, Level level, ItemStack stack, InteractionHand hand) {
        GameLayerContext context = listening(InteractionEvents.USE_ITEM);
        if (context == null) {
            return null;
        }
        PlayerView view = Views.player(player);
        ItemStackRef ref = stackRef(player, stack);
        dev.plexusmodloader.api.entity.Hand polyHand = Interactions.hand(hand);
        return decided(
                player,
                context.events()
                        .publishUntilDecided(
                                InteractionEvents.USE_ITEM,
                                listener -> listener.onUseItem(view, polyHand, ref),
                                dev.plexusmodloader.api.world.InteractionResult.PASS));
    }

    /** Head of {@code Player.interactOn}: a decided {@code USE_ENTITY} result ends the click (server only). */
    public static InteractionResult onInteractOn(Player player, Entity target, InteractionHand hand, Vec3 location) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return null;
        }
        GameLayerContext context = listening(InteractionEvents.USE_ENTITY);
        if (context == null) {
            return null;
        }
        PlayerView view = Views.player(serverPlayer);
        ItemStackRef ref = stackRef(serverPlayer, serverPlayer.getItemInHand(hand));
        dev.plexusmodloader.api.entity.EntityView targetView = Views.entity(target);
        dev.plexusmodloader.api.entity.Hand polyHand = Interactions.hand(hand);
        return decided(
                serverPlayer,
                context.events()
                        .publishUntilDecided(
                                InteractionEvents.USE_ENTITY,
                                listener -> listener.onUseEntity(view, polyHand, ref, targetView),
                                dev.plexusmodloader.api.world.InteractionResult.PASS));
    }

    /** Head of {@code Player.attack}: {@code true} (skip the attack) when a listener denies it (server only). */
    public static boolean onAttack(Player player, Entity target) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        GameLayerContext context = listening(InteractionEvents.ATTACK_ENTITY);
        if (context == null) {
            return false;
        }
        PlayerView view = Views.player(serverPlayer);
        dev.plexusmodloader.api.entity.EntityView targetView = Views.entity(target);
        return context.events()
                        .publishUntilDecided(
                                InteractionEvents.ATTACK_ENTITY,
                                listener -> listener.onAttackEntity(view, targetView),
                                EventResult.PASS)
                == EventResult.DENY;
    }

    /**
     * Head of {@code ServerPlayerGameMode.destroyBlock(BlockPos)}: FALSE (not broken) when a {@code
     * BREAK} listener denies it, after telling the client the block entity is still there (the
     * game's caller resends the block itself); null lets the game go on.
     */
    public static Boolean onDestroyBlock(ServerPlayerGameMode gameMode, net.minecraft.core.BlockPos pos) {
        GameLayerContext context = listening(BlockEvents.BREAK);
        if (context == null) {
            return null;
        }
        ServerPlayer player = player(gameMode);
        ServerLevel level = level(gameMode);
        BlockState state = level.getBlockState(pos);
        dev.plexusmodloader.api.world.WorldView world = Views.world(level);
        dev.plexusmodloader.api.world.BlockPos polyPos = Views.pos(pos);
        dev.plexusmodloader.api.resource.ResourceId block = Views.blockId(state.getBlock());
        PlayerView view = Views.player(player);
        EventResult result = context.events()
                .publishUntilDecided(
                        BlockEvents.BREAK,
                        listener -> listener.onBlockBreak(world, polyPos, block, view),
                        EventResult.PASS);
        if (result != EventResult.DENY) {
            return null;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity != null && player.connection != null) {
            Packet<ClientGamePacketListener> update = blockEntity.getUpdatePacket();
            if (update != null) {
                player.connection.send(update);
            }
        }
        return Boolean.FALSE;
    }

    /**
     * Replaces the call to {@code placeBlock} in {@code BlockItem.place}: {@code false} (the game then
     * returns FAIL) when a {@code PLACE} listener denies it; otherwise the item's own placeBlock.
     */
    public static boolean onPlaceBlock(BlockItem item, BlockPlaceContext placeContext, BlockState state) {
        Level level = placeContext.getLevel();
        if (!(placeContext.getPlayer() instanceof ServerPlayer player) || level.isClientSide()) {
            return placeBlock(item, placeContext, state);
        }
        GameLayerContext context = LayerState.context().orElse(null);
        if (context == null) {
            return placeBlock(item, placeContext, state);
        }
        net.minecraft.core.BlockPos pos = placeContext.getClickedPos().immutable();
        if (context.events().hasListeners(BlockEvents.PLACE)) {
            dev.plexusmodloader.api.world.WorldView world = Views.world(level);
            dev.plexusmodloader.api.world.BlockPos polyPos = Views.pos(pos);
            dev.plexusmodloader.api.resource.ResourceId block = Views.blockId(state.getBlock());
            PlayerView view = Views.player(player);
            EventResult result = context.events()
                    .publishUntilDecided(
                            BlockEvents.PLACE,
                            listener -> listener.onBlockPlace(world, polyPos, block, view),
                            EventResult.PASS);
            if (result == EventResult.DENY) {
                // The client already placed the block and used the item in its prediction.
                resynchronise(player);
                return false;
            }
        }
        boolean placed = placeBlock(item, placeContext, state);
        if (placed && context.events().hasListeners(BlockEvents.PLACED)) {
            PENDING_PLACE.set(new PendingPlace(pos, state, player));
        }
        return placed;
    }

    /** Before the return of {@code BlockItem.place}: fires {@code PLACED} for a placement that succeeded. */
    public static InteractionResult onPlaceReturn(
            BlockItem item, BlockPlaceContext placeContext, InteractionResult returned) {
        PendingPlace pending = PENDING_PLACE.get();
        if (pending == null) {
            return returned;
        }
        PENDING_PLACE.remove();
        if (returned.consumesAction()) {
            LayerState.context().ifPresent(context -> {
                dev.plexusmodloader.api.world.WorldView world =
                        Views.world(pending.player().level());
                dev.plexusmodloader.api.world.BlockPos polyPos = Views.pos(pending.pos());
                dev.plexusmodloader.api.resource.ResourceId block =
                        Views.blockId(pending.state().getBlock());
                PlayerView view = Views.player(pending.player());
                context.events()
                        .publish(BlockEvents.PLACED, listener -> listener.onBlockPlaced(world, polyPos, block, view));
            });
        }
        return returned;
    }

    private static boolean placeBlock(BlockItem item, BlockPlaceContext placeContext, BlockState state) {
        try {
            return (boolean) PLACE_BLOCK.invokeExact(item, placeContext, state);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException("BlockItem.placeBlock threw a checked exception", e);
        }
    }

    private static ServerPlayer player(ServerPlayerGameMode gameMode) {
        try {
            return (ServerPlayer) GAME_MODE_PLAYER.invokeExact(gameMode);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException("Reading ServerPlayerGameMode.player failed", e);
        }
    }

    private static ServerLevel level(ServerPlayerGameMode gameMode) {
        try {
            return (ServerLevel) GAME_MODE_LEVEL.invokeExact(gameMode);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException("Reading ServerPlayerGameMode.level failed", e);
        }
    }

    /** The context when Poly Mod Loader runs and someone listens to {@code event}, else null. */
    private static GameLayerContext listening(EventKey<?> event) {
        GameLayerContext context = LayerState.context().orElse(null);
        return context != null && context.events().hasListeners(event) ? context : null;
    }

    private static ItemStackRef stackRef(ServerPlayer player, ItemStack stack) {
        return ItemComponents.ref(stack, () -> player.getInventory().setChanged());
    }

    /** The game result for a decided interaction, or null for PASS; resends the inventory after a decision. */
    private static InteractionResult decided(
            ServerPlayer player, dev.plexusmodloader.api.world.InteractionResult result) {
        if (result == dev.plexusmodloader.api.world.InteractionResult.PASS) {
            return null;
        }
        // The client may have predicted what the game would have done with the held item.
        resynchronise(player);
        return Interactions.toGame(result, () -> InteractionResult.PASS);
    }

    private static void resynchronise(ServerPlayer player) {
        if (player.connection != null) {
            player.containerMenu.sendAllDataToRemote();
        }
    }
}
