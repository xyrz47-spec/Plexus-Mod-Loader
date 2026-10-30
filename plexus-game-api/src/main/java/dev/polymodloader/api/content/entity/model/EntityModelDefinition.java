package dev.polymodloader.api.content.entity.model;

import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * A box model for a mod mob, in the game's model space: units are pixels (1/16 block), y grows
 * downwards, and a root part with pivot y 24 stands on the ground. This is the layout that
 * Blockbench's "Modded Entity" projects use. The texture is the mob's texture with
 * {@code textureWidth × textureHeight} pixels.
 *
 * @param textureWidth texture width in pixels
 * @param textureHeight texture height in pixels
 * @param parts the parts; parents come before their children
 * @param animations keyframe animations by name ({@code [a-z0-9_]+}), in declaration order
 */
@ApiStability(StabilityLevel.PREVIEW)
public record EntityModelDefinition(
        int textureWidth,
        int textureHeight,
        List<ModelPartDefinition> parts,
        Map<String, KeyframeAnimation> animations) {

    /** At most this many {@link KeyframeAnimation.Trigger#ON_DEMAND} animations per model. */
    public static final int MAX_ON_DEMAND_ANIMATIONS = 64;

    private static final Pattern ANIMATION_NAME = Pattern.compile("[a-z0-9_]+");

    public EntityModelDefinition {
        if (textureWidth < 1 || textureHeight < 1) {
            throw new IllegalArgumentException("Texture size must be >= 1");
        }
        parts = List.copyOf(parts);
        if (parts.isEmpty()) {
            throw new IllegalArgumentException("A model needs at least one part");
        }
        Set<String> seen = new HashSet<>();
        for (ModelPartDefinition part : parts) {
            if (part.parent().isPresent() && !seen.contains(part.parent().get())) {
                throw new IllegalArgumentException(
                        "Part " + part.name() + " names parent " + part.parent().get() + " before it is defined");
            }
            if (!seen.add(part.name())) {
                throw new IllegalArgumentException("Part " + part.name() + " is defined twice");
            }
        }
        animations = Collections.unmodifiableMap(new LinkedHashMap<>(animations));
        int onDemand = 0;
        for (Map.Entry<String, KeyframeAnimation> animation : animations.entrySet()) {
            if (!ANIMATION_NAME.matcher(animation.getKey()).matches()) {
                throw new IllegalArgumentException(
                        "Animation name must match [a-z0-9_]+: '" + animation.getKey() + "'");
            }
            for (KeyframeAnimation.Channel channel : animation.getValue().channels()) {
                if (!seen.contains(channel.part())) {
                    throw new IllegalArgumentException("Animation " + animation.getKey() + " moves part "
                            + channel.part() + ", which the model does not have");
                }
            }
            if (animation.getValue().trigger() == KeyframeAnimation.Trigger.ON_DEMAND) {
                onDemand++;
            }
        }
        if (onDemand > MAX_ON_DEMAND_ANIMATIONS) {
            throw new IllegalArgumentException(
                    "A model can have at most " + MAX_ON_DEMAND_ANIMATIONS + " ON_DEMAND animations, not " + onDemand);
        }
    }

    /** Names of the {@link KeyframeAnimation.Trigger#ON_DEMAND} animations, in declaration order. */
    public List<String> onDemandAnimations() {
        return animations.entrySet().stream()
                .filter(entry -> entry.getValue().trigger() == KeyframeAnimation.Trigger.ON_DEMAND)
                .map(Map.Entry::getKey)
                .toList();
    }

    /** A builder for a model using a {@code textureWidth × textureHeight} texture. */
    public static Builder builder(int textureWidth, int textureHeight) {
        return new Builder(textureWidth, textureHeight);
    }

    /** Builds an {@link EntityModelDefinition}. */
    public static final class Builder {

        private final int textureWidth;
        private final int textureHeight;
        private final List<ModelPartDefinition> parts = new ArrayList<>();
        private final Map<String, KeyframeAnimation> animations = new LinkedHashMap<>();

        private Builder(int textureWidth, int textureHeight) {
            this.textureWidth = textureWidth;
            this.textureHeight = textureHeight;
        }

        /** Adds a part configured by {@code part}. */
        public Builder part(String name, Consumer<ModelPartDefinition.Builder> part) {
            ModelPartDefinition.Builder builder = new ModelPartDefinition.Builder(name);
            part.accept(builder);
            parts.add(builder.build());
            return this;
        }

        /**
         * Adds a keyframe animation called {@code name}; {@link KeyframeAnimation.Trigger#ON_DEMAND} ones
         * are started by name with {@link dev.polymodloader.api.entity.MobView#playAnimation}.
         */
        public Builder animation(String name, KeyframeAnimation animation) {
            if (animations.putIfAbsent(name, Objects.requireNonNull(animation, "animation")) != null) {
                throw new IllegalArgumentException("Animation " + name + " is defined twice");
            }
            return this;
        }

        /** Creates the model; invalid parts throw {@link IllegalArgumentException}. */
        public EntityModelDefinition build() {
            return new EntityModelDefinition(textureWidth, textureHeight, parts, animations);
        }
    }
}
