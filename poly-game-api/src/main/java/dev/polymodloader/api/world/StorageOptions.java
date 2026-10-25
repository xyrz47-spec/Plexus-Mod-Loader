package dev.polymodloader.api.world;

import dev.polymodloader.api.data.DataMigration;
import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;
import java.util.Objects;
import java.util.Optional;

/**
 * How {@link WorldView#storage(dev.polymodloader.api.resource.ResourceId, StorageOptions)} keeps data.
 *
 * @param version the current data version; saved data of an older version runs {@code migration}
 * @param migration upgrades older data, if any
 * @param syncToClients whether every player's client gets a read-only copy, updated on
 *     {@link StoredData#markChanged()}
 */
@ApiStability(StabilityLevel.PREVIEW)
public record StorageOptions(int version, Optional<DataMigration> migration, boolean syncToClients) {

    /** Version 0, no migration, not synchronised. */
    public static final StorageOptions DEFAULT = new StorageOptions(0, Optional.empty(), false);

    public StorageOptions {
        if (version < 0) {
            throw new IllegalArgumentException("version must be >= 0: " + version);
        }
        Objects.requireNonNull(migration, "migration");
    }

    /** These options at {@code version}, upgrading older data with {@code migration}. */
    public StorageOptions withVersion(int value, DataMigration upgrade) {
        return new StorageOptions(value, Optional.of(upgrade), syncToClients);
    }

    /** These options, synchronised to clients. */
    public StorageOptions syncedToClients() {
        return new StorageOptions(version, migration, true);
    }
}
