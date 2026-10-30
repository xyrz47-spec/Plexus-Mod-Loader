package dev.plexusmodloader.api.content.block.state;

import dev.plexusmodloader.api.resource.ResourceId;
import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;
import java.util.Optional;

/** A block together with the values of its properties; an immutable snapshot. */
@ApiStability(StabilityLevel.PREVIEW)
public interface BlockStateView {

    /** The block, for example {@code minecraft:furnace}. */
    ResourceId block();

    /** Whether the block has a property with this name and value type. */
    boolean has(BlockProperty<?> property);

    /**
     * The property's value.
     *
     * @throws IllegalArgumentException when the block has no such property
     */
    <T> T get(BlockProperty<T> property);

    /** The property's value, or empty when the block has no such property. */
    default <T> Optional<T> find(BlockProperty<T> property) {
        return has(property) ? Optional.of(get(property)) : Optional.empty();
    }

    /**
     * This state with one property changed; this snapshot stays as it is. It does not change the
     * world: use {@link dev.plexusmodloader.api.world.WorldView#setProperty} for that.
     *
     * @throws IllegalArgumentException when the block has no such property or the value is not allowed
     */
    <T> BlockStateView with(BlockProperty<T> property, T value);
}
