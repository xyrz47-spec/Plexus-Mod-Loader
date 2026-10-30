package dev.polymodloader.internal.transform;

import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;

/** Stable error codes for transformation problems. */
@ApiStability(StabilityLevel.INTERNAL)
public enum TransformProblemCode {
    TARGET_CLASS_NOT_FOUND("PLEXUS-TRANSFORM-001"),
    TARGET_METHOD_NOT_FOUND("PLEXUS-TRANSFORM-002"),
    PRECONDITION_FAILED("PLEXUS-TRANSFORM-003"),
    CONFLICTING_RULES("PLEXUS-TRANSFORM-004"),
    VERIFICATION_FAILED("PLEXUS-TRANSFORM-005"),
    GAME_VERSION_MISMATCH("PLEXUS-TRANSFORM-006"),
    DUPLICATE_RULE_ID("PLEXUS-TRANSFORM-007"),
    CACHE_ENTRY_DAMAGED("PLEXUS-TRANSFORM-008"),
    CACHE_WRITE_FAILED("PLEXUS-TRANSFORM-009"),
    ACCESS_TARGET_NOT_FOUND("PLEXUS-TRANSFORM-010"),
    /**
     * A call redirect left out of one class because another loader's transformer (a bridge's Mixin) already replaced
     * or moved the call (ADR-0020); a warning, the class still loads.
     */
    REDIRECT_TAKEN_BY_OTHER_TRANSFORMER("PLEXUS-TRANSFORM-011");

    private final String code;

    TransformProblemCode(String code) {
        this.code = code;
    }

    /** The stable code, for example {@code PLEXUS-TRANSFORM-003}. */
    public String code() {
        return code;
    }
}
