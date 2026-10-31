package dev.plexusmodloader.compat.bridge;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/**
 * One feature of a foreign mod and what Plexus Mod Loader can do about it.
 *
 * @param code stable code, e.g. {@code PLEXUS-COMPAT-101}
 * @param blocking whether this feature alone prevents loading
 * @param feature short feature name, e.g. {@code mixins}
 * @param detail what was found, with names and counts
 */
@ApiStability(StabilityLevel.INTERNAL)
public record CompatibilityFinding(String code, boolean blocking, String feature, String detail) {

    @Override
    public String toString() {
        return code + (blocking ? " [blocking] " : " [limited] ") + feature + ": " + detail;
    }
}
