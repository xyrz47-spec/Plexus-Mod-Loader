package dev.plexusmodloader.api.content.entity.model;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A keyframe animation of a mob's box model, like Blockbench's animations: per part and channel,
 * values at points in time, blended in between. The values are added to the part's rest pose.
 *
 * @param lengthSeconds length in seconds
 * @param looping whether it starts over at the end; otherwise an {@link Trigger#ON_DEMAND} animation
 *     ends there and the others hold their last keyframe
 * @param trigger when it plays
 * @param channels the animated values; at most one channel per part and target
 */
@ApiStability(StabilityLevel.PREVIEW)
public record KeyframeAnimation(float lengthSeconds, boolean looping, Trigger trigger, List<Channel> channels) {

    public KeyframeAnimation {
        if (!(lengthSeconds > 0)) {
            throw new IllegalArgumentException("lengthSeconds must be > 0: " + lengthSeconds);
        }
        Objects.requireNonNull(trigger, "trigger");
        channels = List.copyOf(channels);
        if (channels.isEmpty()) {
            throw new IllegalArgumentException("An animation needs at least one keyframe");
        }
    }

    /** When an animation plays. */
    public enum Trigger {
        /** All the time, from when the mob appears; usually looping (idle breathing, a spinning part). */
        ALWAYS,
        /** While the mob moves, faster the faster it moves; usually looping (walk cycle, wing beats). */
        WHILE_MOVING,
        /**
         * When the server starts it with {@link dev.plexusmodloader.api.entity.MobView#playAnimation};
         * every client that sees the mob plays it from the start (attack, wave).
         */
        ON_DEMAND
    }

    /** What a channel changes. */
    public enum Target {
        /** Rotation in degrees around x, y and z. */
        ROTATION,
        /** Offset in model pixels; y grows upwards, as in Blockbench. */
        POSITION,
        /** Size factor per axis; 1 keeps the size. */
        SCALE
    }

    /** How values are blended between two keyframes. */
    public enum Interpolation {
        /** In a straight line. */
        LINEAR,
        /** Along a smooth curve through the neighbouring keyframes (Catmull-Rom). */
        SMOOTH
    }

    /**
     * A value at a point in time.
     *
     * @param time seconds from the start, {@code 0..lengthSeconds}
     * @param x value for x
     * @param y value for y
     * @param z value for z
     * @param interpolation how the value is reached from the previous keyframe
     */
    public record Keyframe(float time, float x, float y, float z, Interpolation interpolation) {
        public Keyframe {
            if (time < 0) {
                throw new IllegalArgumentException("Keyframe time must be >= 0: " + time);
            }
            Objects.requireNonNull(interpolation, "interpolation");
        }
    }

    /**
     * The keyframes of one part and target, in time order.
     *
     * @param part model part name
     * @param target what the keyframes change
     * @param keyframes the keyframes, sorted by time
     */
    public record Channel(String part, Target target, List<Keyframe> keyframes) {
        public Channel {
            Objects.requireNonNull(part, "part");
            Objects.requireNonNull(target, "target");
            keyframes = keyframes.stream()
                    .sorted(Comparator.comparingDouble(Keyframe::time))
                    .toList();
            if (keyframes.isEmpty()) {
                throw new IllegalArgumentException("A channel needs at least one keyframe");
            }
        }
    }

    /** A builder for an animation {@code lengthSeconds} long that plays when the server starts it. */
    public static Builder builder(float lengthSeconds) {
        return new Builder(lengthSeconds);
    }

    /** Builds a {@link KeyframeAnimation}. */
    public static final class Builder {

        private final float lengthSeconds;
        private boolean looping;
        private Trigger trigger = Trigger.ON_DEMAND;
        private Interpolation interpolation = Interpolation.LINEAR;
        private final Map<String, List<Keyframe>> keyframes = new LinkedHashMap<>();

        private Builder(float lengthSeconds) {
            this.lengthSeconds = lengthSeconds;
        }

        /** Starts over at the end. */
        public Builder looping() {
            this.looping = true;
            return this;
        }

        /** When the animation plays; {@link Trigger#ON_DEMAND} by default. */
        public Builder trigger(Trigger trigger) {
            this.trigger = Objects.requireNonNull(trigger, "trigger");
            return this;
        }

        /** Blends the keyframes added after this call along smooth curves instead of straight lines. */
        public Builder smooth() {
            this.interpolation = Interpolation.SMOOTH;
            return this;
        }

        /** Rotates {@code part} by {@code x, y, z} degrees at {@code time} seconds. */
        public Builder rotation(String part, float time, float x, float y, float z) {
            return keyframe(part, Target.ROTATION, time, x, y, z, interpolation);
        }

        /** Moves {@code part} by {@code x, y, z} model pixels (y up) at {@code time} seconds. */
        public Builder position(String part, float time, float x, float y, float z) {
            return keyframe(part, Target.POSITION, time, x, y, z, interpolation);
        }

        /** Scales {@code part} by {@code x, y, z} (1 keeps the size) at {@code time} seconds. */
        public Builder scale(String part, float time, float x, float y, float z) {
            return keyframe(part, Target.SCALE, time, x, y, z, interpolation);
        }

        /** Adds a keyframe with every detail given. */
        public Builder keyframe(
                String part, Target target, float time, float x, float y, float z, Interpolation interpolation) {
            if (time > lengthSeconds) {
                throw new IllegalArgumentException(
                        "Keyframe at " + time + " s is after the end of the " + lengthSeconds + " s animation");
            }
            keyframes
                    .computeIfAbsent(part + "\u0000" + target.name(), ignored -> new ArrayList<>())
                    .add(new Keyframe(time, x, y, z, interpolation));
            return this;
        }

        /** Creates the animation; invalid values throw {@link IllegalArgumentException}. */
        public KeyframeAnimation build() {
            List<Channel> channels = new ArrayList<>();
            keyframes.forEach((key, frames) -> {
                int separator = key.indexOf('\u0000');
                channels.add(
                        new Channel(key.substring(0, separator), Target.valueOf(key.substring(separator + 1)), frames));
            });
            return new KeyframeAnimation(lengthSeconds, looping, trigger, channels);
        }
    }
}
