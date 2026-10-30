package dev.plexusmodloader.api.tag;

import dev.plexusmodloader.api.resource.ResourceId;
import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;
import java.util.List;

/**
 * Reads the tags the game loaded: on the server from its data packs, on the client the copy the
 * server sent. Obtained from {@code WorldView.tags()}.
 *
 * <p><b>Thread:</b> the thread of the world that handed it out. <b>When:</b> once a world is loaded;
 * results change after {@code /reload}, so do not cache them across reloads.
 */
@ApiStability(StabilityLevel.PREVIEW)
public interface TagView {

    /**
     * Whether {@code entry} (a block, item, or entity type ID, matching the tag's registry) is in the
     * tag, including through nested tags. An unknown tag or entry is simply not contained.
     */
    boolean contains(TagKey tag, ResourceId entry);

    /** Every entry of the tag, nested tags resolved, in the game's order; empty for an unknown tag. */
    List<ResourceId> entries(TagKey tag);
}
