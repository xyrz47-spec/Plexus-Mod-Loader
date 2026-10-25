package dev.polymodloader.api.permission;

import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;

/** What a {@link PermissionProvider} decided. */
@ApiStability(StabilityLevel.PREVIEW)
public enum PermissionResult {
    ALLOW,
    DENY,
    /** No opinion; the next provider, or the node's default level, decides. */
    UNDECIDED
}
