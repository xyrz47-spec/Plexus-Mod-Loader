package dev.plexusmodloader.api.client;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/**
 * Draws extra shapes in the world every frame while the client is in a world, on the render thread:
 * area markers, paths, debug views, glowing effects. Keep it fast; it runs every frame.
 */
@ApiStability(StabilityLevel.PREVIEW)
@FunctionalInterface
public interface WorldPainter {

    /** Draws this frame's shapes on {@code canvas}. */
    void paint(WorldCanvas canvas);
}
