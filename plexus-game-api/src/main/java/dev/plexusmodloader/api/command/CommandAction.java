package dev.plexusmodloader.api.command;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/** What a command does. Runs on the server thread. */
@ApiStability(StabilityLevel.PREVIEW)
@FunctionalInterface
public interface CommandAction {

    /**
     * Runs the command.
     *
     * @return the command's result value (used by {@code /execute store}); 1 conventionally means success
     * @throws CommandFailure to show an error message to the source and report failure
     */
    int run(CommandContext context) throws CommandFailure;
}
