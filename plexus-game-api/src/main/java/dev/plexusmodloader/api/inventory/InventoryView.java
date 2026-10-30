package dev.plexusmodloader.api.inventory;

import dev.plexusmodloader.api.resource.ResourceId;
import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/**
 * Slots of an inventory, such as a mod block entity's. Changes are saved with the world. Same
 * thread rules as {@link dev.plexusmodloader.api.world.WorldView}.
 */
@ApiStability(StabilityLevel.PREVIEW)
public interface InventoryView {

    /** Number of slots. */
    int size();

    /** The stack in {@code slot}; {@link IndexOutOfBoundsException} for a bad slot. */
    ItemStackView get(int slot);

    /**
     * Replaces the stack in {@code slot}; a count of 0 empties it.
     *
     * @throws IllegalArgumentException for an unknown item or a count above the item's stack size
     */
    void set(int slot, ResourceId item, int count);

    /**
     * Adds items to existing stacks first, then to empty slots.
     *
     * @return how many did not fit
     */
    int add(ResourceId item, int count);

    /** How many of {@code item} the inventory holds. */
    int count(ResourceId item);

    /** The live stack in {@code slot}, to read or change its components. */
    ItemStackRef stackAt(int slot);

    /** Empties every slot. */
    void clear();
}
