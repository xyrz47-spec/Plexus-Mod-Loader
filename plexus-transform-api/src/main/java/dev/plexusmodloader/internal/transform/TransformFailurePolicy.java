package dev.plexusmodloader.internal.transform;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/**
 * What Poly Mod Loader does when a rule cannot be applied safely. Chosen per rule by how essential the feature
 * is; a failed rule is never reported as applied (design doc §12.4).
 */
@ApiStability(StabilityLevel.INTERNAL)
public enum TransformFailurePolicy {
    /** Stop the launch with a diagnostic. For rules whose absence could corrupt game state or data. */
    ABORT_STARTUP,
    /** Skip only this rule, log the reason, and continue. For optional features. */
    DISABLE_RULE
}
