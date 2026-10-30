package dev.plexusmodloader.api.content.block;

import dev.plexusmodloader.api.entity.EntityView;
import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;
import dev.plexusmodloader.api.world.BlockPos;
import dev.plexusmodloader.api.world.InteractionResult;
import dev.plexusmodloader.api.world.WorldView;

/**
 * Code that runs when something happens to a mod block. Every method has a default that keeps the
 * game's normal behaviour, so implement only what the block needs.
 *
 * <p><b>Thread:</b> the world's thread. Callbacks run on both the client and the server copy of the
 * world unless stated otherwise; check {@link WorldView#isClientSide()} before changing state.
 * <b>Failure:</b> exceptions propagate into the game like exceptions from the game's own blocks.
 */
@ApiStability(StabilityLevel.PREVIEW)
public interface BlockBehavior {

    /** A player used the block with an empty hand. */
    default InteractionResult onUse(BlockUse use) {
        return InteractionResult.PASS;
    }

    /** An entity walks on top of the block. */
    default void onSteppedOn(WorldView world, BlockPos pos, EntityView entity) {}

    /** The block was placed by an entity (server and client). */
    default void onPlaced(WorldView world, BlockPos pos, EntityView placer) {}

    /**
     * A random tick reached the block (server only, about every 68 seconds per block at the default
     * {@code randomTickSpeed}). Requires {@code BlockDefinition.Builder.randomTicks()}.
     */
    default void onRandomTick(WorldView world, BlockPos pos) {}

    /** A neighbouring block changed (server and client); typical for redstone-like reactions. */
    default void onNeighborChanged(WorldView world, BlockPos pos) {}
}
