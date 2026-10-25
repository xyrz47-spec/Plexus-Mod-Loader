package dev.polymodloader.compat.forge;

import dev.polymodloader.compat.bridge.InjectionPointReplacement;
import java.util.List;

/**
 * Where MinecraftForge's patch moved vanilla calls that other loaders' Mixins inject at (ADR-0021 G8), for {@link
 * ForgeGameHost#injectionPointReplacements()}. Each was checked with {@code javap} against the vanilla and the patched
 * classes of the pinned Forge releases 26.1-62.0.9 and 26.2-65.1.5, and {@code ForgeInjectionPointsTest} checks them
 * against a patched client jar. The bridge that uses them checks the patched classes again before it moves anything.
 */
final class ForgeInjectionPoints {

    private static final String GAME_MODE = "net/minecraft/server/level/ServerPlayerGameMode";

    private ForgeInjectionPoints() {}

    static List<InjectionPointReplacement> all() {
        return List.of(
                // Bootstrap.bootStrap calls GameData.vanillaSnapshot() at the end where vanilla calls wrapStreams()
                // (Forge wraps the streams elsewhere): the point at which Fabric API's registry synchronisation starts.
                new InjectionPointReplacement(
                        "net/minecraft/server/Bootstrap",
                        "bootStrap",
                        "Lnet/minecraft/server/Bootstrap;wrapStreams()V",
                        "Lnet/minecraftforge/registries/GameData;vanillaSnapshot()V"),
                // ServerPlayerGameMode.destroyBlock: vanilla calls Block.playerWillDestroy after the permission checks,
                // then removes the block. Forge calls it inside BlockState.onDestroyedByPlayer, from its new
                // removeBlock(BlockPos, boolean). The first call after the same checks, before anything changes (the
                // item, the block), is ServerPlayer.preventsBlockDrops(); the locals there (state, blockEntity) are
                // vanilla's. Fabric API's PlayerBlockBreakEvents.BEFORE (cancellable) runs there.
                new InjectionPointReplacement(
                        GAME_MODE,
                        "destroyBlock",
                        "Lnet/minecraft/world/level/block/Block;playerWillDestroy(Lnet/minecraft/world/level/Level;"
                                + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;"
                                + "Lnet/minecraft/world/entity/player/Player;)"
                                + "Lnet/minecraft/world/level/block/state/BlockState;",
                        "Lnet/minecraft/server/level/ServerPlayer;preventsBlockDrops()Z"),
                // Vanilla calls Block.destroy when removeBlock removed the block. Forge's removeBlock(BlockPos, Z)
                // calls it exactly then and returns whether it removed the block; destroyBlock calls it twice
                // (creative and survival). Fabric API's PlayerBlockBreakEvents.AFTER runs after each call that
                // returned true.
                new InjectionPointReplacement(
                        GAME_MODE,
                        "destroyBlock",
                        "Lnet/minecraft/world/level/block/Block;destroy(Lnet/minecraft/world/level/LevelAccessor;"
                                + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V",
                        "L" + GAME_MODE + ";removeBlock(Lnet/minecraft/core/BlockPos;Z)Z",
                        InjectionPointReplacement.Placement.AFTER_TRUE_RESULT));
    }
}
