package dev.plexusmodloader.runtime.lifecycle;

/**
 * Plexus Mod Loader's launch phases, in order. Names follow the loading pipeline of design doc §11; phases not
 * implemented yet (game validation, runtime monitoring) are added when
 * their features exist.
 */
public enum RuntimePhase {
    BOOTSTRAP,
    DISCOVERY,
    METADATA_VALIDATION,
    DEPENDENCY_RESOLUTION,
    CONFLICT_RESOLUTION,
    TRANSFORMATION_PLANNING,
    SYSTEM_REGISTRATION,
    LIFECYCLE_INITIALIZATION,
    GAME_LAUNCH,
    SHUTDOWN
}
