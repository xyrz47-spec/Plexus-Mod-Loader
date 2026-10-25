package dev.polymodloader.runtime.dependency;

/** Stable error codes for problems between mods. */
public enum DependencyProblemCode {
    DUPLICATE_MOD_ID("POLY-DEPENDENCY-001"),
    MISSING_DEPENDENCY("POLY-DEPENDENCY-002"),
    DEPENDENCY_VERSION_MISMATCH("POLY-DEPENDENCY-003"),
    DEPENDENCY_CYCLE("POLY-DEPENDENCY-004"),
    CONFLICTING_MODS("POLY-DEPENDENCY-005"),
    WRONG_ENVIRONMENT("POLY-DEPENDENCY-006"),
    UNSUPPORTED_MINECRAFT_VERSION("POLY-DEPENDENCY-007"),
    RESERVED_MOD_ID("POLY-DEPENDENCY-008"),
    /** The dependency is installed, but it is a mod of another loader that Poly Mod Loader cannot load. */
    UNAVAILABLE_DEPENDENCY("POLY-DEPENDENCY-009");

    private final String code;

    DependencyProblemCode(String code) {
        this.code = code;
    }

    /** The stable code, for example {@code POLY-DEPENDENCY-002}. */
    public String code() {
        return code;
    }
}
