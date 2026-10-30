package dev.plexusmodloader.runtime.dependency;

/** Stable error codes for problems between mods. */
public enum DependencyProblemCode {
    DUPLICATE_MOD_ID("PLEXUS-DEPENDENCY-001"),
    MISSING_DEPENDENCY("PLEXUS-DEPENDENCY-002"),
    DEPENDENCY_VERSION_MISMATCH("PLEXUS-DEPENDENCY-003"),
    DEPENDENCY_CYCLE("PLEXUS-DEPENDENCY-004"),
    CONFLICTING_MODS("PLEXUS-DEPENDENCY-005"),
    WRONG_ENVIRONMENT("PLEXUS-DEPENDENCY-006"),
    UNSUPPORTED_MINECRAFT_VERSION("PLEXUS-DEPENDENCY-007"),
    RESERVED_MOD_ID("PLEXUS-DEPENDENCY-008"),
    /** The dependency is installed, but it is a mod of another loader that Poly Mod Loader cannot load. */
    UNAVAILABLE_DEPENDENCY("PLEXUS-DEPENDENCY-009");

    private final String code;

    DependencyProblemCode(String code) {
        this.code = code;
    }

    /** The stable code, for example {@code PLEXUS-DEPENDENCY-002}. */
    public String code() {
        return code;
    }
}
