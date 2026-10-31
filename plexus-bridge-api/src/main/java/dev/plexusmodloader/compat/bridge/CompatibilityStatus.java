package dev.plexusmodloader.compat.bridge;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/** Compatibility status of a mod built for another loader (design doc §13.3). */
@ApiStability(StabilityLevel.INTERNAL)
public enum CompatibilityStatus {
    /** Passed the defined test plan. */
    VERIFIED,
    /** Works with known feature limits. */
    PARTIAL,
    /** Loaded through a bridge path that only some experiments cover. */
    EXPERIMENTAL,
    /** Cannot run: it needs features no bridge provides yet. Not loaded. */
    BLOCKED,
    /** Not assessed. */
    UNTESTED;

    /** Whether Plexus Mod Loader loads mods with this status. */
    public boolean loadable() {
        return this == VERIFIED || this == PARTIAL || this == EXPERIMENTAL;
    }
}
