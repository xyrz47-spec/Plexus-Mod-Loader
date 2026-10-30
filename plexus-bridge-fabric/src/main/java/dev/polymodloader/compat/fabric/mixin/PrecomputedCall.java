package dev.polymodloader.compat.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

/**
 * The {@code Operation} a {@code @WrapOperation} handler gets when Poly Mod Loader's bridge has already made the wrapped
 * call and changed its result into the shape the handler was written for ({@link HostOptionalResults}): calling it
 * returns that result. The bridge is only used for a handler that calls its {@code Operation} once, with its own
 * arguments unchanged, which the check before the launch verifies; so the result is the one the call would give.
 *
 * <p>Thread: whichever thread runs the wrapped call; the object is used once, for that call.
 */
public final class PrecomputedCall implements Operation<Object> {

    private final Object result;

    /** @param result what the wrapped call returned, in the shape the handler expects */
    public PrecomputedCall(Object result) {
        this.result = result;
    }

    @Override
    public Object call(Object... arguments) {
        return result;
    }
}
