package dev.polymodloader.api.permission;

import dev.polymodloader.api.entity.PlayerView;
import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;

/**
 * Decides permissions, for example from a permissions file or groups. Providers are asked in
 * registration order; the first answer other than {@link PermissionResult#UNDECIDED} counts. Called
 * on the server thread, often (command suggestions); keep it fast.
 */
@ApiStability(StabilityLevel.PREVIEW)
@FunctionalInterface
public interface PermissionProvider {

    /**
     * Whether {@code player} has {@code node}, or {@link PermissionResult#UNDECIDED} to let the next provider or the
     * node's default decide.
     */
    PermissionResult check(PlayerView player, PermissionNode node);
}
