package dev.plexusmodloader.api.content;

import dev.plexusmodloader.api.resource.ResourceId;
import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/** A declared piece of content. The game creates it later, when its registries are built. */
@ApiStability(StabilityLevel.PREVIEW)
public interface ContentHandle {

    /** The content's ID, {@code <mod id>:<path>}. */
    ResourceId id();

    /** Whether the game has created and registered the content yet. */
    boolean isRegistered();
}
