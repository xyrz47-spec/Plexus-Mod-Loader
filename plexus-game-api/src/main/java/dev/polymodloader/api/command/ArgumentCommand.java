package dev.polymodloader.api.command;

import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;
import java.util.Objects;

/** A typed value in a command. Create with {@link CommandNode#argument(String, CommandArgumentType)}. */
@ApiStability(StabilityLevel.PREVIEW)
public final class ArgumentCommand extends CommandNode<ArgumentCommand> {

    private final CommandArgumentType type;

    ArgumentCommand(String name, CommandArgumentType type) {
        super(name);
        this.type = Objects.requireNonNull(type, "type");
    }

    /** What the argument accepts. */
    public CommandArgumentType type() {
        return type;
    }

    @Override
    ArgumentCommand self() {
        return this;
    }
}
