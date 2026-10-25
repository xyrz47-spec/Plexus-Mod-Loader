package dev.polymodloader.api.worldgen;

import dev.polymodloader.api.content.entity.MobCategory;
import dev.polymodloader.api.resource.ResourceId;
import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;
import java.util.Objects;

/**
 * A mob that spawns in a code-defined biome.
 *
 * @param category spawn group
 * @param type entity type, for example {@code minecraft:sheep}
 * @param weight chance relative to other mobs of the category
 * @param minGroup smallest group
 * @param maxGroup largest group
 */
@ApiStability(StabilityLevel.PREVIEW)
public record BiomeSpawn(MobCategory category, ResourceId type, int weight, int minGroup, int maxGroup) {

    public BiomeSpawn {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(type, "type");
        if (weight < 1 || minGroup < 1 || maxGroup < minGroup) {
            throw new IllegalArgumentException("Need weight >= 1 and 1 <= minGroup <= maxGroup");
        }
    }
}
