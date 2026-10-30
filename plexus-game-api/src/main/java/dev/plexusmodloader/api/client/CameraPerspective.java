package dev.plexusmodloader.api.client;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/** Where the camera is, as cycled with F5. */
@ApiStability(StabilityLevel.PREVIEW)
public enum CameraPerspective {
    FIRST_PERSON,
    THIRD_PERSON_BACK,
    THIRD_PERSON_FRONT
}
