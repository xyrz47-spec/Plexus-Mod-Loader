package dev.plexusmodloader.internal.transform;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/**
 * Calls a static hook at the start of a method, before its original body runs, so Plexus Mod Loader can observe
 * or react to the call (an {@code INTERCEPT} at method entry). The original body is unchanged.
 *
 * <p><b>Precondition:</b> the target is a non-abstract instance method other than a constructor
 * (calling out before {@code super()} would expose an uninitialised object).
 *
 * <p><b>Result:</b> {@code ALOAD 0; INVOKESTATIC hookOwner.hookMethod(Ljava/lang/Object;)V} is
 * inserted before the first instruction. The hook receives {@code this} as {@link Object} because
 * Plexus Mod Loader code is compiled without Minecraft types. Exceptions thrown by the hook propagate into the
 * game, so hooks must catch and report their own failures.
 *
 * @param hookOwnerInternalName JVM internal name of the class declaring the hook
 * @param hookMethodName name of the public static {@code (Object)void} hook method
 */
@ApiStability(StabilityLevel.INTERNAL)
public record InjectStaticCallAtHead(String hookOwnerInternalName, String hookMethodName)
        implements MethodTransformAction {

    public InjectStaticCallAtHead {
        HookReference.validate(hookOwnerInternalName, hookMethodName);
    }

    @Override
    public String describe() {
        return "call " + hookOwnerInternalName.replace('/', '.') + "." + hookMethodName + "(this) on entry";
    }
}
