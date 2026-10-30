package dev.plexusmodloader.api.content.block;

import dev.plexusmodloader.api.content.block.entity.BlockEntityDefinition;
import dev.plexusmodloader.api.content.block.state.BlockProperty;
import dev.plexusmodloader.api.content.block.state.BlockStateView;
import dev.plexusmodloader.api.content.item.ItemDefinition;
import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * Properties of a mod block. Created with {@link #builder()}; immutable.
 *
 * @param hardness time factor to break the block; {@code -1} is unbreakable
 * @param explosionResistance resistance to explosions
 * @param requiresCorrectTool whether the block drops only when mined with the right tool (tool tags
 *     such as {@code minecraft:mineable/pickaxe} come from the mod's data pack)
 * @param sound sound type
 * @param lightLevel emitted light, 0–15
 * @param friction slipperiness; the game's default is 0.6, ice is 0.98
 * @param collision whether entities collide with the block
 * @param occludes whether the block hides the faces of its neighbours; turn off for see-through blocks
 * @param behavior interaction callbacks
 * @param item the block item to declare with the same ID, or empty for no item
 * @param properties block state properties, in order
 * @param placementFacing a horizontal direction property set on placement so the block faces the
 *     player, or empty
 * @param randomTicks whether the block receives random ticks ({@link BlockBehavior#onRandomTick})
 * @param blockEntity per-position saved state, or empty
 * @param lightByState emitted light computed from the state (0–15), overriding {@code lightLevel}; for
 *     example a lamp that glows only when {@code lit=true}
 * @param shape the outline the player aims at, by state, or empty for a full block
 * @param collisionShape what entities collide with, by state, or empty for the same as {@code shape};
 *     ignored without {@code collision}
 */
@ApiStability(StabilityLevel.PREVIEW)
public record BlockDefinition(
        float hardness,
        float explosionResistance,
        boolean requiresCorrectTool,
        BlockSound sound,
        int lightLevel,
        float friction,
        boolean collision,
        boolean occludes,
        BlockBehavior behavior,
        Optional<ItemDefinition> item,
        List<BlockProperty<?>> properties,
        Optional<BlockProperty.DirectionProperty> placementFacing,
        boolean randomTicks,
        Optional<BlockEntityDefinition> blockEntity,
        Optional<ToIntFunction<BlockStateView>> lightByState,
        Optional<Function<BlockStateView, BlockShape>> shape,
        Optional<Function<BlockStateView, BlockShape>> collisionShape) {

    public BlockDefinition {
        if (hardness < 0 && hardness != -1) {
            throw new IllegalArgumentException("hardness must be >= 0, or -1 for unbreakable: " + hardness);
        }
        if (explosionResistance < 0) {
            throw new IllegalArgumentException("explosionResistance must be >= 0: " + explosionResistance);
        }
        if (lightLevel < 0 || lightLevel > 15) {
            throw new IllegalArgumentException("lightLevel must be 0-15: " + lightLevel);
        }
        if (friction <= 0 || friction > 1) {
            throw new IllegalArgumentException("friction must be in (0, 1]: " + friction);
        }
        Objects.requireNonNull(sound, "sound");
        Objects.requireNonNull(behavior, "behavior");
        Objects.requireNonNull(item, "item");
        properties = List.copyOf(properties);
        Objects.requireNonNull(placementFacing, "placementFacing");
        Objects.requireNonNull(blockEntity, "blockEntity");
        Objects.requireNonNull(lightByState, "lightByState");
        Objects.requireNonNull(shape, "shape");
        Objects.requireNonNull(collisionShape, "collisionShape");
        var names = new HashSet<String>();
        for (BlockProperty<?> property : properties) {
            if (!names.add(property.name())) {
                throw new IllegalArgumentException("Block property " + property.name() + " is declared twice");
            }
        }
        if (placementFacing.isPresent()) {
            if (!properties.contains(placementFacing.get())) {
                throw new IllegalArgumentException("The placement facing property must be one of the properties");
            }
            if (!placementFacing.get().horizontalOnly()) {
                throw new IllegalArgumentException("The placement facing property must be horizontal");
            }
        }
    }

    /** A builder with the game's defaults for a plain stone-like block that has a block item. */
    public static Builder builder() {
        return new Builder();
    }

    /** Builds a {@link BlockDefinition}. */
    public static final class Builder {

        private float hardness = 1.5f;
        private float explosionResistance = 6.0f;
        private boolean requiresCorrectTool;
        private BlockSound sound = BlockSound.STONE;
        private int lightLevel;
        private float friction = 0.6f;
        private boolean collision = true;
        private boolean occludes = true;
        private BlockBehavior behavior = new BlockBehavior() {};
        private Optional<ItemDefinition> item =
                Optional.of(ItemDefinition.builder().build());

        private final List<BlockProperty<?>> properties = new ArrayList<>();
        private Optional<BlockProperty.DirectionProperty> placementFacing = Optional.empty();
        private boolean randomTicks;
        private Optional<BlockEntityDefinition> blockEntity = Optional.empty();
        private Optional<ToIntFunction<BlockStateView>> lightByState = Optional.empty();
        private Optional<Function<BlockStateView, BlockShape>> shape = Optional.empty();
        private Optional<Function<BlockStateView, BlockShape>> collisionShape = Optional.empty();

        private Builder() {}

        /** Sets hardness and explosion resistance (stone is 1.5 / 6.0). */
        public Builder strength(float hardness, float explosionResistance) {
            this.hardness = hardness;
            this.explosionResistance = explosionResistance;
            return this;
        }

        /** The block drops only when mined with the correct tool. */
        public Builder requiresCorrectTool() {
            this.requiresCorrectTool = true;
            return this;
        }

        /** Sets the sound type. */
        public Builder sound(BlockSound sound) {
            this.sound = sound;
            return this;
        }

        /** Sets the emitted light level, 0–15. */
        public Builder lightLevel(int lightLevel) {
            this.lightLevel = lightLevel;
            return this;
        }

        /**
         * Computes the emitted light from each state, for example
         * {@code state -> state.get(LIT) ? 15 : 0}. The game evaluates it once per state while the
         * block is created; results outside 0–15 are clamped.
         */
        public Builder lightLevel(ToIntFunction<BlockStateView> byState) {
            this.lightByState = Optional.of(byState);
            return this;
        }

        /** Sets the friction (0.6 default, 0.98 ice). */
        public Builder friction(float friction) {
            this.friction = friction;
            return this;
        }

        /** Entities pass through the block. */
        public Builder noCollision() {
            this.collision = false;
            return this;
        }

        /** The block does not hide its neighbours' faces (glass-like blocks). */
        public Builder noOcclusion() {
            this.occludes = false;
            return this;
        }

        /** Sets the interaction callbacks. */
        public Builder behavior(BlockBehavior behavior) {
            this.behavior = behavior;
            return this;
        }

        /** Sets the properties of the block item declared with the block. */
        public Builder item(ItemDefinition item) {
            this.item = Optional.of(item);
            return this;
        }

        /** Declares no block item. */
        public Builder noItem() {
            this.item = Optional.empty();
            return this;
        }

        /** Adds block state properties; their default values form the default state. */
        public Builder properties(BlockProperty<?>... added) {
            properties.addAll(List.of(added));
            return this;
        }

        /** Adds {@code facing} as a property and sets it on placement so the block faces the player. */
        public Builder facesPlayerWhenPlaced(BlockProperty.DirectionProperty facing) {
            if (!properties.contains(facing)) {
                properties.add(facing);
            }
            this.placementFacing = Optional.of(facing);
            return this;
        }

        /** The block receives random ticks. */
        public Builder randomTicks() {
            this.randomTicks = true;
            return this;
        }

        /** Gives the block per-position saved state. */
        public Builder blockEntity(BlockEntityDefinition definition) {
            this.blockEntity = Optional.of(definition);
            return this;
        }

        /**
         * The block's outline and collision: a slab, a post, a pipe. The game also uses it to hide only
         * the neighbour faces the shape covers. Block models are not changed; draw the model to match.
         */
        public Builder shape(BlockShape shape) {
            Objects.requireNonNull(shape, "shape");
            return shape(state -> shape);
        }

        /**
         * The block's outline and collision computed from the state, for example turned with {@link
         * BlockShape#facing} by a facing property. Called once per state when the game starts.
         */
        public Builder shape(Function<BlockStateView, BlockShape> byState) {
            this.shape = Optional.of(byState);
            return this;
        }

        /** What entities collide with, when it differs from {@link #shape}: a low fence-like box, for example. */
        public Builder collisionShape(BlockShape shape) {
            Objects.requireNonNull(shape, "shape");
            return collisionShape(state -> shape);
        }

        /** What entities collide with, computed from the state. Called once per state when the game starts. */
        public Builder collisionShape(Function<BlockStateView, BlockShape> byState) {
            this.collisionShape = Optional.of(byState);
            return this;
        }

        /** Creates the definition; invalid values throw {@link IllegalArgumentException}. */
        public BlockDefinition build() {
            return new BlockDefinition(
                    hardness,
                    explosionResistance,
                    requiresCorrectTool,
                    sound,
                    lightLevel,
                    friction,
                    collision,
                    occludes,
                    behavior,
                    item,
                    properties,
                    placementFacing,
                    randomTicks,
                    blockEntity,
                    lightByState,
                    shape,
                    collisionShape);
        }
    }
}
