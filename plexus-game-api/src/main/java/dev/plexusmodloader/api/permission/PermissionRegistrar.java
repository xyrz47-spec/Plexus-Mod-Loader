package dev.plexusmodloader.api.permission;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/** Declares a mod's permissions and lets permission mods decide them. */
@ApiStability(StabilityLevel.PREVIEW)
public interface PermissionRegistrar {

    /**
     * Declares {@code <mod id>.<path>}.
     *
     * @param path dotted, {@code a-z 0-9 _ .}
     * @throws IllegalArgumentException for an invalid or used path ({@code PLEXUS-PERMISSION-001})
     */
    PermissionNode node(String path, int defaultLevel);

    /** Adds a provider asked for every permission check of every mod. */
    void provider(PermissionProvider provider);
}
