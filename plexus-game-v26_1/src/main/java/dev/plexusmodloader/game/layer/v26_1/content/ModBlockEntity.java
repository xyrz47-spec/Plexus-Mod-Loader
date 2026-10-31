package dev.plexusmodloader.game.layer.v26_1.content;

import dev.plexusmodloader.api.content.block.entity.BlockEntityView;

/** A block entity created for a mod block; implemented by both Plexus Mod Loader block entity classes. */
public interface ModBlockEntity {

    /** A view for callbacks. */
    BlockEntityView view();
}
