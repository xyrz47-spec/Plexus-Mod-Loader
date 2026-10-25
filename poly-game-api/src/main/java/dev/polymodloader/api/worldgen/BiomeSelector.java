package dev.polymodloader.api.worldgen;

import dev.polymodloader.api.resource.ResourceId;
import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;
import java.util.Objects;
import java.util.Set;

/** Chooses biomes: by ID, or by biome tag such as {@code minecraft:is_overworld}. */
@ApiStability(StabilityLevel.PREVIEW)
public sealed interface BiomeSelector {

    /** Biomes with one of these IDs. */
    static BiomeSelector ids(ResourceId... ids) {
        return new ById(Set.of(ids));
    }

    /** Biomes in this tag. */
    static BiomeSelector tag(ResourceId tag) {
        return new ByTag(tag);
    }

    /** @param ids biome IDs */
    record ById(Set<ResourceId> ids) implements BiomeSelector {
        public ById {
            ids = Set.copyOf(ids);
        }
    }

    /** @param tag biome tag ID, without {@code #} */
    record ByTag(ResourceId tag) implements BiomeSelector {
        public ByTag {
            Objects.requireNonNull(tag, "tag");
        }
    }
}
