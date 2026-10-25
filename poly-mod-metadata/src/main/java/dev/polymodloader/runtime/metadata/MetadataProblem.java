package dev.polymodloader.runtime.metadata;

/**
 * One finding from reading a {@code poly.mod.json}.
 *
 * @param code stable error code
 * @param field JSON path of the offending field, or {@code $} for the whole document
 * @param message human-readable explanation including the offending value
 */
public record MetadataProblem(MetadataProblemCode code, String field, String message) {

    /** Whether this problem prevents the mod from loading. */
    public boolean isError() {
        return code.severity() == MetadataProblemCode.Severity.ERROR;
    }

    @Override
    public String toString() {
        return code.code() + " " + code.severity() + " at " + field + ": " + message;
    }
}
