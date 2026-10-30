package dev.plexusmodloader.internal.transform;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;
import java.util.Objects;

/**
 * Replaces a method that returns a constant with a call to a static hook that computes the value,
 * so a game method can be routed to a Poly Mod Loader system.
 *
 * <p><b>Precondition:</b> the method body, ignoring labels, line numbers, and frames, is exactly
 * {@code LDC expectedConstant; ARETURN}.
 *
 * <p><b>Result:</b> the body becomes {@code INVOKESTATIC hookOwner.hookMethod()R; ARETURN}, where
 * {@code R} is the target method's return type. Arguments and {@code this} are not passed. The
 * hook class must be visible to the game class loader (a {@code dev.plexusmodloader.game.*} class).
 *
 * @param expectedConstant the constant the method returns today
 * @param hookOwnerInternalName JVM internal name of the class declaring the hook
 * @param hookMethodName name of the public static no-argument hook method
 */
@ApiStability(StabilityLevel.INTERNAL)
public record ReplaceConstantReturnWithStaticCall(
        String expectedConstant, String hookOwnerInternalName, String hookMethodName) implements MethodTransformAction {

    public ReplaceConstantReturnWithStaticCall {
        Objects.requireNonNull(expectedConstant, "expectedConstant");
        HookReference.validate(hookOwnerInternalName, hookMethodName);
    }

    @Override
    public String describe() {
        return "return " + hookOwnerInternalName.replace('/', '.') + "." + hookMethodName + "() instead of \""
                + expectedConstant + "\"";
    }
}
