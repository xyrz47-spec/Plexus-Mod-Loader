package com.example.poly.showcase;

import dev.polymodloader.api.content.entity.MobCategory;
import dev.polymodloader.api.event.EventRegistry;
import dev.polymodloader.api.event.world.WorldEvents;
import dev.polymodloader.api.resource.ResourceId;
import dev.polymodloader.api.worldgen.BiomeDefinition;
import dev.polymodloader.api.worldgen.ColumnBuilder;
import dev.polymodloader.api.worldgen.GenerationStepName;
import dev.polymodloader.api.worldgen.TerrainGenerator;
import dev.polymodloader.api.worldgen.WorldgenRegistrar;
import java.util.concurrent.atomic.AtomicLong;

/**
 * World generation in code: a biome, terraced terrain used by the {@code native_showcase:terraces}
 * dimension (from {@code data/native_showcase/dimension/terraces.json}), and a chunk load watch.
 */
final class WorldContent {

    static final ResourceId GLOW_MEADOW = ResourceId.of(NativeShowcaseMod.MOD_ID, "glow_meadow");
    static final ResourceId TERRACES = ResourceId.of(NativeShowcaseMod.MOD_ID, "terraces");
    static final long NOT_WATCHING = Long.MIN_VALUE;
    /** A chunk a test waits for, packed as {@code x << 32 | z}; set to {@link #NOT_WATCHING} when seen. */
    static final AtomicLong WATCHED_CHUNK = new AtomicLong(NOT_WATCHING);

    private WorldContent() {}

    static void declare(WorldgenRegistrar worldgen, EventRegistry events) {
        worldgen.biome(
                "glow_meadow",
                BiomeDefinition.builder()
                        .climate(0.7f, 0.6f, true)
                        .skyColor(0x9ad0ff)
                        .grassColor(0x7fd16b)
                        .feature(GenerationStepName.VEGETAL_DECORATION, ResourceId.minecraft("flower_plains"))
                        .feature(GenerationStepName.VEGETAL_DECORATION, ResourceId.minecraft("patch_grass_plain"))
                        .feature(GenerationStepName.VEGETAL_DECORATION, CodeWorldgenContent.BOULDER)
                        .feature(
                                GenerationStepName.UNDERGROUND_ORES,
                                ResourceId.of(NativeShowcaseMod.MOD_ID, "glow_ore"))
                        .spawn(MobCategory.CREATURE, CreatureContent.GOLEM, 10, 1, 2)
                        .spawn(MobCategory.CREATURE, ResourceId.minecraft("sheep"), 8, 2, 4)
                        .build());
        worldgen.chunkGenerator("terraces", new Terraces());
        events.register(
                WorldEvents.CHUNK_LOADED,
                (world, chunkX, chunkZ) -> WATCHED_CHUNK.compareAndSet(pack(chunkX, chunkZ), NOT_WATCHING));
    }

    static long pack(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    /**
     * Terraces 8 blocks wide, stepping up by 2 every 8 blocks diagonally, grass on top, and a glow
     * block on the corner of every chunk. The height at {@code (0, 0)} is 64.
     */
    static final class Terraces implements TerrainGenerator {

        static int height(int x, int z) {
            return 64 + Math.floorMod(Math.floorDiv(x, 8) + Math.floorDiv(z, 8), 4) * 2;
        }

        @Override
        public void fillColumn(int x, int z, ColumnBuilder column) {
            int top = height(x, z);
            column.set(column.minY(), ResourceId.minecraft("bedrock"));
            column.fill(column.minY() + 1, top - 4, ResourceId.minecraft("stone"));
            column.fill(top - 3, top - 1, ResourceId.minecraft("dirt"));
            column.set(top, ResourceId.minecraft("grass_block"));
            if (Math.floorMod(x, 16) == 0 && Math.floorMod(z, 16) == 0) {
                column.set(top + 1, NativeShowcaseMod.GLOW_BLOCK);
            }
        }
    }
}
