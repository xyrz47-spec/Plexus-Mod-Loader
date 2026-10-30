package dev.polymodloader.api.client;

import dev.polymodloader.api.resource.ResourceId;
import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;

/** A rebindable key. Query it on the client thread, for example from {@link ClientEvents#TICK}. */
@ApiStability(StabilityLevel.PREVIEW)
public interface KeyBinding {

    /** The binding's ID. */
    ResourceId id();

    /** Whether the key is held now. */
    boolean isDown();

    /** Returns {@code true} once per press since the last call, so no press is lost between ticks. */
    boolean consumeClick();
}
