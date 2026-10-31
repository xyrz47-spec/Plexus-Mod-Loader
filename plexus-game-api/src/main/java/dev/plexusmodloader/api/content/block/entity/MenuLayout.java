package dev.plexusmodloader.api.content.block.entity;

import dev.plexusmodloader.api.resource.ResourceId;
import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The menu of a block entity with its own slot positions, like a furnace. Coordinates are GUI pixels
 * from the menu's top-left corner; a slot is 16×16 with its frame one pixel outside. The player's
 * inventory (27 + 9 slots) is placed at {@code inventoryX, inventoryY}.
 *
 * <p>Without a {@code background} texture Plexus Mod Loader draws a plain panel with slot frames. With one, the
 * texture ({@code <namespace>:textures/gui/...png}, 256×256 canvas) is drawn as is and must include
 * the slot frames.
 *
 * @param width menu width (176 for the game's menus)
 * @param height menu height (166 for the game's menus)
 * @param slots the block entity's slots, in inventory order
 * @param inventoryX left of the player's inventory
 * @param inventoryY top of the player's inventory
 * @param background texture, or empty for the plain panel
 * @param progressBars bars filled from block entity data
 */
@ApiStability(StabilityLevel.PREVIEW)
public record MenuLayout(
        int width,
        int height,
        List<MenuSlot> slots,
        int inventoryX,
        int inventoryY,
        Optional<ResourceId> background,
        List<ProgressBar> progressBars) {

    public MenuLayout {
        if (width < 176 || height < 166 || width > 256 || height > 256) {
            throw new IllegalArgumentException("Menu size must be 176-256 x 166-256: " + width + "x" + height);
        }
        slots = List.copyOf(slots);
        if (slots.isEmpty() || slots.size() > 54) {
            throw new IllegalArgumentException("A menu needs 1-54 slots: " + slots.size());
        }
        Objects.requireNonNull(background, "background");
        progressBars = List.copyOf(progressBars);
        if (progressBars.size() > 8) {
            throw new IllegalArgumentException("A menu can show at most 8 progress bars");
        }
    }

    /** A builder for a {@code width} × {@code height} menu with the inventory at the game's usual place. */
    public static Builder builder(int width, int height) {
        return new Builder(width, height);
    }

    /**
     * One slot.
     *
     * @param x left of the 16×16 item area
     * @param y top of the item area
     * @param outputOnly players can take items out but not put them in
     */
    public record MenuSlot(int x, int y, boolean outputOnly) {}

    /**
     * A bar filled in proportion to {@code data().getInt(valueKey) / data().getInt(maxKey)} of the
     * block entity. Values reach the client while the menu is open; they must fit in
     * {@code -32768..32767}.
     *
     * @param x left
     * @param y top
     * @param style how it looks
     * @param valueKey data key of the current value
     * @param maxKey data key of the full value; the bar is empty while it is 0
     */
    public record ProgressBar(int x, int y, ProgressStyle style, String valueKey, String maxKey) {
        public ProgressBar {
            Objects.requireNonNull(style, "style");
            Objects.requireNonNull(valueKey, "valueKey");
            Objects.requireNonNull(maxKey, "maxKey");
        }
    }

    /** Looks of a {@link ProgressBar}, taken from the game's furnace. */
    public enum ProgressStyle {
        /** 24×16 arrow filling left to right. */
        ARROW,
        /** 14×14 flame filling bottom to top. */
        FLAME
    }

    /** Builds a {@link MenuLayout}. */
    public static final class Builder {

        private final int width;
        private final int height;
        private final List<MenuSlot> slots = new ArrayList<>();
        private final List<ProgressBar> progressBars = new ArrayList<>();
        private int inventoryX = 8;
        private int inventoryY;
        private Optional<ResourceId> background = Optional.empty();

        private Builder(int width, int height) {
            this.width = width;
            this.height = height;
            this.inventoryY = height - 82;
        }

        /** Adds a slot. */
        public Builder slot(int x, int y) {
            slots.add(new MenuSlot(x, y, false));
            return this;
        }

        /** Adds a slot players can only take from. */
        public Builder outputSlot(int x, int y) {
            slots.add(new MenuSlot(x, y, true));
            return this;
        }

        /** Adds a {@code columns} × {@code rows} grid of slots with 18-pixel spacing. */
        public Builder grid(int x, int y, int columns, int rows) {
            for (int row = 0; row < rows; row++) {
                for (int column = 0; column < columns; column++) {
                    slot(x + column * 18, y + row * 18);
                }
            }
            return this;
        }

        /** Moves the player's inventory (default {@code 8, height - 82}). */
        public Builder playerInventory(int x, int y) {
            this.inventoryX = x;
            this.inventoryY = y;
            return this;
        }

        /** Draws {@code texture} instead of the plain panel. */
        public Builder background(ResourceId texture) {
            this.background = Optional.of(texture);
            return this;
        }

        /** Adds a progress bar. */
        public Builder progressBar(int x, int y, ProgressStyle style, String valueKey, String maxKey) {
            progressBars.add(new ProgressBar(x, y, style, valueKey, maxKey));
            return this;
        }

        /** Creates the layout; invalid values throw {@link IllegalArgumentException}. */
        public MenuLayout build() {
            return new MenuLayout(width, height, slots, inventoryX, inventoryY, background, progressBars);
        }
    }
}
