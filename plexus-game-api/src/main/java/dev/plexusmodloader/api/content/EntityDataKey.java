package dev.plexusmodloader.api.content;

import dev.plexusmodloader.api.resource.ResourceId;
import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;
import java.util.Objects;

/**
 * Mod data attached to entities (players included): a {@link dev.plexusmodloader.api.data.DataTree}
 * per entity, saved with it, kept by players when they respawn, and synchronised to clients as the
 * {@link EntityDataOptions} say. Read it with
 * {@link dev.plexusmodloader.api.entity.EntityView#data(EntityDataKey)}.
 *
 * @param id key ID
 * @param options synchronisation and version
 */
@ApiStability(StabilityLevel.PREVIEW)
public record EntityDataKey(ResourceId id, EntityDataOptions options) {

    public EntityDataKey {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(options, "options");
    }

    /** Whether a player's own client receives the player's data. */
    public boolean syncToOwner() {
        return options.syncToOwner() || options.syncToTrackers();
    }
}
