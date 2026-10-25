package dev.polymodloader.api.content.block.entity;

import dev.polymodloader.api.data.DataTree;
import dev.polymodloader.api.inventory.InventoryView;
import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;
import dev.polymodloader.api.world.BlockPos;
import dev.polymodloader.api.world.WorldView;
import java.util.Optional;

/**
 * A mod block entity. The data tree and inventory live as long as the block; the view itself is
 * valid only during the callback that handed it out.
 */
@ApiStability(StabilityLevel.PREVIEW)
public interface BlockEntityView {

    /** The world it is in. */
    WorldView world();

    /** Its position. */
    BlockPos pos();

    /** Its saved data; call {@link #markChanged()} after changing it. */
    DataTree data();

    /** Its inventory, when the definition has one. */
    Optional<InventoryView> inventory();

    /** Marks the data for saving and, for synchronised block entities, sends it to clients. */
    void markChanged();
}
