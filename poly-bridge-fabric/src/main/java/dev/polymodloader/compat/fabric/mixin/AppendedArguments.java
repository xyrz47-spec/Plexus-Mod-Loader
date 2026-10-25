package dev.polymodloader.compat.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Arrays;
import java.util.Objects;

/**
 * The {@code Operation} a {@code @WrapOperation} handler gets when the host's patch calls the wrapped method with more
 * arguments at the end than the vanilla game does (ADR-0026 40): the handler calls it with the vanilla arguments, and it
 * calls the patched call's {@code Operation} with those followed by the arguments the patch added, as the patched game
 * passed them. NeoForge calls {@code CustomPacketPayload.codec(fallback, types, protocol, flow)} where vanilla calls
 * {@code codec(fallback, types)}; Fabric API wraps the vanilla call to give the payload codec Fabric's payload types.
 *
 * <p>Thread: whichever thread runs the wrapped call; the object is used once, for that call.
 */
public final class AppendedArguments implements Operation<Object> {

    private final Operation<?> patched;
    private final Object[] appended;

    /**
     * @param patched the patched call's {@code Operation}
     * @param appended the arguments the patch passes after the vanilla ones, in order
     */
    public AppendedArguments(Operation<?> patched, Object[] appended) {
        this.patched = Objects.requireNonNull(patched, "patched");
        this.appended = appended.clone();
    }

    @Override
    public Object call(Object... arguments) {
        Object[] all = Arrays.copyOf(arguments, arguments.length + appended.length);
        System.arraycopy(appended, 0, all, arguments.length, appended.length);
        return patched.call(all);
    }
}
