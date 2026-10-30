package dev.plexusmodloader.api.content.block.entity;

import dev.plexusmodloader.api.data.DataMigration;
import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;
import java.util.Objects;
import java.util.Optional;

/**
 * Gives a mod block per-position state that is saved with the world: a {@link
 * dev.plexusmodloader.api.data.DataTree}, an optional inventory, and an optional server tick.
 *
 * @param inventoryRows rows of 9 slots (1–6), or 0 for no inventory. Blocks with an inventory open
 *     the game's chest-style menu when used (unless the block behaviour returns something other
 *     than {@code PASS}), drop their contents when broken, and work with hoppers.
 * @param menuTitle translation key or plain text shown above the menu
 * @param ticker server-side code run every tick, or empty
 * @param syncToClients whether data changes reach clients (for rendering or client logic); the
 *     inventory is synchronised through the menu either way
 * @param menu a menu with its own slot layout, or empty; when present the inventory has one slot
 *     per layout slot and {@code inventoryRows} is 0
 * @param dataVersion the current version of the data tree; older saved data runs {@code migration}
 * @param migration upgrades older data, if any
 */
@ApiStability(StabilityLevel.PREVIEW)
public record BlockEntityDefinition(
        int inventoryRows,
        String menuTitle,
        Optional<BlockEntityTicker> ticker,
        boolean syncToClients,
        Optional<MenuLayout> menu,
        int dataVersion,
        Optional<DataMigration> migration) {

    public BlockEntityDefinition {
        if (inventoryRows < 0 || inventoryRows > 6) {
            throw new IllegalArgumentException("inventoryRows must be 0-6: " + inventoryRows);
        }
        Objects.requireNonNull(menuTitle, "menuTitle");
        Objects.requireNonNull(ticker, "ticker");
        Objects.requireNonNull(menu, "menu");
        Objects.requireNonNull(migration, "migration");
        if (dataVersion < 0) {
            throw new IllegalArgumentException("dataVersion must be >= 0: " + dataVersion);
        }
        if (menu.isPresent() && inventoryRows != 0) {
            throw new IllegalArgumentException("Use either chest rows or a menu layout, not both");
        }
    }

    /** Number of inventory slots: from the layout, or rows × 9. */
    public int inventorySize() {
        return menu.map(layout -> layout.slots().size()).orElse(inventoryRows * 9);
    }

    /** A builder for a block entity with data only. */
    public static Builder builder() {
        return new Builder();
    }

    /** Builds a {@link BlockEntityDefinition}. */
    public static final class Builder {

        private int inventoryRows;
        private String menuTitle = "container.chest";
        private Optional<BlockEntityTicker> ticker = Optional.empty();
        private boolean syncToClients;
        private Optional<MenuLayout> menu = Optional.empty();
        private int dataVersion;
        private Optional<DataMigration> migration = Optional.empty();

        private Builder() {}

        /** Adds an inventory of {@code rows} × 9 slots with a menu titled {@code title}. */
        public Builder inventory(int rows, String title) {
            this.inventoryRows = rows;
            this.menuTitle = title;
            return this;
        }

        /** Adds an inventory with one slot per layout slot, shown in a menu with that layout. */
        public Builder menu(MenuLayout layout, String title) {
            this.menu = Optional.of(layout);
            this.inventoryRows = 0;
            this.menuTitle = title;
            return this;
        }

        /** Sets the data version; block entities saved with an older one run {@code upgrade} on load. */
        public Builder dataVersion(int version, DataMigration upgrade) {
            this.dataVersion = version;
            this.migration = Optional.of(upgrade);
            return this;
        }

        /** Runs {@code ticker} on the server every tick. */
        public Builder ticker(BlockEntityTicker ticker) {
            this.ticker = Optional.of(ticker);
            return this;
        }

        /** Sends data changes to clients when {@link BlockEntityView#markChanged()} is called. */
        public Builder syncToClients() {
            this.syncToClients = true;
            return this;
        }

        /** Creates the definition. */
        public BlockEntityDefinition build() {
            return new BlockEntityDefinition(
                    inventoryRows, menuTitle, ticker, syncToClients, menu, dataVersion, migration);
        }
    }
}
