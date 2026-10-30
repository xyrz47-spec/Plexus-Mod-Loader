package dev.plexusmodloader.internal.transform;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;
import java.util.Objects;

/**
 * Calls a hook right before or right after matching calls in the method, and keeps the call itself (a call-site
 * observer). Unlike {@link RedirectCall}, the original call instruction stays where it was, so other transformers
 * that look for it, such as Fabric mods' Mixin injectors ({@code @At("INVOKE")}), still find it (ADR-0020).
 *
 * <p><b>Matching:</b> as {@link RedirectCall}: the call instruction names exactly {@code invoked}'s owner, name and
 * descriptor.
 *
 * <p><b>Precondition:</b> the target has a body and at least {@code ordinal + 1} matching calls (at least one when
 * {@code ordinal} is {@link #ALL}); the calls are not {@code invokespecial}; the receiver and arguments together take
 * at most two operand stack slots (they are duplicated for the hook with {@code DUP} or {@code DUP2}); for {@link
 * Timing#AFTER} the called method returns {@code void}.
 *
 * <p><b>Hook signature:</b> {@code static void hook(Owner receiver, P1 p1, ..., Pn pn)} with {@code invoked}'s
 * parameters; the receiver is omitted when {@code invoked} is static. The hook sees the same values the call gets.
 * Nothing else on the stack changes, and no branch is added, so no stack map frame changes.
 *
 * @param invoked the called method, as written in the call instruction
 * @param ordinal which matching call to observe, counted from 0 in instruction order, or {@link #ALL}
 * @param timing whether the hook runs before or after the call
 * @param hookOwnerInternalName JVM internal name of the class declaring the hook
 * @param hookMethodName name of the public static hook method
 */
@ApiStability(StabilityLevel.INTERNAL)
public record InvokeHookAtCall(
        MethodTarget invoked, int ordinal, Timing timing, String hookOwnerInternalName, String hookMethodName)
        implements MethodTransformAction {

    /** {@link #ordinal()} value that observes every matching call. */
    public static final int ALL = -1;

    /** When the hook runs relative to the call. */
    public enum Timing {
        BEFORE,
        AFTER
    }

    public InvokeHookAtCall {
        Objects.requireNonNull(invoked, "invoked");
        Objects.requireNonNull(timing, "timing");
        if (ordinal < ALL) {
            throw new IllegalArgumentException("ordinal must be " + ALL + " (all calls) or at least 0, got " + ordinal);
        }
        if (invoked.name().equals("<init>") || invoked.name().equals("<clinit>")) {
            throw new IllegalArgumentException("constructor and class initialiser calls cannot be observed");
        }
        HookReference.validate(hookOwnerInternalName, hookMethodName);
    }

    @Override
    public String describe() {
        return "call " + hookOwnerInternalName.replace('/', '.') + "." + hookMethodName + " "
                + timing.name().toLowerCase(java.util.Locale.ROOT) + " "
                + (ordinal == ALL ? "every call" : "call #" + ordinal) + " to " + invoked + " (the call stays)";
    }
}
