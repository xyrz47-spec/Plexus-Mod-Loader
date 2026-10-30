package dev.plexusmodloader.game.layer.v26_3.worldgen;

import com.mojang.serialization.MapCodec;
import dev.plexusmodloader.api.resource.ResourceId;
import dev.plexusmodloader.api.worldgen.CodeFeature;
import dev.plexusmodloader.api.worldgen.FeatureContext;
import dev.plexusmodloader.api.worldgen.NoiseFunction;
import dev.plexusmodloader.api.worldgen.StructureDefinition;
import dev.plexusmodloader.api.worldgen.StructurePieceContext;
import dev.plexusmodloader.game.layer.v26_3.Views;
import dev.plexusmodloader.game.version.layer.NativeDeclarations.CodeFeatureDeclaration;
import dev.plexusmodloader.game.version.layer.NativeDeclarations.DensityFunctionDeclaration;
import dev.plexusmodloader.game.version.layer.NativeDeclarations.StructureDeclaration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.random.RandomGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Interval;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * World generation written in code: feature types, density function types, and structure types
 * with one shared piece type. All are registered before the registries freeze; the generated data
 * pack adds the configured and placed features, structures and structure sets that use them.
 */
public final class CodeWorldgen {

    private static final Logger LOGGER = LoggerFactory.getLogger("Poly Mod Loader");

    /** The piece type of every code structure; a piece names its structure in its saved data. */
    private static final Identifier PIECE_ID = Identifier.fromNamespaceAndPath("poly", "code_structure_piece");

    private static final Map<ResourceId, StructureDefinition> STRUCTURES = new ConcurrentHashMap<>();

    /** IDs whose code already failed once, so world generation logs each failure once. */
    private static final Set<ResourceId> FAILED = ConcurrentHashMap.newKeySet();

    private static StructurePieceType pieceType;

    private CodeWorldgen() {}

    /** Registers the declared types; called before the registries freeze. */
    public static void install(
            List<CodeFeatureDeclaration> features,
            List<DensityFunctionDeclaration> densityFunctions,
            List<StructureDeclaration> structures) {
        for (CodeFeatureDeclaration declaration : features) {
            CodeFeatureType feature = new CodeFeatureType(declaration.id(), declaration.feature());
            Registry.register(BuiltInRegistries.FEATURE_TYPE, Views.id(declaration.id()), feature.codec);
        }
        for (DensityFunctionDeclaration declaration : densityFunctions) {
            CodeDensity density = new CodeDensity(
                    declaration.id(), declaration.function(), declaration.minValue(), declaration.maxValue());
            Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, Views.id(declaration.id()), density.codec);
        }
        if (!structures.isEmpty()) {
            pieceType = (StructurePieceType.ContextlessType) CodePiece::new;
            Registry.register(BuiltInRegistries.STRUCTURE_PIECE, PIECE_ID, pieceType);
        }
        for (StructureDeclaration declaration : structures) {
            STRUCTURES.put(declaration.id(), declaration.definition());
            ResourceId id = declaration.id();
            MapCodec<CodeStructure> codec = Structure.simpleCodec(settings -> new CodeStructure(settings, id));
            StructureType<CodeStructure> type = () -> codec;
            CodeStructure.TYPES.put(id, type);
            Registry.register(BuiltInRegistries.STRUCTURE_TYPE, Views.id(id), type);
        }
        int total = features.size() + densityFunctions.size() + structures.size();
        if (total > 0) {
            LOGGER.info(
                    "[Poly Mod Loader] Registered {} code feature(s), {} density function(s) and {} structure(s) from mods",
                    features.size(),
                    densityFunctions.size(),
                    structures.size());
        }
    }

    private static void reportFailure(ResourceId id, String what, RuntimeException e) {
        if (FAILED.add(id)) {
            LOGGER.error(
                    "[Poly Mod Loader] PLEXUS-WORLDGEN-005 {} {} threw; it is skipped where it fails", what, id, e);
        }
    }

    /** The game's random source as the API's random generator. */
    private static RandomGenerator generator(RandomSource random) {
        return new RandomGenerator() {
            @Override
            public long nextLong() {
                return random.nextLong();
            }

            @Override
            public int nextInt(int bound) {
                return random.nextInt(bound);
            }

            @Override
            public double nextDouble() {
                return random.nextDouble();
            }

            @Override
            public float nextFloat() {
                return random.nextFloat();
            }

            @Override
            public boolean nextBoolean() {
                return random.nextBoolean();
            }
        };
    }

    private static net.minecraft.world.level.block.state.BlockState state(ResourceId block) {
        return Views.block(block).defaultBlockState();
    }

    /**
     * A feature type running a mod's {@link CodeFeature}. Since 26.3 a feature is its own configuration; this one
     * has none, so its type's codec always gives this instance, as the game's {@code NoOpFeature} does.
     */
    private static final class CodeFeatureType implements Feature {

        private final ResourceId id;
        private final CodeFeature feature;
        private final MapCodec<CodeFeatureType> codec;

        CodeFeatureType(ResourceId id, CodeFeature feature) {
            this.id = id;
            this.feature = feature;
            this.codec = MapCodec.unit(this);
        }

        @Override
        public MapCodec<CodeFeatureType> codec() {
            return codec;
        }

        @Override
        public boolean place(
                WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource randomSource, BlockPos origin) {
            RandomGenerator random = generator(randomSource);
            FeatureContext view = new FeatureContext() {
                @Override
                public dev.plexusmodloader.api.world.BlockPos origin() {
                    return Views.pos(origin);
                }

                @Override
                public RandomGenerator random() {
                    return random;
                }

                @Override
                public ResourceId blockAt(dev.plexusmodloader.api.world.BlockPos pos) {
                    return Views.blockId(level.getBlockState(Views.pos(pos)).getBlock());
                }

                @Override
                public boolean isAir(dev.plexusmodloader.api.world.BlockPos pos) {
                    return level.getBlockState(Views.pos(pos)).isAir();
                }

                @Override
                public void setBlock(dev.plexusmodloader.api.world.BlockPos pos, ResourceId block) {
                    level.setBlock(Views.pos(pos), state(block), 3);
                }

                @Override
                public int surfaceY(int x, int z) {
                    return level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
                }

                @Override
                public int minY() {
                    return level.getMinY();
                }

                @Override
                public int maxY() {
                    return level.getMaxY();
                }
            };
            try {
                return feature.place(view);
            } catch (RuntimeException e) {
                reportFailure(id, "Code feature", e);
                return false;
            }
        }
    }

    /**
     * A density function type running a mod's {@link NoiseFunction}. It has no children and depends on all three
     * axes, so its sampler fills a volume one block at a time.
     */
    private static final class CodeDensity implements DensityFunction, DensitySampler {

        private final ResourceId id;
        private final NoiseFunction function;
        private final double minValue;
        private final double maxValue;
        private final MapCodec<DensityFunction> codec;

        CodeDensity(ResourceId id, NoiseFunction function, double minValue, double maxValue) {
            this.id = id;
            this.function = function;
            this.minValue = minValue;
            this.maxValue = maxValue;
            this.codec = MapCodec.unit(this);
        }

        @Override
        public DensitySampler compileSampler(DensityFunction.CompileContext context) {
            return this;
        }

        @Override
        public float sampleValue(SamplerContext context, int x, int y, int z) {
            try {
                return (float) function.compute(x, y, z);
            } catch (RuntimeException e) {
                reportFailure(id, "Density function", e);
                return 0;
            }
        }

        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer buffer, DensityVolume volume) {
            DensitySampler.sampleVolumeNaive(context, buffer, volume, this);
        }

        @Override
        public DensityFunction rewriteChildren(DfRewriteRule rule) {
            return this;
        }

        @Override
        public Interval range() {
            return Interval.of((float) minValue, (float) maxValue);
        }

        @Override
        public int domainAxes() {
            return DensityFunction.ALL_AXES;
        }

        @Override
        public MapCodec<DensityFunction> codec() {
            return codec;
        }
    }

    /** A structure placing one {@link CodePiece} on the surface at the centre of its chunk. */
    private static final class CodeStructure extends Structure {

        static final Map<ResourceId, StructureType<CodeStructure>> TYPES = new ConcurrentHashMap<>();

        private final ResourceId id;

        CodeStructure(Structure.StructureSettings settings, ResourceId id) {
            super(settings);
            this.id = id;
        }

        @Override
        protected Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context) {
            StructureDefinition definition = STRUCTURES.get(id);
            ChunkPos chunk = context.chunkPos();
            int x = chunk.getMiddleBlockX();
            int z = chunk.getMiddleBlockZ();
            int surface = context.chunkGenerator()
                    .getFirstFreeHeight(
                            x, z, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
            BlockPos corner = new BlockPos(
                    x - definition.sizeX() / 2, surface + definition.yOffset(), z - definition.sizeZ() / 2);
            long seed = context.random().nextLong();
            return Optional.of(new Structure.GenerationStub(
                    new BlockPos(x, surface, z),
                    builder -> builder.addPiece(new CodePiece(id, corner, definition, seed))));
        }

        @Override
        public StructureType<?> type() {
            return TYPES.get(id);
        }
    }

    /** The box of a code structure; built by the mod's builder clipped to each chunk it touches. */
    private static final class CodePiece extends StructurePiece {

        private final ResourceId id;
        private final long seed;

        CodePiece(ResourceId id, BlockPos corner, StructureDefinition definition, long seed) {
            super(
                    pieceType,
                    0,
                    new BoundingBox(
                            corner.getX(),
                            corner.getY(),
                            corner.getZ(),
                            corner.getX() + definition.sizeX() - 1,
                            corner.getY() + definition.sizeY() - 1,
                            corner.getZ() + definition.sizeZ() - 1));
            this.id = id;
            this.seed = seed;
        }

        CodePiece(CompoundTag tag) {
            super(pieceType, tag);
            this.id = ResourceId.parse(tag.getStringOr("poly_structure", "poly:missing"));
            this.seed = tag.getLongOr("poly_seed", 0);
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
            tag.putString("poly_structure", id.toString());
            tag.putLong("poly_seed", seed);
        }

        @Override
        public void postProcess(
                WorldGenLevel level,
                StructureManager structureManager,
                ChunkGenerator generator,
                RandomSource random,
                BoundingBox chunkBB,
                ChunkPos chunkPos,
                BlockPos referencePos) {
            StructureDefinition definition = STRUCTURES.get(id);
            if (definition == null) {
                return;
            }
            BoundingBox box = this.boundingBox;
            RandomGenerator pieceRandom = generator(RandomSource.create(seed));
            StructurePieceContext context = new StructurePieceContext() {
                @Override
                public dev.plexusmodloader.api.world.BlockPos origin() {
                    return dev.plexusmodloader.api.world.BlockPos.of(box.minX(), box.minY(), box.minZ());
                }

                @Override
                public int sizeX() {
                    return box.getXSpan();
                }

                @Override
                public int sizeY() {
                    return box.getYSpan();
                }

                @Override
                public int sizeZ() {
                    return box.getZSpan();
                }

                @Override
                public RandomGenerator random() {
                    return pieceRandom;
                }

                @Override
                public ResourceId blockAt(int x, int y, int z) {
                    BlockPos pos = new BlockPos(box.minX() + x, box.minY() + y, box.minZ() + z);
                    return chunkBB.isInside(pos)
                            ? Views.blockId(level.getBlockState(pos).getBlock())
                            : ResourceId.minecraft("air");
                }

                @Override
                public void setBlock(int x, int y, int z, ResourceId block) {
                    BlockPos pos = new BlockPos(box.minX() + x, box.minY() + y, box.minZ() + z);
                    net.minecraft.world.level.block.state.BlockState state = state(block);
                    if (box.isInside(pos) && chunkBB.isInside(pos)) {
                        level.setBlock(pos, state, 2);
                    }
                }
            };
            try {
                definition.builder().build(context);
            } catch (RuntimeException e) {
                reportFailure(id, "Structure", e);
            }
        }
    }
}
