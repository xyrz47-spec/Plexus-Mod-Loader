package dev.plexusmodloader.game.layer.v1_21_11.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.plexusmodloader.api.resource.ResourceId;
import dev.plexusmodloader.api.worldgen.ColumnBuilder;
import dev.plexusmodloader.api.worldgen.TerrainGenerator;
import dev.plexusmodloader.game.layer.v1_21_11.Views;
import dev.plexusmodloader.game.version.layer.NativeDeclarations.ChunkGeneratorDeclaration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A chunk generator whose terrain comes from a mod's {@link TerrainGenerator}. Each registration
 * gets its own codec type, {@code <mod id>:<path>}, with a {@code biome_source} field.
 */
public final class PlexusChunkGenerator extends ChunkGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger("Poly Mod Loader");
    private static final Map<ResourceId, MapCodec<PlexusChunkGenerator>> CODECS = new ConcurrentHashMap<>();
    private static final Map<ResourceId, BlockState> STATES = new ConcurrentHashMap<>();

    private final ResourceId id;
    private final TerrainGenerator terrain;

    private PlexusChunkGenerator(BiomeSource biomeSource, ResourceId id, TerrainGenerator terrain) {
        super(biomeSource);
        this.id = id;
        this.terrain = terrain;
    }

    /** Registers one codec type per declared generator; called before the registries freeze. */
    public static void install(List<ChunkGeneratorDeclaration> declarations) {
        for (ChunkGeneratorDeclaration declaration : declarations) {
            ResourceId id = declaration.id();
            TerrainGenerator terrain = declaration.generator();
            MapCodec<PlexusChunkGenerator> codec = RecordCodecBuilder.mapCodec(instance -> instance.group(
                            BiomeSource.CODEC.fieldOf("biome_source").forGetter(generator -> generator.biomeSource))
                    .apply(instance, instance.stable(source -> new PlexusChunkGenerator(source, id, terrain))));
            CODECS.put(id, codec);
            Registry.register(BuiltInRegistries.CHUNK_GENERATOR, Views.id(id), codec);
        }
        if (!declarations.isEmpty()) {
            LOGGER.info("[Poly Mod Loader] Registered {} chunk generator type(s) from mods", declarations.size());
        }
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODECS.get(id);
    }

    private static BlockState state(ResourceId block) {
        return STATES.computeIfAbsent(block, key -> Views.block(key).defaultBlockState());
    }

    private long seed(RandomState randomState) {
        return randomState.getOrCreateRandomFactory(Views.id(id)).at(0, 0, 0).nextLong();
    }

    /** Runs the mod's terrain for one column into an array indexed by {@code y - minY}. */
    private BlockState[] column(int x, int z, int minY, int height, long seed) {
        BlockState[] states = new BlockState[height];
        Arrays.fill(states, Blocks.AIR.defaultBlockState());
        terrain.fillColumn(x, z, new Column(minY, height, seed) {
            @Override
            void write(int y, BlockState state) {
                states[y - minY] = state;
            }
        });
        return states;
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(
            Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess chunk) {
        long seed = seed(randomState);
        ChunkPos pos = chunk.getPos();
        Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap worldSurface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int localX = x;
                int localZ = z;
                terrain.fillColumn(
                        pos.getMinBlockX() + x,
                        pos.getMinBlockZ() + z,
                        new Column(chunk.getMinY(), chunk.getHeight(), seed) {
                            @Override
                            void write(int y, BlockState state) {
                                chunk.setBlockState(mutable.set(localX, y, localZ), state);
                                oceanFloor.update(localX, y, localZ, state);
                                worldSurface.update(localX, y, localZ, state);
                            }
                        });
            }
        }
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public int getBaseHeight(
            int x, int z, Heightmap.Types type, LevelHeightAccessor heightAccessor, RandomState randomState) {
        BlockState[] states = column(x, z, heightAccessor.getMinY(), heightAccessor.getHeight(), seed(randomState));
        for (int index = states.length - 1; index >= 0; index--) {
            if (type.isOpaque().test(states[index])) {
                return heightAccessor.getMinY() + index + 1;
            }
        }
        return heightAccessor.getMinY();
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor heightAccessor, RandomState randomState) {
        return new NoiseColumn(
                heightAccessor.getMinY(),
                column(x, z, heightAccessor.getMinY(), heightAccessor.getHeight(), seed(randomState)));
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
        ChunkPos center = region.getCenter();
        Holder<Biome> biome = region.getBiome(center.getWorldPosition().atY(region.getMaxY()));
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(RandomSupport.generateUniqueSeed()));
        random.setDecorationSeed(region.getSeed(), center.getMinBlockX(), center.getMinBlockZ());
        NaturalSpawner.spawnMobsForChunkGeneration(region, biome, center, random);
    }

    @Override
    public void applyCarvers(
            WorldGenRegion region,
            long seed,
            RandomState randomState,
            BiomeManager biomeManager,
            StructureManager structureManager,
            ChunkAccess chunk) {}

    @Override
    public void buildSurface(
            WorldGenRegion region, StructureManager structureManager, RandomState randomState, ChunkAccess chunk) {}

    @Override
    public void addDebugScreenInfo(List<String> result, RandomState randomState, BlockPos feetPos) {
        result.add("Poly Mod Loader terrain: " + id);
    }

    @Override
    public int getGenDepth() {
        return 384;
    }

    @Override
    public int getSeaLevel() {
        return terrain.seaLevel();
    }

    @Override
    public int getMinY() {
        return -64;
    }

    /** The column handed to the mod; subclasses decide where blocks go. */
    private abstract static class Column implements ColumnBuilder {

        private final int minY;
        private final int height;
        private final long seed;

        Column(int minY, int height, long seed) {
            this.minY = minY;
            this.height = height;
            this.seed = seed;
        }

        abstract void write(int y, BlockState state);

        @Override
        public int minY() {
            return minY;
        }

        @Override
        public int maxY() {
            return minY + height - 1;
        }

        @Override
        public long seed() {
            return seed;
        }

        @Override
        public void set(int y, ResourceId block) {
            BlockState state = state(block);
            if (y >= minY && y <= maxY()) {
                write(y, state);
            }
        }
    }
}
