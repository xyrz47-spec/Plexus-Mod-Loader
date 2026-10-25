package dev.polymodloader.runtime.metadata;

/** Stable error codes for {@code poly.mod.json} problems (design doc §7.2, §10). */
public enum MetadataProblemCode {
    INVALID_JSON("POLY-METADATA-001", Severity.ERROR),
    MISSING_FIELD("POLY-METADATA-002", Severity.ERROR),
    WRONG_TYPE("POLY-METADATA-003", Severity.ERROR),
    UNSUPPORTED_SCHEMA_VERSION("POLY-METADATA-004", Severity.ERROR),
    INVALID_MOD_ID("POLY-METADATA-005", Severity.ERROR),
    INVALID_VERSION("POLY-METADATA-006", Severity.ERROR),
    INVALID_ENVIRONMENT("POLY-METADATA-007", Severity.ERROR),
    INVALID_ENTRYPOINT("POLY-METADATA-008", Severity.ERROR),
    INVALID_VERSION_RANGE("POLY-METADATA-009", Severity.ERROR),
    FEATURE_NOT_SUPPORTED_YET("POLY-METADATA-010", Severity.ERROR),
    UNKNOWN_FIELD("POLY-METADATA-011", Severity.WARNING),
    MISSING_CAPABILITY("POLY-METADATA-012", Severity.ERROR),
    UNKNOWN_CAPABILITY("POLY-METADATA-013", Severity.WARNING),
    INVALID_SYSTEM_REPLACEMENT("POLY-METADATA-014", Severity.ERROR),
    INVALID_TRANSFORM_DECLARATION("POLY-METADATA-015", Severity.ERROR),
    INVALID_ACCESS_WIDENING("POLY-METADATA-016", Severity.ERROR);

    /** Whether a problem blocks loading the mod. */
    public enum Severity {
        ERROR,
        WARNING
    }

    private final String code;
    private final Severity severity;

    MetadataProblemCode(String code, Severity severity) {
        this.code = code;
        this.severity = severity;
    }

    /** The stable code, for example {@code POLY-METADATA-005}. */
    public String code() {
        return code;
    }

    public Severity severity() {
        return severity;
    }
}
