package dev.plexusmodloader.internal.transform;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/**
 * Calls a static hook at the start of a method with the receiver and every argument, and optionally
 * lets the hook end the call early ({@code INTERCEPT}).
 *
 * <p><b>Precondition:</b> the target has a body and is not a constructor or class initialiser;
 * {@link HeadHookResult#CANCEL_IF_TRUE} needs a {@code void} target and
 * {@link HeadHookResult#RETURN_IF_NON_NULL} a target that returns a value.
 *
 * <p><b>Hook signature:</b> {@code static R hook(Owner this, P1 p1, …)}: the receiver is omitted for
 * static targets, parameter types are the target's exact types, and {@code R} is {@code void},
 * {@code boolean}, or the target's return type (boxed when primitive) according to {@link #result()}. Hooks therefore live
 * in code compiled against the game (the game layer, ADR-0010). Exceptions thrown by the hook
 * propagate into the game.
 *
 * @param hookOwnerInternalName JVM internal name of the class declaring the hook
 * @param hookMethodName name of the public static hook method
 * @param result how the hook's return value is used
 */
@ApiStability(StabilityLevel.INTERNAL)
public record InvokeHookAtHead(String hookOwnerInternalName, String hookMethodName, HeadHookResult result)
        implements MethodTransformAction {

    public InvokeHookAtHead {
        HookReference.validate(hookOwnerInternalName, hookMethodName);
        java.util.Objects.requireNonNull(result, "result");
    }

    @Override
    public String describe() {
        return "call " + hookOwnerInternalName.replace('/', '.') + "." + hookMethodName + "(this, args) on entry ("
                + result + ")";
    }
}
