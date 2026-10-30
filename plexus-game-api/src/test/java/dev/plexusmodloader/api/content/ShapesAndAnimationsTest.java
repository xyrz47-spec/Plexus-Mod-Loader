package dev.plexusmodloader.api.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.plexusmodloader.api.content.block.BlockShape;
import dev.plexusmodloader.api.content.entity.model.EntityModelDefinition;
import dev.plexusmodloader.api.content.entity.model.KeyframeAnimation;
import dev.plexusmodloader.api.world.Direction;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShapesAndAnimationsTest {

    /** A box along the north edge, as drawn facing north. */
    private static final BlockShape NORTH_EDGE = BlockShape.box(2, 0, 0, 6, 4, 3);

    @Test
    void shapesTurnAroundTheBlockCentre() {
        assertEquals(NORTH_EDGE, NORTH_EDGE.facing(Direction.NORTH));
        assertEquals(BlockShape.box(13, 0, 2, 16, 4, 6), NORTH_EDGE.facing(Direction.EAST));
        assertEquals(BlockShape.box(10, 0, 13, 14, 4, 16), NORTH_EDGE.facing(Direction.SOUTH));
        assertEquals(BlockShape.box(0, 0, 10, 3, 4, 14), NORTH_EDGE.facing(Direction.WEST));
        assertThrows(IllegalArgumentException.class, () -> NORTH_EDGE.facing(Direction.UP));
    }

    @Test
    void shapesCombineAndCheckBounds() {
        BlockShape post = BlockShape.box(0, 0, 0, 16, 2, 16).or(BlockShape.box(6, 2, 6, 10, 16, 10));
        assertEquals(2, post.boxes().size());
        assertThrows(IllegalArgumentException.class, () -> BlockShape.box(0, 0, 0, 17, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> BlockShape.box(4, 0, 0, 4, 1, 1));
    }

    @Test
    void keyframesAreGroupedByPartAndTargetInTimeOrder() {
        KeyframeAnimation wave = KeyframeAnimation.builder(1)
                .rotation("arm", 1, 0, 0, 0)
                .rotation("arm", 0, 0, 0, 0)
                .smooth()
                .rotation("arm", 0.5f, 90, 0, 0)
                .position("arm", 0, 0, 1, 0)
                .build();

        assertEquals(KeyframeAnimation.Trigger.ON_DEMAND, wave.trigger());
        assertEquals(2, wave.channels().size());
        KeyframeAnimation.Channel rotation = wave.channels().get(0);
        assertEquals(KeyframeAnimation.Target.ROTATION, rotation.target());
        assertEquals(
                List.of(0f, 0.5f, 1f),
                rotation.keyframes().stream()
                        .map(KeyframeAnimation.Keyframe::time)
                        .toList());
        assertEquals(
                KeyframeAnimation.Interpolation.SMOOTH,
                rotation.keyframes().get(1).interpolation());
        assertThrows(
                IllegalArgumentException.class,
                () -> KeyframeAnimation.builder(1).rotation("arm", 2, 0, 0, 0));
    }

    @Test
    void modelsCheckAnimationParts() {
        KeyframeAnimation wave =
                KeyframeAnimation.builder(1).rotation("arm", 0, 10, 0, 0).build();
        EntityModelDefinition.Builder model = EntityModelDefinition.builder(16, 16)
                .part("body", part -> part.box(0, 0, 0, 0, 0, 1, 1, 1))
                .part("arm", part -> part.parent("body"));

        EntityModelDefinition valid = model.animation("wave", wave).build();
        assertEquals(List.of("wave"), valid.onDemandAnimations());

        EntityModelDefinition.Builder missingPart = EntityModelDefinition.builder(16, 16)
                .part("body", part -> part.box(0, 0, 0, 0, 0, 1, 1, 1))
                .animation("wave", wave);
        assertThrows(IllegalArgumentException.class, missingPart::build);
        assertThrows(
                IllegalArgumentException.class,
                () -> EntityModelDefinition.builder(16, 16)
                        .part("arm", part -> {})
                        .animation("Wave!", wave)
                        .build());
    }
}
