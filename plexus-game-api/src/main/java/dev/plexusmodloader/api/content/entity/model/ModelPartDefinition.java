package dev.plexusmodloader.api.content.entity.model;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A named part of an entity model: boxes around a pivot, an optional parent part, a rest rotation in
 * degrees, and an animation.
 *
 * @param name part name, unique in the model
 * @param parent parent part, or empty for the root
 * @param pivotX pivot x relative to the parent's pivot (model pixels)
 * @param pivotY pivot y (24 is the ground for root parts, y grows downwards)
 * @param pivotZ pivot z
 * @param rotationX rest rotation around x, degrees
 * @param rotationY rest rotation around y, degrees
 * @param rotationZ rest rotation around z, degrees
 * @param boxes the part's cuboids
 * @param animation how it moves
 */
@ApiStability(StabilityLevel.PREVIEW)
public record ModelPartDefinition(
        String name,
        Optional<String> parent,
        float pivotX,
        float pivotY,
        float pivotZ,
        float rotationX,
        float rotationY,
        float rotationZ,
        List<ModelBox> boxes,
        PartAnimation animation) {

    public ModelPartDefinition {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(parent, "parent");
        boxes = List.copyOf(boxes);
        Objects.requireNonNull(animation, "animation");
    }

    /** Builds a {@link ModelPartDefinition}. */
    public static final class Builder {

        private final String name;
        private Optional<String> parent = Optional.empty();
        private float pivotX;
        private float pivotY;
        private float pivotZ;
        private float rotationX;
        private float rotationY;
        private float rotationZ;
        private final List<ModelBox> boxes = new ArrayList<>();
        private PartAnimation animation = PartAnimation.NONE;

        Builder(String name) {
            this.name = name;
        }

        /** Attaches the part to {@code parent}; its pivot becomes relative to the parent's. */
        public Builder parent(String parent) {
            this.parent = Optional.of(parent);
            return this;
        }

        /** The point the part turns around, relative to the parent's pivot (model pixels, y down). */
        public Builder pivot(float x, float y, float z) {
            this.pivotX = x;
            this.pivotY = y;
            this.pivotZ = z;
            return this;
        }

        /** Rest rotation in degrees. */
        public Builder rotation(float x, float y, float z) {
            this.rotationX = x;
            this.rotationY = y;
            this.rotationZ = z;
            return this;
        }

        /** Adds a box whose texture net starts at {@code u, v}. */
        public Builder box(int u, int v, float x, float y, float z, float width, float height, float depth) {
            boxes.add(new ModelBox(u, v, x, y, z, width, height, depth, false));
            return this;
        }

        /** Adds a box with a mirrored texture. */
        public Builder mirroredBox(int u, int v, float x, float y, float z, float width, float height, float depth) {
            boxes.add(new ModelBox(u, v, x, y, z, width, height, depth, true));
            return this;
        }

        /** How the part moves on its own; {@link PartAnimation#NONE} by default. */
        public Builder animation(PartAnimation animation) {
            this.animation = animation;
            return this;
        }

        ModelPartDefinition build() {
            return new ModelPartDefinition(
                    name, parent, pivotX, pivotY, pivotZ, rotationX, rotationY, rotationZ, boxes, animation);
        }
    }
}
