package dev.polymodloader.runtime.system;

/** Stable error codes for system registration and replacement. */
public enum SystemProblemCode {
    CONFLICTING_REPLACEMENTS("PLEXUS-SYSTEM-001"),
    UNKNOWN_SYSTEM("PLEXUS-SYSTEM-002"),
    PROVIDER_FAILED("PLEXUS-SYSTEM-003"),
    NOT_REPLACEABLE("PLEXUS-SYSTEM-004"),
    INVALID_OVERRIDE("PLEXUS-SYSTEM-005");

    private final String code;

    SystemProblemCode(String code) {
        this.code = code;
    }

    /** The stable code, for example {@code PLEXUS-SYSTEM-001}. */
    public String code() {
        return code;
    }
}
