package dev.plexusmodloader.api.command;

import dev.plexusmodloader.api.entity.PlayerView;
import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;
import dev.plexusmodloader.api.world.BlockPos;

/**
 * Parsed arguments and the source of a running command. Valid only during the action.
 * Reading an argument that is not on the executed path, or with the wrong type, throws
 * {@link IllegalArgumentException}.
 */
@ApiStability(StabilityLevel.PREVIEW)
public interface CommandContext {

    /** Who ran the command. */
    CommandSource source();

    /** Value of an {@link CommandArgumentType#integer()} argument. */
    int getInteger(String name);

    /** Value of a {@link CommandArgumentType#decimal()} argument. */
    double getDouble(String name);

    /** Value of a {@link CommandArgumentType#bool()} argument. */
    boolean getBoolean(String name);

    /** Value of a text argument. */
    String getString(String name);

    /**
     * Value of a {@link CommandArgumentType#blockPos()} argument, resolved against the source.
     *
     * @throws CommandFailure when the position is not loaded
     */
    BlockPos getBlockPos(String name) throws CommandFailure;

    /**
     * Value of a {@link CommandArgumentType#player()} argument.
     *
     * @throws CommandFailure when no single online player matches
     */
    PlayerView getPlayer(String name) throws CommandFailure;
}
