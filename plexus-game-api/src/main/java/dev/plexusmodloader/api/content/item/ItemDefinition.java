package dev.plexusmodloader.api.content.item;

import dev.plexusmodloader.api.content.CreativeTab;
import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;
import java.util.Objects;
import java.util.Optional;

/**
 * Properties of a mod item or block item. Created with {@link #builder()}; immutable.
 *
 * @param maxStackSize 1–99; items with durability always stack to 1
 * @param durability uses before breaking, 0 for none
 * @param fireResistant whether the dropped item survives fire and lava
 * @param rarity name colour
 * @param food what eating it does, or empty when it is not food
 * @param creativeTab creative inventory tab to list it in, or empty
 * @param behavior use callbacks; ignored for block items
 */
@ApiStability(StabilityLevel.PREVIEW)
public record ItemDefinition(
        int maxStackSize,
        int durability,
        boolean fireResistant,
        ItemRarity rarity,
        Optional<FoodDefinition> food,
        Optional<CreativeTab> creativeTab,
        ItemBehavior behavior) {

    public ItemDefinition {
        if (maxStackSize < 1 || maxStackSize > 99) {
            throw new IllegalArgumentException("maxStackSize must be 1-99: " + maxStackSize);
        }
        if (durability < 0) {
            throw new IllegalArgumentException("durability must be >= 0: " + durability);
        }
        if (durability > 0 && maxStackSize != 1) {
            throw new IllegalArgumentException("items with durability must have maxStackSize 1");
        }
        Objects.requireNonNull(rarity, "rarity");
        Objects.requireNonNull(food, "food");
        Objects.requireNonNull(creativeTab, "creativeTab");
        Objects.requireNonNull(behavior, "behavior");
    }

    /** A builder for a common item stacking to 64, listed in no tab. */
    public static Builder builder() {
        return new Builder();
    }

    /** Builds an {@link ItemDefinition}. */
    public static final class Builder {

        private int maxStackSize = 64;
        private int durability;
        private boolean fireResistant;
        private ItemRarity rarity = ItemRarity.COMMON;
        private Optional<FoodDefinition> food = Optional.empty();
        private Optional<CreativeTab> creativeTab = Optional.empty();
        private ItemBehavior behavior = new ItemBehavior() {};

        private Builder() {}

        /** Sets the maximum stack size, 1–99. */
        public Builder maxStackSize(int maxStackSize) {
            this.maxStackSize = maxStackSize;
            return this;
        }

        /** Gives the item durability; also sets the stack size to 1. */
        public Builder durability(int durability) {
            this.durability = durability;
            this.maxStackSize = 1;
            return this;
        }

        /** The dropped item survives fire and lava. */
        public Builder fireResistant() {
            this.fireResistant = true;
            return this;
        }

        /** Sets the name colour. */
        public Builder rarity(ItemRarity rarity) {
            this.rarity = rarity;
            return this;
        }

        /** Makes the item edible. */
        public Builder food(FoodDefinition food) {
            this.food = Optional.of(food);
            return this;
        }

        /** Lists the item at the end of a creative inventory tab. */
        public Builder creativeTab(CreativeTab tab) {
            this.creativeTab = Optional.of(tab);
            return this;
        }

        /** Sets the use callbacks. */
        public Builder behavior(ItemBehavior behavior) {
            this.behavior = behavior;
            return this;
        }

        /** Creates the definition; invalid values throw {@link IllegalArgumentException}. */
        public ItemDefinition build() {
            return new ItemDefinition(maxStackSize, durability, fireResistant, rarity, food, creativeTab, behavior);
        }
    }
}
