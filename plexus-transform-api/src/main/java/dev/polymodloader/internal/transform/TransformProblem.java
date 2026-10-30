package dev.polymodloader.internal.transform;

import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;

/**
 * Why a rule was rejected or failed, with everything needed to find the cause (design doc §17).
 *
 * @param code stable error code
 * @param rule the rule concerned
 * @param message explanation, including what was expected and what was found
 */
@ApiStability(StabilityLevel.INTERNAL)
public record TransformProblem(TransformProblemCode code, TransformRuleDefinition rule, String message) {

    @Override
    public String toString() {
        return code.code() + " rule=" + rule.ruleId() + " owner=" + rule.ownerModId() + " target=" + rule.target()
                + " minecraft=" + rule.gameVersion() + ": " + message;
    }
}
