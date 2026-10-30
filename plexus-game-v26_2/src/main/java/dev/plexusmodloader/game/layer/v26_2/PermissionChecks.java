package dev.plexusmodloader.game.layer.v26_2;

import dev.plexusmodloader.api.permission.PermissionNode;
import dev.plexusmodloader.api.permission.PermissionProvider;
import dev.plexusmodloader.api.permission.PermissionResult;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Decides named permissions: mod providers first, then the node's default operator level. */
public final class PermissionChecks {

    private static final Logger LOGGER = LoggerFactory.getLogger("Poly Mod Loader");

    private PermissionChecks() {}

    /** The game's permission for an operator level, 0–4. */
    public static Permission level(int level) {
        return switch (level) {
            case 1 -> Permissions.COMMANDS_MODERATOR;
            case 2 -> Permissions.COMMANDS_GAMEMASTER;
            case 3 -> Permissions.COMMANDS_ADMIN;
            default -> Permissions.COMMANDS_OWNER;
        };
    }

    private static boolean hasLevel(PermissionSet permissions, int level) {
        return level == 0 || permissions.hasPermission(level(level));
    }

    /** Whether {@code player} has {@code node}. */
    public static boolean check(Player player, PermissionNode node) {
        List<PermissionProvider> providers = LayerState.context()
                .map(context -> context.declarations().permissionProviders())
                .orElse(List.of());
        for (PermissionProvider provider : providers) {
            PermissionResult result;
            try {
                result = provider.check(Views.player(player), node);
            } catch (RuntimeException e) {
                LOGGER.error(
                        "[Poly Mod Loader] PLEXUS-PERMISSION-003 a permission provider failed for {}", node.id(), e);
                continue;
            }
            if (result == PermissionResult.ALLOW) {
                return true;
            }
            if (result == PermissionResult.DENY) {
                return false;
            }
        }
        return hasLevel(player.permissions(), node.defaultLevel());
    }

    /** Whether a command source has {@code node}: players through {@link #check}, others by level. */
    public static boolean check(CommandSourceStack source, PermissionNode node) {
        if (source.getEntity() instanceof Player player) {
            return check(player, node);
        }
        return hasLevel(source.permissions(), node.defaultLevel());
    }
}
