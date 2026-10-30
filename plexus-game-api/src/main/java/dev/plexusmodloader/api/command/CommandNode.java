package dev.plexusmodloader.api.command;

import dev.plexusmodloader.api.permission.PermissionNode;
import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One word of a command: a fixed {@link LiteralCommand} or a typed {@link ArgumentCommand}. Nodes form
 * a tree; a node with an action can end the command.
 *
 * @param <N> the concrete node type, returned by the fluent methods
 */
@ApiStability(StabilityLevel.PREVIEW)
public abstract sealed class CommandNode<N extends CommandNode<N>> permits LiteralCommand, ArgumentCommand {

    private final String name;
    private final List<CommandNode<?>> children = new ArrayList<>();
    private CommandAction action;
    private int permissionLevel;
    private PermissionNode permissionNode;

    CommandNode(String name) {
        this.name = Objects.requireNonNull(name, "name");
        if (name.isEmpty() || name.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("Command node names must be non-empty without spaces: '" + name + "'");
        }
    }

    /** A fixed word, for example {@code /hello}. */
    public static LiteralCommand literal(String name) {
        return new LiteralCommand(name);
    }

    /** A typed value; read it in actions with {@code context.get*(name)}. */
    public static ArgumentCommand argument(String name, CommandArgumentType type) {
        return new ArgumentCommand(name, type);
    }

    /** Adds a child that may follow this node. */
    public N then(CommandNode<?> child) {
        children.add(Objects.requireNonNull(child, "child"));
        return self();
    }

    /** Lets the command end at this node and run {@code action}. */
    public N executes(CommandAction action) {
        this.action = Objects.requireNonNull(action, "action");
        return self();
    }

    /**
     * Only sources with at least this permission level can see and run this node: 0 everyone, 2
     * game masters (command blocks, operators), 4 server owners.
     */
    public N requiresPermission(int level) {
        if (level < 0 || level > 4) {
            throw new IllegalArgumentException("Permission level must be 0-4: " + level);
        }
        this.permissionLevel = level;
        return self();
    }

    /** Only sources that have {@code node} can see and run this node. */
    public N requiresPermission(PermissionNode node) {
        this.permissionNode = Objects.requireNonNull(node, "node");
        return self();
    }

    /** The required named permission, if any (checked in addition to the level). */
    public Optional<PermissionNode> permissionNode() {
        return Optional.ofNullable(permissionNode);
    }

    /** The word or argument name. */
    public String name() {
        return name;
    }

    /** Child nodes in registration order. */
    public List<CommandNode<?>> children() {
        return List.copyOf(children);
    }

    /** The action run when the command ends here. */
    public Optional<CommandAction> action() {
        return Optional.ofNullable(action);
    }

    /** Required permission level, 0–4. */
    public int permissionLevel() {
        return permissionLevel;
    }

    abstract N self();
}
