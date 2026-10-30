package dev.plexusmodloader.api.resource;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;
import java.util.Map;

/** Receives a mod's own data or asset files each time the game (re)loads its packs. */
@ApiStability(StabilityLevel.PREVIEW)
@FunctionalInterface
public interface ResourceReloadListener {

    /**
     * The JSON files found, by ID: {@code data/<namespace>/<directory>/<path>.json} becomes
     * {@code <namespace>:<path>}; contents are UTF-8 text. Higher-priority packs replace lower ones.
     */
    void onReload(Map<ResourceId, String> files);
}
