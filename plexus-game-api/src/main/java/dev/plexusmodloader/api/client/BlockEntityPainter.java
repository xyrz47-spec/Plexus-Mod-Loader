package dev.plexusmodloader.api.client;

import dev.plexusmodloader.api.content.block.entity.BlockEntityView;
import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/**
 * Draws extra things for a mod block entity every frame, on the client (render) thread, from the
 * client's copy of the block entity (use {@code syncToClients} for data the drawing needs). The
 * block's own model is drawn by the game as usual.
 */
@ApiStability(StabilityLevel.PREVIEW)
@FunctionalInterface
public interface BlockEntityPainter {

    /** Draws for one block entity; {@code canvas} is placed at the block's lowest corner. */
    void paint(BlockEntityView entity, BlockEntityCanvas canvas);
}
