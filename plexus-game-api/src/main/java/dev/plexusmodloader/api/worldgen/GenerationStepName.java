package dev.plexusmodloader.api.worldgen;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/** The feature placement steps of world generation, in the order the game runs them. */
@ApiStability(StabilityLevel.PREVIEW)
public enum GenerationStepName {
    RAW_GENERATION,
    LAKES,
    LOCAL_MODIFICATIONS,
    UNDERGROUND_STRUCTURES,
    SURFACE_STRUCTURES,
    STRONGHOLDS,
    UNDERGROUND_ORES,
    UNDERGROUND_DECORATION,
    FLUID_SPRINGS,
    VEGETAL_DECORATION,
    TOP_LAYER_MODIFICATION
}
