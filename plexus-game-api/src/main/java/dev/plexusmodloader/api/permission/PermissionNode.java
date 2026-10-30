package dev.plexusmodloader.api.permission;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;
import java.util.Objects;

/**
 * A named permission, {@code <mod id>.<path>}. Permission providers (a permissions mod) decide it per
 * player; without a decision, players need the default operator level.
 *
 * @param id dotted name, for example {@code my_mod.teleport.others}
 * @param defaultLevel operator level needed when no provider decides: 0 everyone, 2 game masters,
 *     4 server owners
 */
@ApiStability(StabilityLevel.PREVIEW)
public record PermissionNode(String id, int defaultLevel) {

    public PermissionNode {
        Objects.requireNonNull(id, "id");
        if (defaultLevel < 0 || defaultLevel > 4) {
            throw new IllegalArgumentException("Permission level must be 0-4: " + defaultLevel);
        }
    }
}
