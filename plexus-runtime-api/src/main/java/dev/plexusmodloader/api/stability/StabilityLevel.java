package dev.plexusmodloader.api.stability;

/**
 * Compatibility promise of a Plexus Mod Loader API element (design doc §5.4). Every public type declares one
 * through {@link ApiStability}.
 */
public enum StabilityLevel {
    /** Public compatibility policy; breaking changes require an API version bump and migration notes. */
    STABLE,
    /** Usable, but the contract may still change. Changes are recorded in the changelog. */
    PREVIEW,
    /** Bound to specific Minecraft versions and reached through a version adapter. */
    VERSION_BOUND,
    /** Used only by Plexus Mod Loader's own modules. Mods must not depend on it. */
    INTERNAL,
    /** Low-level behaviour that can crash the game or corrupt world data; requires explicit opt-in. */
    UNSAFE
}
