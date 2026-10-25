package dev.polymodloader.api.worldgen;

import dev.polymodloader.api.content.entity.MobCategory;
import dev.polymodloader.api.resource.ResourceId;
import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A biome defined in code. Poly Mod Loader writes it as biome data into its generated data pack, so it behaves
 * exactly like a biome from a data pack: dimensions, biome tags, and {@code /locate biome} see it.
 * Colours are {@code 0xRRGGBB}.
 *
 * @param temperature affects snow and grass colour (plains 0.8, snowy plains 0.0)
 * @param downfall affects grass and foliage colour (plains 0.4)
 * @param precipitation whether it rains (or snows when cold)
 * @param skyColor sky colour
 * @param fogColor fog colour, or the game's default
 * @param waterColor water colour (plains 0x3f76e4)
 * @param grassColor grass colour, or computed from temperature and downfall
 * @param foliageColor leaf colour, or computed
 * @param features placed features per generation step
 * @param spawns mobs that spawn naturally
 */
@ApiStability(StabilityLevel.PREVIEW)
public record BiomeDefinition(
        float temperature,
        float downfall,
        boolean precipitation,
        int skyColor,
        Optional<Integer> fogColor,
        int waterColor,
        Optional<Integer> grassColor,
        Optional<Integer> foliageColor,
        Map<GenerationStepName, List<ResourceId>> features,
        List<BiomeSpawn> spawns) {

    public BiomeDefinition {
        if (downfall < 0 || downfall > 1) {
            throw new IllegalArgumentException("downfall must be 0-1: " + downfall);
        }
        Objects.requireNonNull(fogColor, "fogColor");
        Objects.requireNonNull(grassColor, "grassColor");
        Objects.requireNonNull(foliageColor, "foliageColor");
        Map<GenerationStepName, List<ResourceId>> copy = new EnumMap<>(GenerationStepName.class);
        features.forEach((step, ids) -> copy.put(step, List.copyOf(ids)));
        features = Map.copyOf(copy);
        spawns = List.copyOf(spawns);
    }

    /** A builder with plains-like values and no features or spawns. */
    public static Builder builder() {
        return new Builder();
    }

    /** Builds a {@link BiomeDefinition}. */
    public static final class Builder {

        private float temperature = 0.8f;
        private float downfall = 0.4f;
        private boolean precipitation = true;
        private int skyColor = 0x78a7ff;
        private Optional<Integer> fogColor = Optional.empty();
        private int waterColor = 0x3f76e4;
        private Optional<Integer> grassColor = Optional.empty();
        private Optional<Integer> foliageColor = Optional.empty();
        private final Map<GenerationStepName, List<ResourceId>> features = new EnumMap<>(GenerationStepName.class);
        private final List<BiomeSpawn> spawns = new ArrayList<>();

        private Builder() {}

        /** Temperature (plains 0.8), downfall (plains 0.4) and whether it rains or snows. */
        public Builder climate(float temperature, float downfall, boolean precipitation) {
            this.temperature = temperature;
            this.downfall = downfall;
            this.precipitation = precipitation;
            return this;
        }

        /** Sky colour, {@code 0xRRGGBB}. */
        public Builder skyColor(int color) {
            this.skyColor = color;
            return this;
        }

        /** Fog colour, {@code 0xRRGGBB}. */
        public Builder fogColor(int color) {
            this.fogColor = Optional.of(color);
            return this;
        }

        /** Water colour, {@code 0xRRGGBB}. */
        public Builder waterColor(int color) {
            this.waterColor = color;
            return this;
        }

        /** Grass colour, {@code 0xRRGGBB}, instead of one computed from the climate. */
        public Builder grassColor(int color) {
            this.grassColor = Optional.of(color);
            return this;
        }

        /** Leaf colour, {@code 0xRRGGBB}, instead of one computed from the climate. */
        public Builder foliageColor(int color) {
            this.foliageColor = Optional.of(color);
            return this;
        }

        /** Adds a placed feature, such as {@code minecraft:trees_plains}, in {@code step}. */
        public Builder feature(GenerationStepName step, ResourceId placedFeature) {
            features.computeIfAbsent(step, ignored -> new ArrayList<>()).add(placedFeature);
            return this;
        }

        /** Adds a naturally spawning mob. */
        public Builder spawn(MobCategory category, ResourceId type, int weight, int minGroup, int maxGroup) {
            spawns.add(new BiomeSpawn(category, type, weight, minGroup, maxGroup));
            return this;
        }

        /** Creates the definition. */
        public BiomeDefinition build() {
            return new BiomeDefinition(
                    temperature,
                    downfall,
                    precipitation,
                    skyColor,
                    fogColor,
                    waterColor,
                    grassColor,
                    foliageColor,
                    features,
                    spawns);
        }
    }
}
