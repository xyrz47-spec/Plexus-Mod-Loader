package dev.polymodloader.api.client;

import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;

/** Listener for {@link ClientEvents#FIELD_OF_VIEW}. */
@ApiStability(StabilityLevel.PREVIEW)
@FunctionalInterface
public interface FovModifier {

    /** Returns the field of view in degrees to use instead of {@code fov}; listeners chain. */
    float modifyFov(float fov);
}
