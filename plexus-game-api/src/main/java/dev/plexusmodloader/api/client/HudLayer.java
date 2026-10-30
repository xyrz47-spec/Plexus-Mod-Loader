package dev.plexusmodloader.api.client;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/** Draws over the in-game HUD. Called every frame on the client (render) thread while in a world. */
@ApiStability(StabilityLevel.PREVIEW)
@FunctionalInterface
public interface HudLayer {

    /**
     * Draws the layer. Keep it fast: it runs every frame. Exceptions are logged once
     * ({@code PLEXUS-CLIENT-003}) and the layer is disabled.
     */
    void render(HudCanvas canvas);
}
