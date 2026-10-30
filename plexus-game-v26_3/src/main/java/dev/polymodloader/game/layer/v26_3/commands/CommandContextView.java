package dev.polymodloader.game.layer.v26_3.commands;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.polymodloader.api.command.CommandContext;
import dev.polymodloader.api.command.CommandFailure;
import dev.polymodloader.api.command.CommandSource;
import dev.polymodloader.api.entity.PlayerView;
import dev.polymodloader.api.world.BlockPos;
import dev.polymodloader.api.world.WorldView;
import dev.polymodloader.game.layer.v26_3.Views;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.Permissions;

/** {@link CommandContext} over a Brigadier context. */
final class CommandContextView implements CommandContext, CommandSource {

    private final com.mojang.brigadier.context.CommandContext<CommandSourceStack> context;
    private final CommandSourceStack source;

    CommandContextView(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        this.context = context;
        this.source = context.getSource();
    }

    @Override
    public CommandSource source() {
        return this;
    }

    @Override
    public int getInteger(String name) {
        return IntegerArgumentType.getInteger(context, name);
    }

    @Override
    public double getDouble(String name) {
        return DoubleArgumentType.getDouble(context, name);
    }

    @Override
    public boolean getBoolean(String name) {
        return BoolArgumentType.getBool(context, name);
    }

    @Override
    public String getString(String name) {
        return StringArgumentType.getString(context, name);
    }

    @Override
    public BlockPos getBlockPos(String name) throws CommandFailure {
        try {
            return Views.pos(BlockPosArgument.getLoadedBlockPos(context, name));
        } catch (CommandSyntaxException e) {
            throw new CommandFailure(e.getMessage());
        }
    }

    @Override
    public PlayerView getPlayer(String name) throws CommandFailure {
        try {
            return Views.player(EntityArgument.getPlayer(context, name));
        } catch (CommandSyntaxException e) {
            throw new CommandFailure(e.getMessage());
        }
    }

    @Override
    public String name() {
        return source.getTextName();
    }

    @Override
    public void sendMessage(String text) {
        source.sendSuccess(() -> Component.literal(text), false);
    }

    @Override
    public void sendFailure(String text) {
        source.sendFailure(Component.literal(text));
    }

    @Override
    public boolean hasPermission(int level) {
        if (level <= 0) {
            return true;
        }
        Permission permission = switch (level) {
            case 1 -> Permissions.COMMANDS_MODERATOR;
            case 2 -> Permissions.COMMANDS_GAMEMASTER;
            case 3 -> Permissions.COMMANDS_ADMIN;
            default -> Permissions.COMMANDS_OWNER;
        };
        return source.permissions().hasPermission(permission);
    }

    @Override
    public boolean hasPermission(dev.polymodloader.api.permission.PermissionNode node) {
        return dev.polymodloader.game.layer.v26_3.PermissionChecks.check(source, node);
    }

    @Override
    public Optional<PlayerView> player() {
        return Optional.ofNullable(source.getPlayer()).map(Views::player);
    }

    @Override
    public WorldView world() {
        return Views.world(source.getLevel());
    }

    @Override
    public BlockPos blockPos() {
        return Views.pos(net.minecraft.core.BlockPos.containing(source.getPosition()));
    }
}
