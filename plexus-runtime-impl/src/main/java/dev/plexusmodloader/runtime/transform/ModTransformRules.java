package dev.plexusmodloader.runtime.transform;

import dev.plexusmodloader.api.runtime.GameSide;
import dev.plexusmodloader.internal.transform.AccessWidening;
import dev.plexusmodloader.internal.transform.HeadHookResult;
import dev.plexusmodloader.internal.transform.InvokeHookAtHead;
import dev.plexusmodloader.internal.transform.InvokeHookBeforeReturn;
import dev.plexusmodloader.internal.transform.MethodTarget;
import dev.plexusmodloader.internal.transform.MethodTransformAction;
import dev.plexusmodloader.internal.transform.RedirectCall;
import dev.plexusmodloader.internal.transform.ReplaceReturnedStringConstant;
import dev.plexusmodloader.internal.transform.TransformFailurePolicy;
import dev.plexusmodloader.internal.transform.TransformRuleDefinition;
import dev.plexusmodloader.runtime.metadata.AccessWideningDeclaration;
import dev.plexusmodloader.runtime.metadata.ModMetadata;
import dev.plexusmodloader.runtime.metadata.TransformDeclaration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns the {@code transforms} and {@code accessWidenings} declared in {@code poly.mod.json} into engine
 * rules and widenings. The planner then validates them against the game exactly like Poly Mod Loader's own:
 * target, descriptor, precondition, conflicts and order with every other rule (including Poly Mod Loader's),
 * and game version.
 */
public final class ModTransformRules {

    /**
     * Converted rules and widenings plus problems for declarations that could not be converted.
     *
     * @param rules engine rules
     * @param problems one line per unconvertible declaration
     * @param widenings engine access widenings
     */
    public record Result(List<TransformRuleDefinition> rules, List<String> problems, List<AccessWidening> widenings) {
        public Result {
            rules = List.copyOf(rules);
            problems = List.copyOf(problems);
            widenings = List.copyOf(widenings);
        }
    }

    private ModTransformRules() {}

    /** Converts every transform and access widening declared by {@code mods}. */
    public static Result from(List<ModMetadata> mods) {
        List<TransformRuleDefinition> rules = new ArrayList<>();
        List<AccessWidening> widenings = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        for (ModMetadata mod : mods) {
            for (TransformDeclaration declaration : mod.transforms()) {
                try {
                    rules.add(new TransformRuleDefinition(
                            declaration.id(),
                            mod.id().value(),
                            declaration.minecraft(),
                            sides(declaration.sides()),
                            new MethodTarget(declaration.owner(), declaration.method(), declaration.descriptor()),
                            action(declaration),
                            declaration.abortOnFailure()
                                    ? TransformFailurePolicy.ABORT_STARTUP
                                    : TransformFailurePolicy.DISABLE_RULE,
                            declaration.after()));
                } catch (IllegalArgumentException invalid) {
                    problems.add("PLEXUS-METADATA-015 mod=" + mod.id() + " transform=" + declaration.id() + ": "
                            + invalid.getMessage());
                }
            }
            for (AccessWideningDeclaration declaration : mod.accessWidenings()) {
                try {
                    widenings.add(new AccessWidening(
                            mod.id().value(),
                            declaration.minecraft(),
                            sides(declaration.sides()),
                            declaration.owner(),
                            AccessWidening.Kind.valueOf(declaration.kind().toUpperCase(java.util.Locale.ROOT)),
                            declaration.name(),
                            declaration.descriptor(),
                            access(declaration.access()),
                            declaration.abortOnFailure()
                                    ? TransformFailurePolicy.ABORT_STARTUP
                                    : TransformFailurePolicy.DISABLE_RULE));
                } catch (IllegalArgumentException invalid) {
                    problems.add("PLEXUS-METADATA-016 mod=" + mod.id() + " access widening of " + declaration.owner()
                            + (declaration.name().isEmpty() ? "" : "." + declaration.name()) + ": "
                            + invalid.getMessage());
                }
            }
        }
        return new Result(rules, problems, widenings);
    }

    private static Set<GameSide> sides(List<String> sides) {
        Set<GameSide> result = EnumSet.noneOf(GameSide.class);
        for (String side : sides) {
            result.add(side.equals("client") ? GameSide.CLIENT : GameSide.DEDICATED_SERVER);
        }
        return result;
    }

    private static Set<AccessWidening.Access> access(Set<String> words) {
        Set<AccessWidening.Access> result = EnumSet.noneOf(AccessWidening.Access.class);
        for (String word : words) {
            result.add(AccessWidening.Access.valueOf(word.toUpperCase(java.util.Locale.ROOT)));
        }
        return result;
    }

    private static MethodTransformAction action(TransformDeclaration declaration) {
        Map<String, String> arguments = declaration.actionArguments();
        return switch (declaration.actionType()) {
            case "replace_returned_string_constant" ->
                new ReplaceReturnedStringConstant(arguments.get("expected"), arguments.get("replacement"));
            case "invoke_hook_at_head" ->
                new InvokeHookAtHead(
                        internalName(arguments.get("hookOwner")),
                        arguments.get("hookMethod"),
                        HeadHookResult.valueOf(arguments.get("result").toUpperCase(java.util.Locale.ROOT)));
            case "invoke_hook_before_return" ->
                new InvokeHookBeforeReturn(internalName(arguments.get("hookOwner")), arguments.get("hookMethod"));
            case "redirect_call" ->
                new RedirectCall(
                        new MethodTarget(
                                internalName(arguments.get("invokedOwner")),
                                arguments.get("invokedName"),
                                arguments.get("invokedDescriptor")),
                        ordinal(arguments.getOrDefault("ordinal", "all")),
                        internalName(arguments.get("hookOwner")),
                        arguments.get("hookMethod"));
            default -> throw new IllegalArgumentException("unsupported action " + declaration.actionType());
        };
    }

    private static int ordinal(String text) {
        return text.equals("all") ? RedirectCall.ALL : Integer.parseInt(text);
    }

    /** Accepts {@code com.example.Hooks} as well as {@code com/example/Hooks}. */
    private static String internalName(String name) {
        return name.replace('.', '/');
    }
}
