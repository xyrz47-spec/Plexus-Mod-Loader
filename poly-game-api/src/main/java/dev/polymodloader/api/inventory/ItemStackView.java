package dev.polymodloader.api.inventory;

import dev.polymodloader.api.resource.ResourceId;
import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;

/**
 * An item and a count; a snapshot, so changing the inventory does not change it.
 *
 * @param item the item, {@code minecraft:air} for an empty slot
 * @param count how many, 0 for an empty slot
 */
@ApiStability(StabilityLevel.PREVIEW)
public record ItemStackView(ResourceId item, int count) {

    /** An empty slot. */
    public static final ItemStackView EMPTY = new ItemStackView(ResourceId.minecraft("air"), 0);

    /** Whether the slot is empty. */
    public boolean isEmpty() {
        return count == 0;
    }
}
