package dev.polymodloader.internal.transform;

import dev.polymodloader.api.runtime.GameSide;
import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A declarative class transformation rule (design doc §5.7, §12.3). Rules are data: they are
 * collected and validated into a plan before the target class is loaded, never executed ad hoc.
 *
 * <p>Rules that change the same method are rejected together ({@code POLY-TRANSFORM-004}) unless
 * {@link #after()} puts them in one order and their actions can be combined (see {@link
 * MethodTransformAction}).
 *
 * @param ruleId unique ID in {@code namespace:path} form, e.g. {@code poly:client_brand}
 * @param ownerModId mod (or {@code poly}) that provides the rule
 * @param gameVersion exact Minecraft version the target was verified against
 * @param sides processes the rule applies to
 * @param target the method to transform
 * @param action what to change, including its bytecode precondition
 * @param failurePolicy what to do when the rule cannot be applied
 * @param after IDs of rules on the same method whose code must run before this rule's; IDs of rules
 *     that are absent or change another method are ignored
 */
@ApiStability(StabilityLevel.INTERNAL)
public record TransformRuleDefinition(
        String ruleId,
        String ownerModId,
        String gameVersion,
        Set<GameSide> sides,
        MethodTarget target,
        MethodTransformAction action,
        TransformFailurePolicy failurePolicy,
        List<String> after) {

    private static final Pattern RULE_ID = Pattern.compile("[a-z][a-z0-9_]*:[a-z0-9_/]+");

    public TransformRuleDefinition {
        if (!RULE_ID.matcher(ruleId).matches()) {
            throw new IllegalArgumentException("Rule ID must look like namespace:path, got " + ruleId);
        }
        Objects.requireNonNull(ownerModId, "ownerModId");
        Objects.requireNonNull(gameVersion, "gameVersion");
        sides = Set.copyOf(sides);
        if (sides.isEmpty()) {
            throw new IllegalArgumentException("Rule " + ruleId + " must apply to at least one side");
        }
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(failurePolicy, "failurePolicy");
        after = List.copyOf(after);
        for (String earlier : after) {
            if (!RULE_ID.matcher(earlier).matches()) {
                throw new IllegalArgumentException("Rule " + ruleId + " lists an invalid rule ID in after: " + earlier);
            }
            if (earlier.equals(ruleId)) {
                throw new IllegalArgumentException("Rule " + ruleId + " cannot come after itself");
            }
        }
    }

    /** A rule with no declared order. */
    public TransformRuleDefinition(
            String ruleId,
            String ownerModId,
            String gameVersion,
            Set<GameSide> sides,
            MethodTarget target,
            MethodTransformAction action,
            TransformFailurePolicy failurePolicy) {
        this(ruleId, ownerModId, gameVersion, sides, target, action, failurePolicy, List.of());
    }
}
