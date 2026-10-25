package dev.polymodloader.api.client;

import dev.polymodloader.api.content.block.state.BlockStateView;
import dev.polymodloader.api.resource.ResourceId;
import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;

/**
 * Draws in the world relative to a block: {@code (0, 0, 0)} is the block's lowest corner and
 * {@code (1, 1, 1)} its highest. Valid only during {@link BlockEntityPainter#paint}.
 */
@ApiStability(StabilityLevel.PREVIEW)
public interface BlockEntityCanvas {

    /**
     * Draws an item as in an item frame.
     *
     * @param scale 1 is a full block
     * @param yawDegrees turn around the vertical axis
     * @throws IllegalArgumentException for an unknown item
     */
    void item(ResourceId item, double x, double y, double z, float scale, float yawDegrees);

    /**
     * Draws text centred at the position, facing the camera.
     *
     * @param scale a line of text is 9 × scale blocks high (0.025 makes a small label)
     */
    void text(String text, double x, double y, double z, float scale, int argb);

    /** World time plus the partial tick, for smooth animation. */
    float animationTime();

    /**
     * Draws the block model of {@code block}'s default state, centred on {@code x, y, z} and turned
     * {@code yawDegrees} around the vertical axis; {@code scale} 1 is full block size.
     *
     * @throws IllegalArgumentException if {@code block} is not a block
     */
    default void block(ResourceId block, double x, double y, double z, float scale, float yawDegrees) {
        block(defaultState(block), x, y, z, scale, yawDegrees);
    }

    /**
     * Draws the block model of {@code state}, for example a lamp that is lit, placed like {@link
     * #block(ResourceId, double, double, double, float, float)}. Get a state from {@link
     * #defaultState} or {@link dev.polymodloader.api.world.WorldView#stateAt}, and change it with {@link
     * BlockStateView#with}.
     */
    void block(BlockStateView state, double x, double y, double z, float scale, float yawDegrees);

    /**
     * The default state of {@code block}, to change with {@link BlockStateView#with} and draw with
     * {@link #block(BlockStateView, double, double, double, float, float)}.
     *
     * @throws IllegalArgumentException if {@code block} is not a block
     */
    BlockStateView defaultState(ResourceId block);

    /**
     * Quads drawn with a mod shader pipeline, four vertices per quad, in the same coordinates as the
     * other draws (the block's lowest corner is {@code 0, 0, 0}).
     */
    void quads(ShaderPipeline pipeline, java.util.function.Consumer<VertexSink> vertices);
}
