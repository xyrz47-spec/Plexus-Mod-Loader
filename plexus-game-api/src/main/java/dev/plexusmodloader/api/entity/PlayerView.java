package dev.plexusmodloader.api.entity;

import dev.plexusmodloader.api.inventory.InventoryView;
import dev.plexusmodloader.api.inventory.ItemStackRef;
import dev.plexusmodloader.api.resource.ResourceId;
import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;
import dev.plexusmodloader.api.world.BlockPos;

/** A player. Same thread and lifetime rules as {@link dev.plexusmodloader.api.world.WorldView}. */
@ApiStability(StabilityLevel.PREVIEW)
public interface PlayerView extends LivingEntityView {

    /** Shows {@code text} in the player's chat. On the client this is only shown locally. */
    void sendMessage(String text);

    /**
     * Gives the player {@code count} of {@code item}; what does not fit is dropped at their feet.
     *
     * @throws IllegalArgumentException when {@code item} is not a registered item or {@code count < 1}
     * @throws IllegalStateException on the client side
     */
    void give(ResourceId item, int count);

    /** How many of {@code item} the player carries in their inventory. */
    int countItem(ResourceId item);

    /** The item in the main hand, {@code minecraft:air} when empty. */
    ResourceId mainHandItem();

    /** The live stack in the main hand. */
    ItemStackRef mainHandStack();

    /** The player's inventory: 36 main slots (hotbar 0–8), then armour and off hand. */
    InventoryView inventory();

    /**
     * Opens the menu of the block entity at {@code pos} (a chest-style or laid-out mod menu, or a
     * game container such as a furnace) for this player.
     *
     * @return whether the block has a menu
     * @throws IllegalStateException on the client side
     */
    boolean openMenu(BlockPos pos);

    /**
     * Whether the player has completed an advancement, such as {@code minecraft:story/mine_stone}.
     *
     * @throws IllegalArgumentException for an unknown advancement
     * @throws IllegalStateException on the client side
     */
    boolean hasAdvancement(ResourceId advancement);

    /**
     * Completes every criterion of an advancement, with its rewards and announcement.
     *
     * @return whether anything changed
     */
    boolean grantAdvancement(ResourceId advancement);

    /**
     * Clears every criterion of an advancement.
     *
     * @return whether anything changed
     */
    boolean revokeAdvancement(ResourceId advancement);

    /**
     * Whether the player has the permission: the first deciding provider, else the node's default
     * operator level. Server side only.
     */
    boolean hasPermission(dev.plexusmodloader.api.permission.PermissionNode node);
}
