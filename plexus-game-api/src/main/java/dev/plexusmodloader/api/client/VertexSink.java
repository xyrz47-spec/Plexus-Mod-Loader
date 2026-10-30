package dev.plexusmodloader.api.client;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/** Takes the vertices of quads drawn with a {@link ShaderPipeline}: four per quad, in order around it. */
@ApiStability(StabilityLevel.PREVIEW)
public interface VertexSink {

    /**
     * Adds a vertex with a texture coordinate and a colour {@code 0xAARRGGBB}; the shader receives
     * them as {@code Position}, {@code UV0} and {@code Color}. The coordinate is used only by pipelines
     * with textures; {@code (0, 0)} is the texture's top left corner and {@code (1, 1)} its bottom right.
     */
    VertexSink vertex(double x, double y, double z, float u, float v, int argb);

    /** Adds a vertex with a colour {@code 0xAARRGGBB} and texture coordinate {@code (0, 0)}. */
    default VertexSink vertex(double x, double y, double z, int argb) {
        return vertex(x, y, z, 0, 0, argb);
    }
}
