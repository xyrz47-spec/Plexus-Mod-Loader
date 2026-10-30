package dev.plexusmodloader.api.client;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;
import dev.plexusmodloader.api.world.BlockPos;
import java.util.function.Consumer;

/**
 * Draws in the world for one frame, in world coordinates, valid only during {@link WorldPainter#paint}.
 * Lines, boxes, points and text are hidden behind blocks unless drawn through {@link #onTop()}.
 * Colours are {@code 0xAARRGGBB}; an alpha of 0 draws nothing.
 */
@ApiStability(StabilityLevel.PREVIEW)
public interface WorldCanvas {

    /** Camera x this frame. */
    double cameraX();

    /** Camera y this frame. */
    double cameraY();

    /** Camera z this frame. */
    double cameraZ();

    /** Game time in ticks plus the partial tick, for smooth animation. */
    float animationTime();

    /** A line from one point to another, {@code width} pixels wide. */
    void line(double x1, double y1, double z1, double x2, double y2, double z2, int argb, float width);

    /**
     * A box between two corners: its edges in {@code strokeArgb} ({@code width} pixels wide) and its
     * faces filled with {@code fillArgb}; pass 0 for either to leave it out.
     */
    void box(
            double minX,
            double minY,
            double minZ,
            double maxX,
            double maxY,
            double maxZ,
            int strokeArgb,
            float width,
            int fillArgb);

    /** The edges of one block. */
    default void blockOutline(BlockPos pos, int argb, float width) {
        box(pos.x(), pos.y(), pos.z(), pos.x() + 1, pos.y() + 1, pos.z() + 1, argb, width, 0);
    }

    /** A square dot {@code size} pixels wide. */
    void point(double x, double y, double z, int argb, float size);

    /** Text facing the camera, centred on the point; {@code scale} 1 is the game's label size. */
    void text(String text, double x, double y, double z, int argb, float scale);

    /**
     * Quads drawn with a mod shader pipeline: four vertices per quad, in world coordinates. Unlike the
     * other shapes, these follow the pipeline's depth settings.
     */
    void quads(ShaderPipeline pipeline, Consumer<VertexSink> vertices);

    /** This canvas, drawing lines, boxes, points and text in front of blocks. */
    WorldCanvas onTop();
}
