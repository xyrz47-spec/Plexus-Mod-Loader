package dev.plexusmodloader.api.content;

import dev.plexusmodloader.api.data.DataMigration;
import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;
import java.util.Objects;
import java.util.Optional;

/**
 * How an {@link EntityDataKey} is kept and shared.
 *
 * @param syncToOwner a player's own client receives the player's data
 * @param syncToTrackers every client that can see the entity receives its data (for drawing or
 *     client logic); includes the owner
 * @param version current data version; data saved with an older one runs {@code migration}
 * @param migration upgrades older data, if any
 */
@ApiStability(StabilityLevel.PREVIEW)
public record EntityDataOptions(
        boolean syncToOwner, boolean syncToTrackers, int version, Optional<DataMigration> migration) {

    /** Saved only, not synchronised, version 0. */
    public static final EntityDataOptions DEFAULT = new EntityDataOptions(false, false, 0, Optional.empty());

    public EntityDataOptions {
        Objects.requireNonNull(migration, "migration");
        if (version < 0) {
            throw new IllegalArgumentException("version must be >= 0: " + version);
        }
    }

    /** A copy whose data a player's own client receives. */
    public EntityDataOptions syncedToOwner() {
        return new EntityDataOptions(true, syncToTrackers, version, migration);
    }

    /** A copy whose data every client that can see the entity receives. */
    public EntityDataOptions syncedToTrackers() {
        return new EntityDataOptions(syncToOwner, true, version, migration);
    }

    /** A copy with data version {@code value}; {@code upgrade} converts data saved with an older version. */
    public EntityDataOptions withVersion(int value, DataMigration upgrade) {
        return new EntityDataOptions(syncToOwner, syncToTrackers, value, Optional.of(upgrade));
    }
}
