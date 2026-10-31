package dev.plexusmodloader.game.layer.v1_21_11.content;

import dev.plexusmodloader.api.content.block.state.BlockProperty;
import dev.plexusmodloader.api.content.block.state.BlockStateView;
import dev.plexusmodloader.api.resource.ResourceId;
import dev.plexusmodloader.game.layer.v1_21_11.Views;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

/** Converts Native API block properties and values to the game's and back. */
public final class BlockStates {

    private BlockStates() {}

    /** A new game property for a mod block. */
    static Property<?> create(BlockProperty<?> property) {
        return switch (property) {
            case BlockProperty.BooleanProperty bool -> BooleanProperty.create(bool.name());
            case BlockProperty.IntegerProperty integer ->
                IntegerProperty.create(integer.name(), integer.min(), integer.max());
            case BlockProperty.DirectionProperty direction ->
                EnumProperty.create(
                        direction.name(),
                        Direction.class,
                        direction.values().stream().map(BlockStates::toGame).toList());
            case BlockProperty.ChoiceProperty choice -> new ChoiceGameProperty(choice.name(), choice.values());
        };
    }

    /**
     * The game property of {@code state} matching {@code property} by name and value type, or null.
     * Looks at the state itself, so it also works while the block is still being created.
     */
    static Property<?> gameProperty(BlockState state, BlockProperty<?> property) {
        Property<?> game = null;
        for (Property<?> candidate : state.getProperties()) {
            if (candidate.getName().equals(property.name())) {
                game = candidate;
                break;
            }
        }
        if (game == null) {
            return null;
        }
        boolean matches = switch (property) {
            case BlockProperty.BooleanProperty ignored -> game.getValueClass() == Boolean.class;
            case BlockProperty.IntegerProperty ignored -> game.getValueClass() == Integer.class;
            case BlockProperty.DirectionProperty ignored -> game.getValueClass() == Direction.class;
            case BlockProperty.ChoiceProperty ignored ->
                game instanceof ChoiceGameProperty
                        || (game.getValueClass().isEnum()
                                && StringRepresentable.class.isAssignableFrom(game.getValueClass())
                                && game.getValueClass() != Direction.class);
        };
        return matches ? game : null;
    }

    /** {@code state} with {@code property} set to {@code value}; validates both. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T> BlockState with(BlockState state, BlockProperty<T> property, T value) {
        Property game = gameProperty(state, property);
        if (game == null) {
            throw new IllegalArgumentException(Views.blockId(state.getBlock()) + " has no property " + property.name());
        }
        Optional<Comparable> gameValue = toGameValue(game, value);
        if (gameValue.isEmpty() || !game.getPossibleValues().contains(gameValue.get())) {
            throw new IllegalArgumentException(value + " is not allowed for " + property.name() + "; allowed: "
                    + game.getPossibleValues().stream()
                            .map(allowed -> game.getName((Comparable) allowed))
                            .toList());
        }
        return state.setValue(game, gameValue.get());
    }

    /** A snapshot view of {@code state}. */
    public static BlockStateView view(BlockState state) {
        return new StateView(state);
    }

    /**
     * The game state behind a view from {@link #view}.
     *
     * @throws IllegalArgumentException for a view Plexus Mod Loader did not create
     */
    public static BlockState gameState(BlockStateView view) {
        if (view instanceof StateView(BlockState state)) {
            return state;
        }
        throw new IllegalArgumentException("not a block state from Plexus Mod Loader: " + view);
    }

    public static Direction toGame(dev.plexusmodloader.api.world.Direction direction) {
        return Direction.valueOf(direction.name());
    }

    public static dev.plexusmodloader.api.world.Direction fromGame(Direction direction) {
        return dev.plexusmodloader.api.world.Direction.valueOf(direction.name());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Optional<Comparable> toGameValue(Property game, Object value) {
        return switch (value) {
            case dev.plexusmodloader.api.world.Direction direction -> Optional.of(toGame(direction));
            case String name -> game.getValue(name);
            default -> Optional.of((Comparable) value);
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object fromGameValue(Property game, Comparable value) {
        if (value instanceof Direction direction) {
            return fromGame(direction);
        }
        if (game instanceof ChoiceGameProperty || value instanceof Enum<?>) {
            return game.getName(value);
        }
        return value;
    }

    private record StateView(BlockState state) implements BlockStateView {

        @Override
        public ResourceId block() {
            return Views.id(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
        }

        @Override
        public boolean has(BlockProperty<?> property) {
            return gameProperty(state, property) != null;
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public <T> T get(BlockProperty<T> property) {
            Property game = gameProperty(state, property);
            if (game == null) {
                throw new IllegalArgumentException(block() + " has no property " + property.name());
            }
            return (T) fromGameValue(game, state.getValue(game));
        }

        @Override
        public <T> BlockStateView with(BlockProperty<T> property, T value) {
            return new StateView(BlockStates.with(state, property, value));
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public String toString() {
            List<String> values = state.getProperties().stream()
                    .map(property -> property.getName() + "=" + ((Property) property).getName(state.getValue(property)))
                    .toList();
            return block().toString() + values;
        }
    }

    /** A game property whose values are names chosen by a mod. */
    static final class ChoiceGameProperty extends Property<String> {

        private final List<String> values;

        ChoiceGameProperty(String name, List<String> values) {
            super(name, String.class);
            this.values = List.copyOf(values);
        }

        @Override
        public List<String> getPossibleValues() {
            return values;
        }

        @Override
        public String getName(String value) {
            return value;
        }

        @Override
        public Optional<String> getValue(String name) {
            return values.contains(name) ? Optional.of(name) : Optional.empty();
        }

        @Override
        public int getInternalIndex(String value) {
            return values.indexOf(value);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ChoiceGameProperty choice
                    && choice.getName().equals(getName())
                    && choice.values.equals(values);
        }

        @Override
        public int generateHashCode() {
            return 31 * super.generateHashCode() + values.hashCode();
        }
    }
}
