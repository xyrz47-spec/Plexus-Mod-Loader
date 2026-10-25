package dev.polymodloader.internal.transform.engine;

import dev.polymodloader.api.runtime.GameSide;
import dev.polymodloader.internal.transform.AccessWidening;
import dev.polymodloader.internal.transform.AccessWideningProblem;
import dev.polymodloader.internal.transform.InvokeHookAtCall;
import dev.polymodloader.internal.transform.MethodTarget;
import dev.polymodloader.internal.transform.RedirectCall;
import dev.polymodloader.internal.transform.TransformProblem;
import dev.polymodloader.internal.transform.TransformProblemCode;
import dev.polymodloader.internal.transform.TransformRuleDefinition;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Turns declared rules into a {@link TransformPlan} before any game class is loaded (design doc
 * §11 step 6, §12.4). For each rule it checks, in order: unique rule ID, matching game version,
 * conflicts with other rules on the same method, target class presence, exact method
 * name + descriptor, and the action's bytecode precondition. A rule that fails any check is
 * rejected with a {@link TransformProblem}; it is never partially applied.
 *
 * <p>Rules on the same method are accepted together only when their {@code after} lists give one
 * order and their actions can be combined (see {@link dev.polymodloader.internal.transform.MethodTransformAction});
 * each precondition is checked against the original method. The plan lists a class's rules in that
 * order.
 */
public final class ClassTransformPlanner {

    /** Plans {@code rules} for {@code side} of Minecraft {@code gameVersion}. */
    public TransformPlan plan(
            List<TransformRuleDefinition> rules, String gameVersion, GameSide side, ClassBytesSource classes) {
        return plan(rules, List.of(), gameVersion, side, classes);
    }

    /**
     * Plans {@code rules} and {@code widenings} for {@code side} of Minecraft {@code gameVersion}. A
     * widening is accepted when its game version matches and its class and exact member exist.
     */
    public TransformPlan plan(
            List<TransformRuleDefinition> rules,
            List<AccessWidening> widenings,
            String gameVersion,
            GameSide side,
            ClassBytesSource classes) {
        TransformPlan rulePlan = planRules(rules, gameVersion, side, classes);
        Map<String, List<AccessWidening>> acceptedWidenings = new LinkedHashMap<>();
        List<AccessWideningProblem> rejectedWidenings = new ArrayList<>();
        Map<String, Optional<ClassNode>> parsedClasses = new LinkedHashMap<>();
        for (AccessWidening widening : widenings) {
            if (!widening.sides().contains(side)) {
                continue;
            }
            if (!widening.gameVersion().equals(gameVersion)) {
                rejectedWidenings.add(new AccessWideningProblem(
                        TransformProblemCode.GAME_VERSION_MISMATCH,
                        widening,
                        "it was verified against Minecraft " + widening.gameVersion() + " but the game is "
                                + gameVersion));
                continue;
            }
            String owner = widening.ownerInternalName();
            Optional<ClassNode> classNode = parsedClasses.computeIfAbsent(
                    owner, name -> classes.read(name).map(ClassTransformPlanner::parse));
            Optional<String> missing = classNode.isEmpty()
                    ? Optional.of("class " + owner + " is not in the game")
                    : AccessWideningApplier.checkTarget(widening, classNode.get());
            if (missing.isPresent()) {
                rejectedWidenings.add(new AccessWideningProblem(
                        TransformProblemCode.ACCESS_TARGET_NOT_FOUND, widening, missing.get()));
                continue;
            }
            acceptedWidenings.computeIfAbsent(owner, name -> new ArrayList<>()).add(widening);
        }
        return new TransformPlan(rulePlan.rulesByClass(), rulePlan.rejected(), acceptedWidenings, rejectedWidenings);
    }

    private TransformPlan planRules(
            List<TransformRuleDefinition> rules, String gameVersion, GameSide side, ClassBytesSource classes) {
        List<TransformProblem> rejected = new ArrayList<>();
        List<TransformRuleDefinition> candidates = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();

        for (TransformRuleDefinition rule : rules) {
            if (!rule.sides().contains(side)) {
                continue;
            }
            if (!seenIds.add(rule.ruleId())) {
                rejected.add(new TransformProblem(
                        TransformProblemCode.DUPLICATE_RULE_ID, rule, "another rule already uses this ID"));
                continue;
            }
            if (!rule.gameVersion().equals(gameVersion)) {
                rejected.add(new TransformProblem(
                        TransformProblemCode.GAME_VERSION_MISMATCH,
                        rule,
                        "rule was verified against Minecraft " + rule.gameVersion() + " but the game is "
                                + gameVersion));
                continue;
            }
            candidates.add(rule);
        }

        Map<MethodTarget, List<TransformRuleDefinition>> byMethod = new LinkedHashMap<>();
        candidates.forEach(rule -> byMethod.computeIfAbsent(rule.target(), target -> new ArrayList<>())
                .add(rule));

        Map<String, List<TransformRuleDefinition>> accepted = new LinkedHashMap<>();
        Map<String, Optional<ClassNode>> parsedClasses = new LinkedHashMap<>();
        for (Map.Entry<MethodTarget, List<TransformRuleDefinition>> entry : byMethod.entrySet()) {
            List<TransformRuleDefinition> sameMethod = entry.getValue();
            if (sameMethod.size() > 1) {
                // Rules on one method are combined only with a declared order and actions that can
                // be combined; otherwise all are rejected instead of picking a winner (§4.4, §5.7).
                Optional<String> conflict = combinationProblem(sameMethod);
                if (conflict.isEmpty()) {
                    sameMethod = declaredOrder(sameMethod).orElseThrow();
                } else {
                    List<String> ids = sameMethod.stream()
                            .map(TransformRuleDefinition::ruleId)
                            .toList();
                    for (TransformRuleDefinition rule : sameMethod) {
                        rejected.add(new TransformProblem(
                                TransformProblemCode.CONFLICTING_RULES,
                                rule,
                                "rules " + ids + " all modify " + entry.getKey() + "; " + conflict.get()));
                    }
                    continue;
                }
            }
            for (TransformRuleDefinition rule : sameMethod) {
                String owner = rule.target().ownerInternalName();
                Optional<ClassNode> classNode = parsedClasses.computeIfAbsent(
                        owner, name -> classes.read(name).map(ClassTransformPlanner::parse));
                if (classNode.isEmpty()) {
                    rejected.add(new TransformProblem(
                            TransformProblemCode.TARGET_CLASS_NOT_FOUND,
                            rule,
                            "class " + owner + " is not in the game"));
                    continue;
                }
                Optional<MethodNode> method = findMethod(classNode.get(), rule.target());
                if (method.isEmpty()) {
                    rejected.add(new TransformProblem(
                            TransformProblemCode.TARGET_METHOD_NOT_FOUND,
                            rule,
                            "no method " + rule.target().name() + rule.target().descriptor() + " in " + owner
                                    + describeSameName(
                                            classNode.get(), rule.target().name())));
                    continue;
                }
                Optional<String> preconditionFailure =
                        MethodActionApplier.checkPrecondition(rule.action(), method.get());
                if (preconditionFailure.isPresent()) {
                    rejected.add(new TransformProblem(
                            TransformProblemCode.PRECONDITION_FAILED, rule, preconditionFailure.get()));
                    continue;
                }
                accepted.computeIfAbsent(owner, name -> new ArrayList<>()).add(rule);
            }
        }
        return new TransformPlan(accepted, rejected);
    }

    /** Why rules on one method cannot be combined, or empty when they can. */
    private static Optional<String> combinationProblem(List<TransformRuleDefinition> sameMethod) {
        for (TransformRuleDefinition rule : sameMethod) {
            if (MethodActionApplier.isExclusive(rule.action())) {
                return Optional.of(rule.ruleId() + " replaces the body's constant, which cannot be combined with"
                        + " other rules; remove one of them");
            }
        }
        Map<MethodTarget, String> redirected = new LinkedHashMap<>();
        for (TransformRuleDefinition rule : sameMethod) {
            if (rule.action() instanceof RedirectCall redirect) {
                String earlier = redirected.putIfAbsent(redirect.invoked(), rule.ruleId());
                if (earlier != null) {
                    return Optional.of(earlier + " and " + rule.ruleId() + " both redirect calls to "
                            + redirect.invoked() + "; remove one of them");
                }
            }
        }
        // A redirect replaces the call instruction an observer of the same call looks for.
        for (TransformRuleDefinition rule : sameMethod) {
            if (rule.action() instanceof InvokeHookAtCall hook && redirected.containsKey(hook.invoked())) {
                return Optional.of(redirected.get(hook.invoked()) + " redirects the calls to " + hook.invoked()
                        + " that " + rule.ruleId() + " observes; remove one of them");
            }
        }
        if (declaredOrder(sameMethod).isEmpty()) {
            return Optional.of("declare an order with \"after\" that puts every rule before or after each"
                    + " other (a cycle or two rules with no order between them is not enough), or remove one");
        }
        return Optional.empty();
    }

    /**
     * The rules in the single order their {@code after} lists allow, or empty when there is no order
     * or more than one: at every step exactly one rule must have all its earlier rules placed.
     */
    static Optional<List<TransformRuleDefinition>> declaredOrder(List<TransformRuleDefinition> sameMethod) {
        Set<String> ids = new HashSet<>();
        sameMethod.forEach(rule -> ids.add(rule.ruleId()));
        List<TransformRuleDefinition> remaining = new ArrayList<>(sameMethod);
        Set<String> placed = new HashSet<>();
        List<TransformRuleDefinition> ordered = new ArrayList<>();
        while (!remaining.isEmpty()) {
            List<TransformRuleDefinition> ready = remaining.stream()
                    .filter(rule -> rule.after().stream().filter(ids::contains).allMatch(placed::contains))
                    .toList();
            if (ready.size() != 1) {
                return Optional.empty();
            }
            TransformRuleDefinition next = ready.getFirst();
            ordered.add(next);
            placed.add(next.ruleId());
            remaining.remove(next);
        }
        return Optional.of(ordered);
    }

    static ClassNode parse(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    static Optional<MethodNode> findMethod(ClassNode classNode, MethodTarget target) {
        return classNode.methods.stream()
                .filter(method -> method.name.equals(target.name()) && method.desc.equals(target.descriptor()))
                .findFirst();
    }

    private static String describeSameName(ClassNode classNode, String name) {
        List<String> descriptors = classNode.methods.stream()
                .filter(method -> method.name.equals(name))
                .map(method -> method.desc)
                .toList();
        return descriptors.isEmpty()
                ? " (no method with that name exists)"
                : " (methods with that name have descriptors " + descriptors + ")";
    }
}
