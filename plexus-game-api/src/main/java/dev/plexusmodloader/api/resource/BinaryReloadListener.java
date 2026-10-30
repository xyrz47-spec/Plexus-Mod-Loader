package dev.plexusmodloader.api.resource;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;
import java.util.Map;

/** Like {@link ResourceReloadListener}, for files of any kind: their bytes as stored in the pack. */
@ApiStability(StabilityLevel.PREVIEW)
@FunctionalInterface
public interface BinaryReloadListener {

    /** Files by ID ({@code <namespace>:<path without the suffix>}); the arrays are the listener's own. */
    void onReload(Map<ResourceId, byte[]> files);
}
