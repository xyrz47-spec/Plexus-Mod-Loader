package dev.polymodloader.api.tag;

import dev.polymodloader.api.resource.ResourceId;
import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;
import java.util.Objects;

/**
 * Names a tag of one registry, for example the block tag {@code #minecraft:mineable/pickaxe}. A key
 * is only a name: the tag's entries come from data packs and mod jars ({@code
 * data/<namespace>/tags/<registry>/<path>.json}) and from {@code ContentRegistrar.addToTag}, and
 * change on {@code /reload}. Query them through {@link TagView} ({@code WorldView.tags()}) or the
 * {@code isIn} methods of the views. Create keys once and keep them in constants.
 *
 * @param registry what the tag groups
 * @param id the tag's ID, without the {@code #}
 */
@ApiStability(StabilityLevel.PREVIEW)
public record TagKey(TagRegistry registry, ResourceId id) {

    public TagKey {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(id, "id");
    }

    /** A block tag, for example {@code TagKey.block(ResourceId.minecraft("mineable/pickaxe"))}. */
    public static TagKey block(ResourceId id) {
        return new TagKey(TagRegistry.BLOCK, id);
    }

    /** An item tag, for example {@code TagKey.item(ResourceId.minecraft("logs"))}. */
    public static TagKey item(ResourceId id) {
        return new TagKey(TagRegistry.ITEM, id);
    }

    /** An entity type tag, for example {@code TagKey.entityType(ResourceId.minecraft("skeletons"))}. */
    public static TagKey entityType(ResourceId id) {
        return new TagKey(TagRegistry.ENTITY_TYPE, id);
    }

    /** The tag as written in commands and data files, for example {@code #minecraft:logs}. */
    @Override
    public String toString() {
        return "#" + id;
    }
}
