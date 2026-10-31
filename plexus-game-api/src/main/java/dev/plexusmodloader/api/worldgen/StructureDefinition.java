package dev.plexusmodloader.api.worldgen;

import dev.plexusmodloader.api.resource.ResourceId;
import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;
import java.util.Objects;

/**
 * A structure built in code: a box placed on the terrain surface at the centre of chosen chunks,
 * filled by a {@link StructureBuilder}. Plexus Mod Loader writes the structure and its structure set into the
 * generated data pack, so {@code /locate structure} and {@code /place structure} work with its ID.
 *
 * @param sizeX box size along x, 1–128
 * @param sizeY box size along y, 1–256
 * @param sizeZ box size along z, 1–128
 * @param yOffset where the box's bottom is relative to the surface; negative sinks it into the ground
 * @param biomes where it may generate
 * @param spacing average distance between structures, in chunks (village 34)
 * @param separation minimum distance between structures, in chunks; less than {@code spacing}
 * @param step the generation step it is built in
 * @param adaptTerrain whether the terrain is smoothed around it, like around a village
 * @param builder builds its blocks
 */
@ApiStability(StabilityLevel.PREVIEW)
public record StructureDefinition(
        int sizeX,
        int sizeY,
        int sizeZ,
        int yOffset,
        BiomeSelector biomes,
        int spacing,
        int separation,
        GenerationStepName step,
        boolean adaptTerrain,
        StructureBuilder builder) {

    public StructureDefinition {
        if (sizeX < 1 || sizeX > 128 || sizeZ < 1 || sizeZ > 128 || sizeY < 1 || sizeY > 256) {
            throw new IllegalArgumentException(
                    "Structure size must be 1-128 wide and long and 1-256 high: " + sizeX + "x" + sizeY + "x" + sizeZ);
        }
        if (spacing < 1 || spacing > 4096 || separation < 0 || separation >= spacing) {
            throw new IllegalArgumentException(
                    "spacing must be 1-4096 and separation 0..spacing-1: " + spacing + ", " + separation);
        }
        Objects.requireNonNull(biomes, "biomes");
        Objects.requireNonNull(step, "step");
        Objects.requireNonNull(builder, "builder");
    }

    /** A builder for a {@code sizeX × sizeY × sizeZ} structure built by {@code builder}. */
    public static Builder builder(int sizeX, int sizeY, int sizeZ, StructureBuilder builder) {
        return new Builder(sizeX, sizeY, sizeZ, builder);
    }

    /** Builds a {@link StructureDefinition}. */
    public static final class Builder {

        private final int sizeX;
        private final int sizeY;
        private final int sizeZ;
        private final StructureBuilder builder;
        private int yOffset;
        private BiomeSelector biomes = BiomeSelector.tag(ResourceId.minecraft("is_overworld"));
        private int spacing = 32;
        private int separation = 8;
        private GenerationStepName step = GenerationStepName.SURFACE_STRUCTURES;
        private boolean adaptTerrain;

        private Builder(int sizeX, int sizeY, int sizeZ, StructureBuilder builder) {
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;
            this.builder = builder;
        }

        /** Raises (or with a negative value sinks) the box relative to the surface; 0 by default. */
        public Builder yOffset(int yOffset) {
            this.yOffset = yOffset;
            return this;
        }

        /** Where it may generate; every overworld biome ({@code #minecraft:is_overworld}) by default. */
        public Builder biomes(BiomeSelector biomes) {
            this.biomes = biomes;
            return this;
        }

        /** Average and minimum distance between structures in chunks; 32 and 8 by default. */
        public Builder spacing(int spacing, int separation) {
            this.spacing = spacing;
            this.separation = separation;
            return this;
        }

        /** The generation step; {@link GenerationStepName#SURFACE_STRUCTURES} by default. */
        public Builder step(GenerationStepName step) {
            this.step = step;
            return this;
        }

        /** Smooths the terrain around the box, like around a village. */
        public Builder adaptTerrain() {
            this.adaptTerrain = true;
            return this;
        }

        /** Creates the definition; invalid values throw {@link IllegalArgumentException}. */
        public StructureDefinition build() {
            return new StructureDefinition(
                    sizeX, sizeY, sizeZ, yOffset, biomes, spacing, separation, step, adaptTerrain, builder);
        }
    }
}
