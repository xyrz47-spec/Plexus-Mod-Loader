package dev.polymodloader.api.command;

import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;

/** A fixed word in a command. Create with {@link CommandNode#literal(String)}. */
@ApiStability(StabilityLevel.PREVIEW)
public final class LiteralCommand extends CommandNode<LiteralCommand> {

    LiteralCommand(String name) {
        super(name);
    }

    @Override
    LiteralCommand self() {
        return this;
    }
}
