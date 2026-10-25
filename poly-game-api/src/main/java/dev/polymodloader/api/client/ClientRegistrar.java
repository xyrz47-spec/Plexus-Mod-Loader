package dev.polymodloader.api.client;

import dev.polymodloader.api.resource.ResourceId;
import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;

/**
 * Client-only registrations. Obtain it in client entrypoints; on a dedicated server
 * {@code ModContext.client()} throws.
 */
@ApiStability(StabilityLevel.PREVIEW)
public interface ClientRegistrar {

    /**
     * Declares a key binding that players can rebind in Controls. Its name is translated from
     * {@code key.<mod id>.<path>}; it is listed under the category {@code key.category.<mod id>}.
     *
     * @param path binding name inside the mod's namespace
     * @param defaultKey default key, one of {@link KeyCodes}
     * @throws IllegalStateException after registration closed ({@code POLY-CLIENT-002})
     */
    KeyBinding keyBinding(String path, int defaultKey);

    /**
     * Adds a layer drawn over the game's HUD every frame, after the game's own layers, in
     * registration order.
     *
     * @throws IllegalStateException after registration closed ({@code POLY-CLIENT-002})
     */
    void hudLayer(String path, HudLayer layer);

    /**
     * Draws extra things for a mod block that has a block entity.
     *
     * @throws IllegalArgumentException for a block that is not one of this mod's, or a second painter
     */
    void blockEntityRenderer(ResourceId block, BlockEntityPainter painter);

    /** Declares a texture with pixels set by code, {@code <mod id>:dynamic/<path>}. */
    DynamicTexture dynamicTexture(String path, int width, int height);

    /**
     * Draws in the world every frame with {@code painter}.
     *
     * @throws IllegalArgumentException for an invalid or used path ({@code POLY-CLIENT-002})
     */
    void worldPainter(String path, WorldPainter painter);

    /**
     * Declares render pipeline {@code <mod id>:<path>} using the mod's own core shader, for {@link
     * WorldCanvas#quads} and {@link BlockEntityCanvas#quads}.
     *
     * @throws IllegalArgumentException for an invalid or used path ({@code POLY-CLIENT-002})
     */
    ShaderPipeline shaderPipeline(String path, ShaderPipelineDefinition definition);
}
