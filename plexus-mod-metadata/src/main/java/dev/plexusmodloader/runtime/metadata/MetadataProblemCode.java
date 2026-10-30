package dev.plexusmodloader.runtime.metadata;

/** Stable error codes for {@code poly.mod.json} problems (design doc §7.2, §10). */
public enum MetadataProblemCode {
    INVALID_JSON("PLEXUS-METADATA-001", Severity.ERROR),
    MISSING_FIELD("PLEXUS-METADATA-002", Severity.ERROR),
    WRONG_TYPE("PLEXUS-METADATA-003", Severity.ERROR),
    UNSUPPORTED_SCHEMA_VERSION("PLEXUS-METADATA-004", Severity.ERROR),
    INVALID_MOD_ID("PLEXUS-METADATA-005", Severity.ERROR),
    INVALID_VERSION("PLEXUS-METADATA-006", Severity.ERROR),
    INVALID_ENVIRONMENT("PLEXUS-METADATA-007", Severity.ERROR),
    INVALID_ENTRYPOINT("PLEXUS-METADATA-008", Severity.ERROR),
    INVALID_VERSION_RANGE("PLEXUS-METADATA-009", Severity.ERROR),
    FEATURE_NOT_SUPPORTED_YET("PLEXUS-METADATA-010", Severity.ERROR),
    UNKNOWN_FIELD("PLEXUS-METADATA-011", Severity.WARNING),
    MISSING_CAPABILITY("PLEXUS-METADATA-012", Severity.ERROR),
    UNKNOWN_CAPABILITY("PLEXUS-METADATA-013", Severity.WARNING),
    INVALID_SYSTEM_REPLACEMENT("PLEXUS-METADATA-014", Severity.ERROR),
    INVALID_TRANSFORM_DECLARATION("PLEXUS-METADATA-015", Severity.ERROR),
    INVALID_ACCESS_WIDENING("PLEXUS-METADATA-016", Severity.ERROR);

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

    /** The stable code, for example {@code PLEXUS-METADATA-005}. */
    public String code() {
        return code;
    }

    public Severity severity() {
        return severity;
    }
}
