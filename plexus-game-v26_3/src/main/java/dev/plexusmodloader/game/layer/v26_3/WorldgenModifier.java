package dev.plexusmodloader.game.layer.v26_3;

import com.google.common.base.Suppliers;
import dev.plexusmodloader.api.worldgen.BiomeSelector;
import dev.plexusmodloader.game.layer.v26_3.entity.EntityInstaller;
import dev.plexusmodloader.game.version.layer.NativeDeclarations.FeatureDeclaration;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.util.random.WeightedList;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.attribute.EnvironmentAttributeMap;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.attribute.modifier.AttributeModifier;
import net.minecraft.world.attribute.modifier.MobSpawnSettingsModifier;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Attaches mod placed features to biomes when a server creates its levels, before any chunk
 * generator collects the features per step. The game keeps the feature lists in a private final
 * field of {@link BiomeGenerationSettings}; this replaces that list on the server's own biome
 * objects, which are created fresh for every server.
 */
final class WorldgenModifier {

    private static final Logger LOGGER = LoggerFactory.getLogger("Plexus Mod Loader");

    private WorldgenModifier() {}

    static void apply(RegistryAccess registries, List<FeatureDeclaration> features) {
        if (features.isEmpty()) {
            return;
        }
        Registry<Biome> biomes = registries.lookupOrThrow(Registries.BIOME);
        Registry<PlacedFeature> placed = registries.lookupOrThrow(Registries.PLACED_FEATURE);
        Field featuresField;
        try {
            featuresField = BiomeGenerationSettings.class.getDeclaredField("features");
            featuresField.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("PLEXUS-WORLDGEN-003 cannot reach biome feature lists", e);
        }
        int changedBiomes = 0;
        for (Holder.Reference<Biome> biome : biomes.listElements().toList()) {
            List<HolderSet<PlacedFeature>> steps = null;
            for (FeatureDeclaration feature : features) {
                if (!matches(biome, feature.biomes())) {
                    continue;
                }
                Holder<PlacedFeature> holder = placed.get(
                                ResourceKey.create(Registries.PLACED_FEATURE, Views.id(feature.placedFeature())))
                        .orElseThrow(() -> new IllegalStateException("PLEXUS-WORLDGEN-001 mod " + feature.ownerModId()
                                + " adds unknown placed feature " + feature.placedFeature()));
                if (steps == null) {
                    steps = new ArrayList<>(
                            biome.value().getGenerationSettings().features());
                }
                int step =
                        GenerationStep.Decoration.valueOf(feature.step().name()).ordinal();
                while (steps.size() <= step) {
                    steps.add(HolderSet.direct(List.of()));
                }
                List<Holder<PlacedFeature>> inStep =
                        new ArrayList<>(steps.get(step).stream().toList());
                if (!inStep.contains(holder)) {
                    inStep.add(holder);
                }
                steps.set(step, HolderSet.direct(inStep));
            }
            if (steps != null) {
                try {
                    featuresField.set(biome.value().getGenerationSettings(), List.copyOf(steps));
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException("PLEXUS-WORLDGEN-003 cannot change biome features", e);
                }
                changedBiomes++;
            }
        }
        LOGGER.info("[Plexus Mod Loader] Added {} feature(s) to {} biome(s)", features.size(), changedBiomes);
        if (changedBiomes > 0) {
            refreshFeatureIndexes(registries);
        }
    }

    /**
     * Rebuilds every dimension's per-step feature index from the changed biomes. A chunk generator
     * builds that index once, and the client's world creation and loading screens already did so
     * before the server starts; a feature missing from it crashes feature placement.
     */
    @SuppressWarnings("unchecked")
    private static void refreshFeatureIndexes(RegistryAccess registries) {
        Field indexField;
        Field settingsField;
        try {
            indexField = ChunkGenerator.class.getDeclaredField("featuresPerStep");
            indexField.setAccessible(true);
            settingsField = ChunkGenerator.class.getDeclaredField("generationSettingsGetter");
            settingsField.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("PLEXUS-WORLDGEN-003 cannot reach chunk generator feature indexes", e);
        }
        for (LevelStem stem : registries.lookupOrThrow(Registries.LEVEL_STEM)) {
            ChunkGenerator generator = stem.generator();
            try {
                Function<Holder<Biome>, BiomeGenerationSettings> settings =
                        (Function<Holder<Biome>, BiomeGenerationSettings>) settingsField.get(generator);
                Supplier<List<FeatureSorter.StepFeatureData>> index =
                        Suppliers.memoize(() -> FeatureSorter.buildFeaturesPerStep(
                                List.copyOf(generator.getBiomeSource().possibleBiomes()),
                                biome -> settings.apply(biome).features(),
                                true));
                indexField.set(generator, index);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(
                        "PLEXUS-WORLDGEN-003 cannot refresh a chunk generator's feature index", e);
            }
        }
    }

    /**
     * Adds naturally spawning mod mobs to the spawn lists of matching biomes. Since 26.3 a biome's spawns are the
     * argument of its {@link EnvironmentAttributes#NATURAL_MOB_SPAWNS} attribute entry, kept in a private final
     * field of {@link Biome}; this replaces that attribute map on the server's biomes with one whose entry has the
     * mod spawns added. The entry keeps its modifier; a biome without the entry gets one with the overlay modifier
     * the game's own biomes use, so it defines only the categories of the added spawns.
     */
    static void applySpawns(RegistryAccess registries, List<EntityInstaller.SpawnEntry> spawns) {
        if (spawns.isEmpty()) {
            return;
        }
        Field attributesField;
        try {
            attributesField = Biome.class.getDeclaredField("attributes");
            attributesField.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("PLEXUS-WORLDGEN-003 cannot reach biome spawn lists", e);
        }
        int changedBiomes = 0;
        for (Holder.Reference<Biome> biome :
                registries.lookupOrThrow(Registries.BIOME).listElements().toList()) {
            List<EntityInstaller.SpawnEntry> matching = spawns.stream()
                    .filter(spawn -> matches(biome, spawn.spawn().biomes()))
                    .toList();
            if (matching.isEmpty()) {
                continue;
            }
            EnvironmentAttributeMap attributes = biome.value().getAttributes();
            EnvironmentAttributeMap.Entry<MobSpawnSettings, ?> entry =
                    attributes.get(EnvironmentAttributes.NATURAL_MOB_SPAWNS);
            MobSpawnSettings settings;
            AttributeModifier<MobSpawnSettings, MobSpawnSettings> modifier;
            if (entry == null) {
                settings = MobSpawnSettings.EMPTY;
                modifier = MobSpawnSettingsModifier.overlay();
            } else if (entry.argument() instanceof MobSpawnSettings argument) {
                settings = argument;
                modifier = spawnSettingsModifier(entry);
            } else {
                throw new IllegalStateException(
                        "PLEXUS-WORLDGEN-003 biome " + biome.key().identifier()
                                + " has a natural mob spawns entry whose argument is not spawn settings: " + entry);
            }
            MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder();
            for (MobCategory category : settings.definedCategories()) {
                builder.addAllSpawns(category, settings.getMobsInCategory(category));
            }
            builder.addAllCosts(settings.allSpawnCosts());
            for (EntityInstaller.SpawnEntry spawn : matching) {
                int min = spawn.spawn().minGroup();
                int max = spawn.spawn().maxGroup();
                WeightedList<MobSpawnSettings.SpawnerData> added = WeightedList.<MobSpawnSettings.SpawnerData>builder()
                        .add(
                                new MobSpawnSettings.SpawnerData(
                                        spawn.type(), min == max ? ConstantInt.of(min) : UniformInt.of(min, max)),
                                spawn.spawn().weight())
                        .build();
                builder.addAllSpawns(spawn.category(), added);
            }
            EnvironmentAttributeMap changed = EnvironmentAttributeMap.builder()
                    .putAll(attributes)
                    .modify(EnvironmentAttributes.NATURAL_MOB_SPAWNS, modifier, builder.build())
                    .build();
            try {
                attributesField.set(biome.value(), changed);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("PLEXUS-WORLDGEN-003 cannot change biome spawn lists", e);
            }
            changedBiomes++;
        }
        LOGGER.info("[Plexus Mod Loader] Added {} natural spawn(s) to {} biome(s)", spawns.size(), changedBiomes);
    }

    /**
     * The entry's modifier, whose argument the caller checked is spawn settings: the game's modifiers for this
     * attribute (override and overlay) all take the settings they apply.
     */
    @SuppressWarnings("unchecked") // the argument was checked to be MobSpawnSettings
    private static AttributeModifier<MobSpawnSettings, MobSpawnSettings> spawnSettingsModifier(
            EnvironmentAttributeMap.Entry<MobSpawnSettings, ?> entry) {
        return (AttributeModifier<MobSpawnSettings, MobSpawnSettings>) entry.modifier();
    }

    private static boolean matches(Holder.Reference<Biome> biome, BiomeSelector selector) {
        return switch (selector) {
            case BiomeSelector.ById byId ->
                byId.ids().contains(Views.id(biome.key().identifier()));
            case BiomeSelector.ByTag byTag -> biome.is(TagKey.create(Registries.BIOME, Views.id(byTag.tag())));
        };
    }
}
