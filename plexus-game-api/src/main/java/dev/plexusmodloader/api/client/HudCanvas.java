package dev.plexusmodloader.api.client;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/**
 * Drawing surface in GUI-scaled pixels; {@code (0, 0)} is the top-left corner. Colours are ARGB
 * ({@code 0xFFFFFFFF} is opaque white). Valid only during {@link HudLayer#render(HudCanvas)}.
 */
@ApiStability(StabilityLevel.PREVIEW)
public interface HudCanvas {

    /** Width of the screen in GUI pixels. */
    int width();

    /** Height of the screen in GUI pixels. */
    int height();

    /** Width of {@code text} in the game font. */
    int textWidth(String text);

    /** Draws text with a shadow. */
    void drawText(String text, int x, int y, int argb);

    /** Fills the rectangle from {@code (x1, y1)} inclusive to {@code (x2, y2)} exclusive. */
    void fill(int x1, int y1, int x2, int y2, int argb);

    /**
     * Draws a whole texture, such as {@code my_mod:textures/gui/icon.png} or a {@link DynamicTexture},
     * stretched to {@code width × height}.
     */
    void drawTexture(dev.plexusmodloader.api.resource.ResourceId texture, int x, int y, int width, int height);
}
