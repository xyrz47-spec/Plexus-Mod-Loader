package dev.polymodloader.api.worldgen;

import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;

/**
 * A placed feature written in code: a boulder, a plant patch, an ore cluster with a custom shape.
 * Called on world generation threads while chunks generate, possibly for several chunks at once; keep
 * all state in local variables and take randomness only from {@link FeatureContext#random()}.
 */
@ApiStability(StabilityLevel.PREVIEW)
@FunctionalInterface
public interface CodeFeature {

    /**
     * Places the feature around {@link FeatureContext#origin()}.
     *
     * @return whether anything was placed
     */
    boolean place(FeatureContext context);
}
