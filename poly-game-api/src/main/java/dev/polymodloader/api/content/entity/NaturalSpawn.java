package dev.polymodloader.api.content.entity;

import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;
import dev.polymodloader.api.worldgen.BiomeSelector;
import java.util.Objects;

/**
 * Where and how often a mob spawns by itself. Creatures spawn on {@code #minecraft:animals_spawnable_on}
 * blocks in daylight, monsters in the dark, water creatures in water, like the game's mobs of the
 * same category.
 *
 * @param biomes where
 * @param weight chance relative to the biome's other mobs of the category (pig 10, zombie 100)
 * @param minGroup smallest group
 * @param maxGroup largest group
 */
@ApiStability(StabilityLevel.PREVIEW)
public record NaturalSpawn(BiomeSelector biomes, int weight, int minGroup, int maxGroup) {

    public NaturalSpawn {
        Objects.requireNonNull(biomes, "biomes");
        if (weight < 1 || minGroup < 1 || maxGroup < minGroup) {
            throw new IllegalArgumentException("Need weight >= 1 and 1 <= minGroup <= maxGroup");
        }
    }
}
