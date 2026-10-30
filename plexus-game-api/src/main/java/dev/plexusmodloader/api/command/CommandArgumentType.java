package dev.plexusmodloader.api.command;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/**
 * What a command argument accepts; each maps to one of the game's argument parsers, so clients
 * complete and validate them natively.
 */
@ApiStability(StabilityLevel.PREVIEW)
public sealed interface CommandArgumentType {

    /** Whole number in {@code [min, max]}; read with {@link CommandContext#getInteger(String)}. */
    record IntegerType(int min, int max) implements CommandArgumentType {
        public IntegerType {
            if (min > max) {
                throw new IllegalArgumentException("min > max");
            }
        }
    }

    /** Decimal number in {@code [min, max]}; read with {@link CommandContext#getDouble(String)}. */
    record DecimalType(double min, double max) implements CommandArgumentType {
        public DecimalType {
            if (min > max) {
                throw new IllegalArgumentException("min > max");
            }
        }
    }

    /** {@code true} or {@code false}; read with {@link CommandContext#getBoolean(String)}. */
    record BooleanType() implements CommandArgumentType {}

    /** Text: one word, a quotable string, or the rest of the line; read with {@link CommandContext#getString(String)}. */
    record TextType(TextKind kind) implements CommandArgumentType {}

    /** Block coordinates, absolute or relative ({@code ~ ~1 ~}); read with {@link CommandContext#getBlockPos(String)}. */
    record BlockPosType() implements CommandArgumentType {}

    /** One online player by name or selector; read with {@link CommandContext#getPlayer(String)}. */
    record PlayerType() implements CommandArgumentType {}

    /** How much text a {@link TextType} consumes. */
    enum TextKind {
        /** Letters, digits, and {@code _ - . +}. */
        WORD,
        /** A word, or text in double quotes. */
        QUOTABLE,
        /** Everything until the end of the command. */
        GREEDY
    }

    /** Any whole number. */
    static CommandArgumentType integer() {
        return new IntegerType(Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    /** A whole number in {@code [min, max]}. */
    static CommandArgumentType integer(int min, int max) {
        return new IntegerType(min, max);
    }

    /** Any decimal number. */
    static CommandArgumentType decimal() {
        return new DecimalType(-Double.MAX_VALUE, Double.MAX_VALUE);
    }

    /** {@code true} or {@code false}. */
    static CommandArgumentType bool() {
        return new BooleanType();
    }

    /** A single word. */
    static CommandArgumentType word() {
        return new TextType(TextKind.WORD);
    }

    /** A word or quoted text. */
    static CommandArgumentType string() {
        return new TextType(TextKind.QUOTABLE);
    }

    /** The rest of the command line. */
    static CommandArgumentType greedyString() {
        return new TextType(TextKind.GREEDY);
    }

    /** Block coordinates. */
    static CommandArgumentType blockPos() {
        return new BlockPosType();
    }

    /** One online player. */
    static CommandArgumentType player() {
        return new PlayerType();
    }
}
