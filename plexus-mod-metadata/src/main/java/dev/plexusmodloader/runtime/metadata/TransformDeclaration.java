package dev.plexusmodloader.runtime.metadata;

import java.util.List;
import java.util.Map;

/**
 * One entry of {@code transforms} in {@code plexus.mod.json}. The metadata module only checks the
 * shape; the runtime turns it into an engine rule and the planner validates it against the game.
 *
 * @param id rule ID, must be {@code <mod id>:<path>}
 * @param minecraft exact Minecraft version the target was verified against
 * @param sides {@code client} and/or {@code dedicated_server}
 * @param owner target class (JVM internal name)
 * @param method target method name
 * @param descriptor target method descriptor
 * @param actionType action identifier, e.g. {@code replace_returned_string_constant}
 * @param actionArguments action parameters, required and optional
 * @param abortOnFailure true for {@code "onFailure": "abort_startup"}, false for {@code "disable_rule"}
 * @param after IDs of rules on the same method whose code must run before this rule's
 */
public record TransformDeclaration(
        String id,
        String minecraft,
        List<String> sides,
        String owner,
        String method,
        String descriptor,
        String actionType,
        Map<String, String> actionArguments,
        boolean abortOnFailure,
        List<String> after) {

    /** Action types this Plexus Mod Loader version understands, with their required arguments. */
    public static final Map<String, List<String>> ACTIONS = Map.of(
            "replace_returned_string_constant", List.of("expected", "replacement"),
            "invoke_hook_at_head", List.of("hookOwner", "hookMethod", "result"),
            "invoke_hook_before_return", List.of("hookOwner", "hookMethod"),
            "redirect_call", List.of("invokedOwner", "invokedName", "invokedDescriptor", "hookOwner", "hookMethod"));

    /** Optional arguments per action type. */
    public static final Map<String, List<String>> OPTIONAL_ARGUMENTS = Map.of("redirect_call", List.of("ordinal"));

    /** Action types that need a capability besides {@code internal.bytecode.transform}. */
    public static final Map<String, String> EXTRA_CAPABILITY = Map.of("redirect_call", "internal.callsite.intercept");

    public TransformDeclaration {
        sides = List.copyOf(sides);
        actionArguments = Map.copyOf(actionArguments);
        after = List.copyOf(after);
    }

    /** A declaration with no declared order. */
    public TransformDeclaration(
            String id,
            String minecraft,
            List<String> sides,
            String owner,
            String method,
            String descriptor,
            String actionType,
            Map<String, String> actionArguments,
            boolean abortOnFailure) {
        this(id, minecraft, sides, owner, method, descriptor, actionType, actionArguments, abortOnFailure, List.of());
    }
}
