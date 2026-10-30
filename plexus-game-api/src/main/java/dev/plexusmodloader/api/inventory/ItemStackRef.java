package dev.plexusmodloader.api.inventory;

import dev.plexusmodloader.api.content.item.ItemComponentKey;
import dev.plexusmodloader.api.resource.ResourceId;
import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;
import java.util.Optional;

/**
 * A live item stack in a slot or a hand: changes apply to the game's stack directly. Valid during
 * the callback that handed it out; same thread rules as {@link dev.plexusmodloader.api.world.WorldView}.
 * Change stacks on the server; client changes are overwritten by the server's copy.
 */
@ApiStability(StabilityLevel.PREVIEW)
public interface ItemStackRef {

    /** The item, {@code minecraft:air} when empty. */
    ResourceId item();

    /** How many, 0 when empty. */
    int count();

    /** Whether the stack is empty. */
    default boolean isEmpty() {
        return count() == 0;
    }

    /**
     * Whether the stack's item is in an item tag, for example {@code #minecraft:logs}; {@code false}
     * for an empty stack.
     *
     * @throws IllegalArgumentException when {@code tag} is not an item tag
     */
    boolean isIn(dev.plexusmodloader.api.tag.TagKey tag);

    /**
     * Changes the count; 0 empties the stack.
     *
     * @throws IllegalArgumentException for a negative count or one above the stack size
     */
    void setCount(int count);

    /** The component's value, or empty when the stack does not have it. */
    <T> Optional<T> get(ItemComponentKey<T> key);

    /**
     * Sets the component's value.
     *
     * @throws IllegalArgumentException for a key that is not registered
     * @throws IllegalStateException on an empty stack
     */
    <T> void set(ItemComponentKey<T> key, T value);

    /** Removes the component. */
    void remove(ItemComponentKey<?> key);

    /** The name given with an anvil or {@link #setCustomName}, if any. */
    Optional<String> customName();

    /** Gives the stack a name, or removes it with {@code null}. */
    void setCustomName(String name);

    /** Durability used up, 0 for undamaged or undamageable items. */
    int damage();

    /** Durability, 0 for undamageable items. */
    int maxDamage();

    /** Sets the used durability, clamped to {@code [0, maxDamage]}. */
    void setDamage(int damage);

    /** A snapshot of item and count. */
    ItemStackView snapshot();
}
