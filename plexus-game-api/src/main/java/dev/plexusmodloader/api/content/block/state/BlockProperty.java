package dev.plexusmodloader.api.content.block.state;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;
import dev.plexusmodloader.api.world.Direction;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

/**
 * A block state property such as {@code lit=true}, {@code level=3}, or {@code facing=north}. Each
 * combination of values is one block state; blockstate files in the resource pack select models by
 * these names and values. Property names use {@code a-z 0-9 _}. The same property object may be read
 * from any block that has a property with the same name and value type, including the game's own
 * blocks (for example {@code facing} of a furnace).
 *
 * <p>A {@link #choice choice} property has named values ({@code shape=round}); it also reads the
 * game's own named properties such as {@code half} of stairs or {@code axis} of logs.
 *
 * @param <T> value type: {@link Boolean}, {@link Integer}, {@link Direction}, or {@link String}
 */
@ApiStability(StabilityLevel.PREVIEW)
public sealed interface BlockProperty<T> {

    /** Property name used in blockstate files and commands. */
    String name();

    /** Value of the block's default state. */
    T defaultValue();

    /** Every allowed value, in the game's order. */
    List<T> values();

    /** A {@code true}/{@code false} property, default {@code false}. */
    static BooleanProperty bool(String name) {
        return new BooleanProperty(name, false);
    }

    /** A whole-number property in {@code [min, max]} with {@code 0 <= min < max <= min + 255}, default {@code min}. */
    static IntegerProperty integer(String name, int min, int max) {
        return new IntegerProperty(name, min, max, min);
    }

    /** A horizontal facing ({@code north}, {@code south}, {@code west}, {@code east}), default north. */
    static DirectionProperty horizontalFacing(String name) {
        return new DirectionProperty(name, true, Direction.NORTH);
    }

    /** Any of the six directions, default north. */
    static DirectionProperty direction(String name) {
        return new DirectionProperty(name, false, Direction.NORTH);
    }

    /**
     * A property with named values, in this order; the first is the default. Values use
     * {@code a-z 0-9 _}.
     */
    static ChoiceProperty choice(String name, String... values) {
        return new ChoiceProperty(name, List.of(values), values.length > 0 ? values[0] : "");
    }

    /** A boolean property. */
    record BooleanProperty(String name, Boolean defaultValue) implements BlockProperty<Boolean> {
        public BooleanProperty {
            Names.check(name);
            Objects.requireNonNull(defaultValue, "defaultValue");
        }

        @Override
        public List<Boolean> values() {
            return List.of(false, true);
        }

        /** The same property with another default. */
        public BooleanProperty withDefault(boolean value) {
            return new BooleanProperty(name, value);
        }
    }

    /** An integer property. */
    record IntegerProperty(String name, int min, int max, Integer defaultValue) implements BlockProperty<Integer> {
        public IntegerProperty {
            Names.check(name);
            if (min < 0 || max <= min || max - min > 255) {
                throw new IllegalArgumentException(
                        "Integer property " + name + " needs 0 <= min < max <= min + 255: " + min + ".." + max);
            }
            if (defaultValue < min || defaultValue > max) {
                throw new IllegalArgumentException("Default " + defaultValue + " is outside " + min + ".." + max);
            }
        }

        @Override
        public List<Integer> values() {
            return IntStream.rangeClosed(min, max).boxed().toList();
        }

        /** The same property with another default. */
        public IntegerProperty withDefault(int value) {
            return new IntegerProperty(name, min, max, value);
        }
    }

    /** A direction property. */
    record DirectionProperty(String name, boolean horizontalOnly, Direction defaultValue)
            implements BlockProperty<Direction> {
        public DirectionProperty {
            Names.check(name);
            Objects.requireNonNull(defaultValue, "defaultValue");
            if (horizontalOnly && !defaultValue.isHorizontal()) {
                throw new IllegalArgumentException(
                        "Horizontal property " + name + " cannot default to " + defaultValue);
            }
        }

        @Override
        public List<Direction> values() {
            return Arrays.stream(Direction.values())
                    .filter(direction -> !horizontalOnly || direction.isHorizontal())
                    .toList();
        }

        /** The same property with another default. */
        public DirectionProperty withDefault(Direction value) {
            return new DirectionProperty(name, horizontalOnly, value);
        }
    }

    /** A property with named values. */
    record ChoiceProperty(String name, List<String> values, String defaultValue) implements BlockProperty<String> {
        public ChoiceProperty {
            Names.check(name);
            values = List.copyOf(values);
            if (values.size() < 2) {
                throw new IllegalArgumentException("Choice property " + name + " needs at least two values");
            }
            if (new java.util.HashSet<>(values).size() != values.size()) {
                throw new IllegalArgumentException("Choice property " + name + " repeats a value: " + values);
            }
            values.forEach(Names::check);
            if (!values.contains(defaultValue)) {
                throw new IllegalArgumentException("Default " + defaultValue + " is not one of " + values);
            }
        }

        /** The same property with another default. */
        public ChoiceProperty withDefault(String value) {
            return new ChoiceProperty(name, values, value);
        }
    }

    /** Name validation shared by the property records. */
    final class Names {
        private static final Pattern NAME = Pattern.compile("[a-z0-9_]+");

        private Names() {}

        static void check(String name) {
            if (name == null || !NAME.matcher(name).matches()) {
                throw new IllegalArgumentException(
                        "Invalid block property name or value '" + name + "': use a-z 0-9 _");
            }
        }
    }
}
