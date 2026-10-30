package dev.polymodloader.internal.transform;

import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;

/**
 * Why an access widening was rejected.
 *
 * @param code stable error code
 * @param widening the widening concerned
 * @param message explanation, including what was expected and what was found
 */
@ApiStability(StabilityLevel.INTERNAL)
public record AccessWideningProblem(TransformProblemCode code, AccessWidening widening, String message) {

    @Override
    public String toString() {
        return code.code() + " access widening owner=" + widening.ownerModId() + " target=" + widening.describeTarget()
                + " minecraft=" + widening.gameVersion() + ": " + message;
    }
}
