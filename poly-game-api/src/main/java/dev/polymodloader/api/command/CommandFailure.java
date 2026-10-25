package dev.polymodloader.api.command;

import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;
import java.io.Serial;

/** Thrown by a {@link CommandAction} to fail with a message shown to the command source in red. */
@ApiStability(StabilityLevel.PREVIEW)
public final class CommandFailure extends Exception {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Creates a failure with the message shown to the user. */
    public CommandFailure(String message) {
        super(message);
    }
}
