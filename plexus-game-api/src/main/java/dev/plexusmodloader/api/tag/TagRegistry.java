package dev.plexusmodloader.api.tag;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/**
 * The registries whose entries tags can group. A tag's file lives in {@code
 * data/<namespace>/tags/<directory>/<path>.json} of a data pack or mod jar.
 */
@ApiStability(StabilityLevel.PREVIEW)
public enum TagRegistry {
    /** Blocks, such as {@code #minecraft:mineable/pickaxe}. */
    BLOCK("block"),
    /** Items, such as {@code #minecraft:logs}. */
    ITEM("item"),
    /** Entity types, such as {@code #minecraft:skeletons}. */
    ENTITY_TYPE("entity_type");

    private final String directory;

    TagRegistry(String directory) {
        this.directory = directory;
    }

    /** The folder under {@code tags/} that holds this registry's tag files, for example {@code block}. */
    public String directory() {
        return directory;
    }
}
