package dev.polymodloader.game.layer.v26_3.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.polymodloader.api.command.ArgumentCommand;
import dev.polymodloader.api.command.CommandAction;
import dev.polymodloader.api.command.CommandArgumentType;
import dev.polymodloader.api.command.CommandFailure;
import dev.polymodloader.api.command.CommandNode;
import dev.polymodloader.api.command.LiteralCommand;
import dev.polymodloader.api.permission.PermissionNode;
import dev.polymodloader.game.layer.v26_3.PermissionChecks;
import dev.polymodloader.game.version.layer.NativeDeclarations.CommandDeclaration;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permission;

/** Translates Native API command trees into the game's Brigadier commands. */
public final class CommandInstaller {

    private CommandInstaller() {}

    /** Adds every registered command to a freshly built command tree. */
    public static void install(Commands commands, List<CommandDeclaration> declarations) {
        CommandDispatcher<CommandSourceStack> dispatcher = commands.getDispatcher();
        for (CommandDeclaration declaration : declarations) {
            dispatcher.register(literal(declaration.command()));
        }
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> literal(
            LiteralCommand command) {
        var builder = Commands.literal(command.name());
        configure(builder, command);
        return builder;
    }

    private static <B extends ArgumentBuilder<CommandSourceStack, B>> void configure(B builder, CommandNode<?> node) {
        int level = node.permissionLevel();
        PermissionNode named = node.permissionNode().orElse(null);
        if (level > 0 || named != null) {
            // Brigadier keeps one requirement per node, so the level and the named permission are combined.
            Permission permission = level > 0 ? PermissionChecks.level(level) : null;
            builder.requires(
                    source -> (permission == null || source.permissions().hasPermission(permission))
                            && (named == null || PermissionChecks.check(source, named)));
        }
        node.action().ifPresent(action -> builder.executes(context -> run(action, context)));
        for (CommandNode<?> child : node.children()) {
            switch (child) {
                case LiteralCommand literal -> builder.then(literal(literal));
                case ArgumentCommand argument -> {
                    var argumentBuilder = Commands.argument(argument.name(), argumentType(argument.type()));
                    configure(argumentBuilder, argument);
                    builder.then(argumentBuilder);
                }
            }
        }
    }

    private static int run(
            CommandAction action, com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        try {
            return action.run(new CommandContextView(context));
        } catch (CommandFailure failure) {
            throw new SimpleCommandExceptionType(Component.literal(failure.getMessage())).create();
        }
    }

    private static ArgumentType<?> argumentType(CommandArgumentType type) {
        return switch (type) {
            case CommandArgumentType.IntegerType integer -> IntegerArgumentType.integer(integer.min(), integer.max());
            case CommandArgumentType.DecimalType decimal -> DoubleArgumentType.doubleArg(decimal.min(), decimal.max());
            case CommandArgumentType.BooleanType ignored -> BoolArgumentType.bool();
            case CommandArgumentType.TextType text ->
                switch (text.kind()) {
                    case WORD -> StringArgumentType.word();
                    case QUOTABLE -> StringArgumentType.string();
                    case GREEDY -> StringArgumentType.greedyString();
                };
            case CommandArgumentType.BlockPosType ignored -> BlockPosArgument.blockPos();
            case CommandArgumentType.PlayerType ignored -> EntityArgument.player();
        };
    }
}
