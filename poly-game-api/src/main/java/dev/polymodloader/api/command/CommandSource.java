package dev.polymodloader.api.command;

import dev.polymodloader.api.entity.PlayerView;
import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;
import dev.polymodloader.api.world.BlockPos;
import dev.polymodloader.api.world.WorldView;
import java.util.Optional;

/** Who or what ran a command: a player, the console, a command block, or a function. */
@ApiStability(StabilityLevel.PREVIEW)
public interface CommandSource {

    /** Display name, for example a player name or {@code Server}. */
    String name();

    /** Sends feedback text to the source. */
    void sendMessage(String text);

    /** Sends an error message (shown in red to players). */
    void sendFailure(String text);

    /** Whether the source has at least permission {@code level} (0–4). */
    boolean hasPermission(int level);

    /**
     * Whether the source has the permission: for players as {@code PlayerView.hasPermission}; for the
     * console, command blocks, and functions by the node's default level.
     */
    boolean hasPermission(dev.polymodloader.api.permission.PermissionNode node);

    /** The player who ran the command, if a player did. */
    Optional<PlayerView> player();

    /** The world the command runs in. */
    WorldView world();

    /** The block position the command runs at. */
    BlockPos blockPos();
}
