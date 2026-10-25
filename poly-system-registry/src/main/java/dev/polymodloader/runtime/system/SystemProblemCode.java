package dev.polymodloader.runtime.system;

/** Stable error codes for system registration and replacement. */
public enum SystemProblemCode {
    CONFLICTING_REPLACEMENTS("POLY-SYSTEM-001"),
    UNKNOWN_SYSTEM("POLY-SYSTEM-002"),
    PROVIDER_FAILED("POLY-SYSTEM-003"),
    NOT_REPLACEABLE("POLY-SYSTEM-004"),
    INVALID_OVERRIDE("POLY-SYSTEM-005");

    private final String code;

    SystemProblemCode(String code) {
        this.code = code;
    }

    /** The stable code, for example {@code POLY-SYSTEM-001}. */
    public String code() {
        return code;
    }
}
