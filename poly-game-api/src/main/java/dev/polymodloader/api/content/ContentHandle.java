package dev.polymodloader.api.content;

import dev.polymodloader.api.resource.ResourceId;
import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;

/** A declared piece of content. The game creates it later, when its registries are built. */
@ApiStability(StabilityLevel.PREVIEW)
public interface ContentHandle {

    /** The content's ID, {@code <mod id>:<path>}. */
    ResourceId id();

    /** Whether the game has created and registered the content yet. */
    boolean isRegistered();
}
