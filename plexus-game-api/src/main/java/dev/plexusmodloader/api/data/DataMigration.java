package dev.plexusmodloader.api.data;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/**
 * Upgrades saved data written by an older version of a mod. Called once when data saved with an
 * older version is loaded, before anything reads it; afterwards the data counts as current.
 */
@ApiStability(StabilityLevel.PREVIEW)
@FunctionalInterface
public interface DataMigration {

    /**
     * Upgrades {@code data} from {@code fromVersion} to the current version.
     *
     * @param data the saved data, changed in place
     * @param fromVersion the version it was saved with (0 for data saved before versions were used)
     */
    void migrate(DataTree data, int fromVersion);
}
