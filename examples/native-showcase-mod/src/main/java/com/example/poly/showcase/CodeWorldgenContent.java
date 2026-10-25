package com.example.poly.showcase;

import dev.polymodloader.api.devtools.gametest.GameTestContext;
import dev.polymodloader.api.devtools.gametest.GameTestRegistrar;
import dev.polymodloader.api.devtools.gametest.TestArea;
import dev.polymodloader.api.resource.ResourceId;
import dev.polymodloader.api.world.BlockPos;
import dev.polymodloader.api.world.WorldView;
import dev.polymodloader.api.worldgen.BiomeSelector;
import dev.polymodloader.api.worldgen.FeatureContext;
import dev.polymodloader.api.worldgen.FeaturePlacement;
import dev.polymodloader.api.worldgen.StructureDefinition;
import dev.polymodloader.api.worldgen.StructurePieceContext;
import dev.polymodloader.api.worldgen.WorldgenRegistrar;

/**
 * World generation in code: a boulder feature, a density function shaping the game's noise terrain
 * in the {@code native_showcase:waves} dimension, and a shrine structure.
 */
final class CodeWorldgenContent {

    static final ResourceId BOULDER = ResourceId.of(NativeShowcaseMod.MOD_ID, "boulder");
    static final ResourceId WAVES = ResourceId.of(NativeShowcaseMod.MOD_ID, "waves");
    static final ResourceId SHRINE = ResourceId.of(NativeShowcaseMod.MOD_ID, "shrine");

    private static final ResourceId MOSSY = ResourceId.minecraft("mossy_cobblestone");
    private static final ResourceId BRICKS = ResourceId.minecraft("stone_bricks");

    private CodeWorldgenContent() {}

    static void declare(WorldgenRegistrar worldgen) {
        // A mossy boulder on the surface of one in four chunks of the glow meadow (WorldContent adds it).
        worldgen.feature("boulder", FeaturePlacement.onSurface(1).rarity(4), CodeWorldgenContent::boulder);

        // Rolling waves of terrain: solid below 64 + 6·sin(x / 6), inside the game's noise generator,
        // which still runs its surface rules; data/native_showcase/worldgen/noise_settings/waves.json.
        worldgen.densityFunction("waves", -1, 1, (x, y, z) -> clamp((waveHeight(x) - y) / 4.0));

        // A small open shrine with a glow block in the middle.
        worldgen.structure(
                "shrine",
                StructureDefinition.builder(7, 4, 7, CodeWorldgenContent::shrine)
                        .biomes(BiomeSelector.tag(ResourceId.minecraft("is_overworld")))
                        .spacing(40, 12)
                        .build());
    }

    static double waveHeight(int x) {
        return 64 + 6 * Math.sin(x / 6.0);
    }

    private static double clamp(double value) {
        return Math.max(-1, Math.min(1, value));
    }

    private static boolean boulder(FeatureContext context) {
        BlockPos origin = context.origin();
        int radius = 1 + context.random().nextInt(2);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dy * dy + dz * dz <= radius * radius + 1) {
                        context.setBlock(origin.offset(dx, dy, dz), MOSSY);
                    }
                }
            }
        }
        return true;
    }

    private static void shrine(StructurePieceContext shrine) {
        shrine.fill(0, 0, 0, 6, 0, 6, BRICKS);
        for (int x : new int[] {0, 6}) {
            for (int z : new int[] {0, 6}) {
                shrine.fill(x, 1, z, x, 3, z, BRICKS);
            }
        }
        shrine.setBlock(3, 1, 3, NativeShowcaseMod.GLOW_BLOCK);
    }

    static void registerTests(GameTestRegistrar tests) {
        tests.register("code_feature_places_blocks", 40, TestArea.cube(9), test -> {
            test.runCommand("fill ~ ~ ~ ~8 ~ ~8 minecraft:stone");
            int placed = test.runCommand("place feature native_showcase:boulder ~4 ~3 ~4");
            test.assertTrue(placed == 1, "/place feature returned " + placed);
            test.assertBlock(BlockPos.of(4, 3, 4), MOSSY);
            test.assertBlock(BlockPos.of(5, 3, 4), MOSSY);
            test.succeed();
        });
        tests.register("code_structure_is_placed", 200, test -> {
            // Far from every test area: the structure is centred on its own chunk, which must be loaded.
            BlockPos far = test.absolute(BlockPos.of(0, 0, 0)).offset(4096, 0, 0);
            int chunkX = Math.floorDiv(far.x(), 16);
            int chunkZ = Math.floorDiv(far.z(), 16);
            test.world().setChunkForced(chunkX, chunkZ, true);
            poll(test, () -> test.world().isChunkLoaded(chunkX, chunkZ), () -> {
                int placed = test.runCommand(
                        "place structure native_showcase:shrine " + far.x() + " " + far.y() + " " + far.z());
                int middleX = chunkX * 16 + 8;
                int middleZ = chunkZ * 16 + 8;
                boolean found = findGlowBlock(test, middleX, middleZ);
                test.world().setChunkForced(chunkX, chunkZ, false);
                test.assertTrue(placed == 1, "/place structure returned " + placed);
                test.assertTrue(found, "the shrine's glow block is at the chunk centre " + middleX + ", " + middleZ);
                test.succeed();
            });
        });
    }

    /**
     * Whether the noise terrain of the waves dimension follows the density function at a few columns.
     * The game blends density inside its noise cells, so the surface may differ by one block.
     */
    static boolean wavesFollowDensity(WorldView waves) {
        for (int x : new int[] {0, 9, 20, 31}) {
            int expectedTop = (int) Math.ceil(waveHeight(x)) - 1;
            int top = 127;
            while (top > 0 && waves.blockAt(BlockPos.of(x, top, 0)).equals(ResourceId.minecraft("air"))) {
                top--;
            }
            if (Math.abs(top - expectedTop) > 1) {
                return false;
            }
        }
        return true;
    }

    /** Checks {@code condition} every 5 ticks and runs {@code then} once it holds; the test times out otherwise. */
    private static void poll(GameTestContext test, java.util.function.BooleanSupplier condition, Runnable then) {
        test.runAfter(5, () -> {
            if (condition.getAsBoolean()) {
                then.run();
            } else {
                poll(test, condition, then);
            }
        });
    }

    private static boolean findGlowBlock(GameTestContext test, int x, int z) {
        for (int y = 200; y > -64; y--) {
            if (test.world().blockAt(BlockPos.of(x, y, z)).equals(NativeShowcaseMod.GLOW_BLOCK)) {
                return true;
            }
        }
        return false;
    }
}
