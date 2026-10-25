package dev.polymodloader.game.layer.v26_1.content;

import dev.polymodloader.api.content.block.entity.BlockEntityView;

/** A block entity created for a mod block; implemented by both Poly Mod Loader block entity classes. */
public interface ModBlockEntity {

    /** A view for callbacks. */
    BlockEntityView view();
}
