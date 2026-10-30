package dev.plexusmodloader.internal.transform;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/** What the target method does with the value an {@link InvokeHookAtHead} hook returns. */
@ApiStability(StabilityLevel.INTERNAL)
public enum HeadHookResult {
    /** The hook returns {@code void}; the original body always runs. */
    CONTINUE,
    /**
     * The target returns {@code void} and the hook returns {@code boolean}; {@code true} returns from
     * the target immediately, skipping the original body.
     */
    CANCEL_IF_TRUE,
    /**
     * The target returns a value and the hook returns the same type, boxed for primitives (for
     * example {@code Boolean} for {@code boolean}); a non-null value is returned from the target
     * immediately, {@code null} runs the original body.
     */
    RETURN_IF_NON_NULL
}
