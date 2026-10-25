package com.example.crystalgarden;

import dev.polymodloader.api.devtools.gametest.GameTestRegistrar;
import dev.polymodloader.api.entity.PlayerView;
import dev.polymodloader.api.world.BlockPos;

/**
 * In-game tests on Mojang's game test server. Run them with
 * {@code ./gradlew gameTests "-PpolyTests=crystal_garden:*"}.
 */
final class GardenTests {

    private static final BlockPos ABOVE_FLOOR = BlockPos.of(0, 1, 0);

    private GardenTests() {}

    static void register(GameTestRegistrar tests) {
        // A sprout reaches stage 1 after TICKS_PER_STAGE ticks on its own.
        tests.register("sprout_grows", CrystalSprout.TICKS_PER_STAGE + 40, test -> {
            test.setBlock(ABOVE_FLOOR, CrystalGardenMod.CRYSTAL_SPROUT);
            BlockPos at = test.absolute(ABOVE_FLOOR);
            test.runAfter(CrystalSprout.TICKS_PER_STAGE + 5, () -> {
                int stage = test.world().stateAt(at).get(CrystalSprout.STAGE);
                test.assertTrue(stage == 1, "stage after " + CrystalSprout.TICKS_PER_STAGE + " ticks is " + stage);
                test.succeed();
            });
        });
        // A ripe sprout gives crystals, starts again, and the harvest is counted for the player and the world.
        tests.register("ripe_sprout_can_be_harvested", test -> {
            test.setBlock(ABOVE_FLOOR, CrystalGardenMod.CRYSTAL_SPROUT);
            BlockPos at = test.absolute(ABOVE_FLOOR);
            test.assertTrue(CrystalSprout.ripen(test.world(), at), "the sprout ripened");
            int harvestsBefore = test.world()
                    .storage(CrystalGardenMod.GARDEN_STATS, CrystalGardenMod.GARDEN_STATS_OPTIONS)
                    .data()
                    .getInt("harvests", 0);
            PlayerView player = test.createMockServerPlayer();
            test.useBlock(ABOVE_FLOOR, player);
            test.assertTrue(
                    player.countItem(CrystalGardenMod.CRYSTAL) == CrystalSprout.CRYSTALS_PER_HARVEST,
                    "the player holds " + player.countItem(CrystalGardenMod.CRYSTAL) + " crystal(s)");
            test.assertTrue(test.world().stateAt(at).get(CrystalSprout.STAGE) == 0, "the sprout starts again");
            int counted = player.data(CrystalGardenMod.harvested).data().getInt("count", 0);
            test.assertTrue(counted == CrystalSprout.CRYSTALS_PER_HARVEST, "the player's count is " + counted);
            int harvests = test.world()
                    .storage(CrystalGardenMod.GARDEN_STATS, CrystalGardenMod.GARDEN_STATS_OPTIONS)
                    .data()
                    .getInt("harvests", 0);
            test.assertTrue(harvests == harvestsBefore + 1, "the world counted " + harvests + " harvest(s)");
            test.succeed();
        });
        // The server may use /garden grow; a fresh player may not.
        tests.register("grow_command_needs_permission", test -> {
            test.setBlock(ABOVE_FLOOR, CrystalGardenMod.CRYSTAL_SPROUT);
            int result = test.runCommand("garden grow ~ ~1 ~");
            test.assertTrue(result == 1, "/garden grow returned " + result);
            int stage = test.world().stateAt(test.absolute(ABOVE_FLOOR)).get(CrystalSprout.STAGE);
            test.assertTrue(stage == CrystalSprout.RIPE, "stage after /garden grow is " + stage);
            PlayerView player = test.createMockServerPlayer();
            test.assertTrue(
                    !player.hasPermission(CrystalGardenMod.growPermission), "a new player may not ripen sprouts");
            test.succeed();
        });
        // The crystal block drops itself (the loot table polyResources generates) and is mined with a pickaxe.
        tests.register("crystal_block_resources", test -> {
            test.setBlock(ABOVE_FLOOR, CrystalGardenMod.CRYSTAL_BLOCK);
            int matches = test.runCommand("execute if block ~ ~1 ~ #minecraft:mineable/pickaxe");
            test.assertTrue(matches == 1, "the crystal block is in #minecraft:mineable/pickaxe");
            int dropped = test.runCommand("loot spawn ~ ~2 ~ loot crystal_garden:blocks/crystal_block");
            test.assertTrue(dropped == 1, "the loot table dropped " + dropped + " stack(s)");
            test.succeed();
        });
    }
}
