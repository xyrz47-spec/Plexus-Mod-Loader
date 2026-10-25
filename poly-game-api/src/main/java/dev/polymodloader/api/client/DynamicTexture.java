package dev.polymodloader.api.client;

import dev.polymodloader.api.resource.ResourceId;
import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;

/**
 * A texture whose pixels the mod sets while the game runs, for example a minimap. Draw it with
 * {@link HudCanvas#drawTexture}. Use it on the client thread; changes appear after {@link #upload()}.
 */
@ApiStability(StabilityLevel.PREVIEW)
public interface DynamicTexture {

    /** The texture's ID, {@code <mod id>:dynamic/<path>}. */
    ResourceId id();

    /** Width in pixels. */
    int width();

    /** Height in pixels. */
    int height();

    /** Sets one pixel, {@code 0xAARRGGBB}. */
    void setPixel(int x, int y, int argb);

    /** Sets every pixel. */
    void fill(int argb);

    /** Sends the pixels to the graphics card. */
    void upload();
}
