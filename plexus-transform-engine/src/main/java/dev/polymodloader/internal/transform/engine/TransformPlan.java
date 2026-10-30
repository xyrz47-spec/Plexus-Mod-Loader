package dev.polymodloader.internal.transform.engine;

import dev.polymodloader.internal.transform.AccessWidening;
import dev.polymodloader.internal.transform.AccessWideningProblem;
import dev.polymodloader.internal.transform.TransformFailurePolicy;
import dev.polymodloader.internal.transform.TransformProblem;
import dev.polymodloader.internal.transform.TransformRuleDefinition;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The validated set of rules and access widenings to apply, grouped by target class, plus every one
 * that was rejected and why. Produced once by {@link ClassTransformPlanner} before game classes load.
 *
 * @param rulesByClass accepted rules keyed by target class internal name, in application order
 * @param rejected problems for every rejected rule
 * @param wideningsByClass accepted access widenings keyed by target class internal name
 * @param rejectedWidenings problems for every rejected access widening
 */
public record TransformPlan(
        Map<String, List<TransformRuleDefinition>> rulesByClass,
        List<TransformProblem> rejected,
        Map<String, List<AccessWidening>> wideningsByClass,
        List<AccessWideningProblem> rejectedWidenings) {

    public TransformPlan {
        rulesByClass = Map.copyOf(rulesByClass);
        rejected = List.copyOf(rejected);
        wideningsByClass = Map.copyOf(wideningsByClass);
        rejectedWidenings = List.copyOf(rejectedWidenings);
    }

    /** A plan without access widenings. */
    public TransformPlan(Map<String, List<TransformRuleDefinition>> rulesByClass, List<TransformProblem> rejected) {
        this(rulesByClass, rejected, Map.of(), List.of());
    }

    /** Number of accepted rules across all classes. */
    public int acceptedRuleCount() {
        return rulesByClass.values().stream().mapToInt(List::size).sum();
    }

    /** Number of accepted access widenings across all classes. */
    public int acceptedWideningCount() {
        return wideningsByClass.values().stream().mapToInt(List::size).sum();
    }

    /** Rejections whose rule demands stopping the launch. */
    public List<TransformProblem> abortingProblems() {
        return rejected.stream()
                .filter(problem -> problem.rule().failurePolicy() == TransformFailurePolicy.ABORT_STARTUP)
                .toList();
    }

    /** Rejected access widenings that demand stopping the launch. */
    public List<AccessWideningProblem> abortingWideningProblems() {
        return rejectedWidenings.stream()
                .filter(problem -> problem.widening().failurePolicy() == TransformFailurePolicy.ABORT_STARTUP)
                .toList();
    }

    /** Every problem that demands stopping the launch, as text. */
    public List<String> abortingProblemMessages() {
        return Stream.concat(
                        abortingProblems().stream().map(TransformProblem::toString),
                        abortingWideningProblems().stream().map(AccessWideningProblem::toString))
                .toList();
    }
}
