package dev.polymodloader.compat.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Objects;

/**
 * The {@code Operation} a {@code @WrapOperation} handler gets when the host's patch does the wrapped call's job with
 * another call ({@link HostEffectRemoval}): calling it makes the host's call with the host's arguments. The arguments
 * the handler passes, the vanilla call's, are not used; the check before the launch verifies that the handler passes its
 * own unchanged, once.
 *
 * <p>Thread: whichever thread runs the wrapped call; the object is used once, for that call.
 */
public final class SubstituteCall implements Operation<Object> {

    private final Operation<?> host;
    private final Object[] arguments;

    /**
     * @param host the host's call's {@code Operation}
     * @param arguments the arguments the host's call got, in order
     */
    public SubstituteCall(Operation<?> host, Object[] arguments) {
        this.host = Objects.requireNonNull(host, "host");
        this.arguments = arguments.clone();
    }

    @Override
    public Object call(Object... ignored) {
        return host.call(arguments);
    }
}
