package dev.plexusmodloader.internal.transform;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/**
 * Calls a static hook immediately before every return instruction of a method, so Poly Mod Loader can react
 * after the original body finished or replace the returned value.
 *
 * <p><b>Precondition:</b> the target has a body, is not a class initialiser, and never writes to
 * its parameter slots, so the arguments are still the values the caller passed at every return.
 * Constructors are allowed: {@code this} is fully initialised at their return instructions.
 *
 * <p><b>Hook signature:</b> {@code static R hook(Owner this, P1 p1, ..., R returned)}: the receiver
 * is omitted for static targets and {@code returned} for {@code void} targets. For non-void targets
 * the hook's result is what the target returns.
 *
 * @param hookOwnerInternalName JVM internal name of the class declaring the hook
 * @param hookMethodName name of the public static hook method
 */
@ApiStability(StabilityLevel.INTERNAL)
public record InvokeHookBeforeReturn(String hookOwnerInternalName, String hookMethodName)
        implements MethodTransformAction {

    public InvokeHookBeforeReturn {
        HookReference.validate(hookOwnerInternalName, hookMethodName);
    }

    @Override
    public String describe() {
        return "call " + hookOwnerInternalName.replace('/', '.') + "." + hookMethodName + " before each return";
    }
}
