package dev.polymodloader.internal.transform;

import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;
import java.util.Objects;

/**
 * Replaces calls from the target method to {@link #invoked()} with a call to a static hook, which can
 * call the original method, pass other arguments, or return something else (a call-site redirect).
 *
 * <p><b>Matching:</b> a call matches when its instruction names exactly {@code invoked}'s owner, name and
 * descriptor. The owner is the class written in the call instruction, which can be a subclass of the
 * declaring class (for example {@code ServerLevel} instead of {@code Level}); check it with {@code
 * javap -c}.
 *
 * <p><b>Precondition:</b> the target has a body, and at least {@code ordinal + 1} matching calls (at
 * least one when {@code ordinal} is {@link #ALL}). Constructor calls ({@code <init>}), {@code super} and
 * other {@code invokespecial} calls, and {@code invokedynamic} cannot be redirected.
 *
 * <p><b>Hook signature:</b> {@code static R hook(Owner receiver, P1 p1, ..., Pn pn)} with {@code invoked}'s
 * parameters and return type {@code R}; the receiver is omitted when {@code invoked} is static. The
 * stack shape does not change, so no stack map frame changes.
 *
 * @param invoked the called method, as written in the call instruction
 * @param ordinal which matching call to replace, counted from 0 in instruction order, or {@link #ALL}
 * @param hookOwnerInternalName JVM internal name of the class declaring the hook
 * @param hookMethodName name of the public static hook method
 */
@ApiStability(StabilityLevel.INTERNAL)
public record RedirectCall(MethodTarget invoked, int ordinal, String hookOwnerInternalName, String hookMethodName)
        implements MethodTransformAction {

    /** {@link #ordinal()} value that replaces every matching call. */
    public static final int ALL = -1;

    public RedirectCall {
        Objects.requireNonNull(invoked, "invoked");
        if (ordinal < ALL) {
            throw new IllegalArgumentException("ordinal must be " + ALL + " (all calls) or at least 0, got " + ordinal);
        }
        if (invoked.name().equals("<init>") || invoked.name().equals("<clinit>")) {
            throw new IllegalArgumentException("constructor and class initialiser calls cannot be redirected");
        }
        HookReference.validate(hookOwnerInternalName, hookMethodName);
    }

    @Override
    public String describe() {
        return "redirect " + (ordinal == ALL ? "every call" : "call #" + ordinal) + " to " + invoked + " to "
                + hookOwnerInternalName.replace('/', '.') + "." + hookMethodName;
    }
}
