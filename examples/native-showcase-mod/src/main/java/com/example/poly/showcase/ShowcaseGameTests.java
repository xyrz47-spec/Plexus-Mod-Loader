package com.example.poly.showcase;

import dev.polymodloader.api.content.block.state.BlockProperty;
import dev.polymodloader.api.content.block.state.BlockStateView;
import dev.polymodloader.api.devtools.gametest.GameTestRegistrar;
import dev.polymodloader.api.devtools.gametest.TestArea;
import dev.polymodloader.api.entity.EntityView;
import dev.polymodloader.api.entity.LivingEntityView;
import dev.polymodloader.api.entity.MobView;
import dev.polymodloader.api.entity.PlayerView;
import dev.polymodloader.api.inventory.InventoryView;
import dev.polymodloader.api.inventory.ItemStackRef;
import dev.polymodloader.api.resource.ResourceId;
import dev.polymodloader.api.world.BlockPos;
import dev.polymodloader.api.world.Direction;
import dev.polymodloader.api.world.StorageOptions;
import dev.polymodloader.api.world.StoredData;
import dev.polymodloader.api.world.WorldView;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-game tests for the showcase. Run them with {@code gradlew :poly-bootstrap:nativeApiGameTests}
 * (the game test server) or {@code /test runmultiple native_showcase:*} in a world.
 */
final class ShowcaseGameTests {

    private static final BlockPos ABOVE_ORIGIN = BlockPos.of(0, 1, 0);

    private ShowcaseGameTests() {}

    /** Checks {@code condition} every 5 ticks and runs {@code then} once it holds; the test times out otherwise. */
    private static void poll(
            dev.polymodloader.api.devtools.gametest.GameTestContext test,
            java.util.function.BooleanSupplier condition,
            Runnable then) {
        test.runAfter(5, () -> {
            if (condition.getAsBoolean()) {
                then.run();
            } else {
                poll(test, condition, then);
            }
        });
    }

    static void register(GameTestRegistrar tests) {
        tests.register("content_is_registered", test -> {
            test.assertTrue(NativeShowcaseMod.glowBlock.isRegistered(), "glow block handle is registered");
            test.assertTrue(NativeShowcaseMod.spark.isRegistered(), "spark handle is registered");
            test.assertTrue(NativeShowcaseMod.showcaseTab.isRegistered(), "creative tab handle is registered");
            test.succeed();
        });
        tests.register("place_glow_block", test -> {
            test.setBlock(ABOVE_ORIGIN, NativeShowcaseMod.GLOW_BLOCK);
            test.assertBlock(ABOVE_ORIGIN, NativeShowcaseMod.GLOW_BLOCK);
            test.succeed();
        });
        tests.register("using_glow_block_gives_spark", test -> {
            test.setBlock(ABOVE_ORIGIN, NativeShowcaseMod.GLOW_BLOCK);
            PlayerView player = test.createMockPlayer();
            test.useBlock(ABOVE_ORIGIN, player);
            test.assertTrue(
                    player.countItem(NativeShowcaseMod.SPARK) == 1,
                    "expected 1 spark, found " + player.countItem(NativeShowcaseMod.SPARK));
            test.succeed();
        });
        tests.register("give_food_item", test -> {
            PlayerView player = test.createMockPlayer();
            player.give(NativeShowcaseMod.STAR_FRUIT, 3);
            test.assertTrue(player.countItem(NativeShowcaseMod.STAR_FRUIT) == 3, "player has 3 star fruit");
            test.succeed();
        });
        tests.register("world_view_reads_and_writes", test -> {
            BlockPos absolute = test.absolute(ABOVE_ORIGIN);
            test.assertTrue(test.world().setBlock(absolute, ResourceId.minecraft("stone")), "block changed");
            test.assertTrue(
                    test.world().blockAt(absolute).equals(ResourceId.minecraft("stone")),
                    "world view reads " + test.world().blockAt(absolute));
            test.assertBlock(ABOVE_ORIGIN, ResourceId.minecraft("stone"));
            test.succeed();
        });
        tests.register("command_returns_sum", test -> {
            int result = test.runCommand("showcase add 2 3");
            test.assertTrue(result == 5, "/showcase add 2 3 returned " + result);
            test.succeed();
        });
        tests.register("loot_table_comes_from_mod_jar", test -> {
            int dropped = test.runCommand("loot spawn ~ ~2 ~ loot native_showcase:blocks/glow_block");
            test.assertTrue(dropped == 1, "the mod's loot table dropped " + dropped + " item stack(s)");
            test.succeed();
        });
        tests.register("tag_comes_from_mod_jar", test -> {
            test.setBlock(ABOVE_ORIGIN, NativeShowcaseMod.GLOW_BLOCK);
            int matches = test.runCommand("execute if block ~ ~1 ~ #minecraft:mineable/pickaxe");
            test.assertTrue(matches == 1, "glow block is in #minecraft:mineable/pickaxe");
            test.succeed();
        });
        tests.register("block_state_properties", test -> {
            test.setBlock(ABOVE_ORIGIN, MachineContent.MACHINE);
            BlockPos at = test.absolute(ABOVE_ORIGIN);
            test.assertTrue(
                    test.world().stateAt(at).get(MachineContent.FACING) == Direction.NORTH, "default facing is north");
            test.assertTrue(!test.world().stateAt(at).get(MachineContent.POWERED), "machine starts unpowered");
            test.assertTrue(test.world().setProperty(at, MachineContent.POWERED, true), "powered changed");
            test.assertTrue(test.world().stateAt(at).get(MachineContent.POWERED), "machine is powered");
            test.assertTrue(
                    !test.world().stateAt(at).has(BlockProperty.integer("level", 0, 3)), "machine has no level");
            boolean rejected = false;
            try {
                test.world().setProperty(at, BlockProperty.bool("lit"), true);
            } catch (IllegalArgumentException expected) {
                rejected = true;
            }
            test.assertTrue(rejected, "setting a missing property is rejected");
            test.succeed();
        });
        tests.register("vanilla_block_state_is_readable", test -> {
            test.setBlock(ABOVE_ORIGIN, ResourceId.minecraft("furnace"));
            test.assertTrue(
                    test.world().stateAt(test.absolute(ABOVE_ORIGIN)).has(MachineContent.FACING),
                    "a furnace has a horizontal facing");
            test.succeed();
        });
        tests.register("using_machine_toggles_and_ticks", 40, test -> {
            test.setBlock(ABOVE_ORIGIN, MachineContent.MACHINE);
            PlayerView player = test.createMockPlayer();
            test.useBlock(ABOVE_ORIGIN, player);
            BlockPos at = test.absolute(ABOVE_ORIGIN);
            test.assertTrue(test.world().stateAt(at).get(MachineContent.POWERED), "use powered the machine");
            test.runAfter(10, () -> {
                int ticks = test.world().blockEntityAt(at).orElseThrow().data().getInt("ticks", 0);
                test.assertTrue(ticks >= 5, "machine counted " + ticks + " ticks");
                test.succeed();
            });
        });
        tests.register("crate_inventory", test -> {
            test.setBlock(ABOVE_ORIGIN, MachineContent.CRATE);
            InventoryView inventory = test.world()
                    .blockEntityAt(test.absolute(ABOVE_ORIGIN))
                    .orElseThrow()
                    .inventory()
                    .orElseThrow();
            test.assertTrue(inventory.size() == 27, "crate has 27 slots, found " + inventory.size());
            int left = inventory.add(NativeShowcaseMod.SPARK, 70);
            test.assertTrue(left == 0, "70 sparks fit, " + left + " left over");
            test.assertTrue(inventory.count(NativeShowcaseMod.SPARK) == 70, "crate holds 70 sparks");
            test.assertTrue(inventory.get(0).count() == 64, "first slot is a full stack");
            test.assertTrue(inventory.get(1).count() == 6, "second slot holds the rest");
            test.succeed();
        });
        tests.register("world_storage", test -> {
            StoredData data = test.world().storage(ResourceId.of(NativeShowcaseMod.MOD_ID, "test_storage"));
            data.data().child("scores").putInt("alex", 12);
            data.markChanged();
            int read = test.world()
                    .storage(ResourceId.of(NativeShowcaseMod.MOD_ID, "test_storage"))
                    .data()
                    .child("scores")
                    .getInt("alex", 0);
            test.assertTrue(read == 12, "stored score reads back as " + read);
            test.succeed();
        });
        tests.register("scheduled_task_runs", 20, test -> {
            AtomicInteger runs = new AtomicInteger();
            test.world().schedule(3, runs::incrementAndGet);
            test.runAfter(1, () -> test.assertTrue(runs.get() == 0, "task did not run early"));
            test.runAfter(6, () -> {
                test.assertTrue(runs.get() == 1, "task ran once, ran " + runs.get());
                test.succeed();
            });
        });
        tests.register("summon_mod_mob", test -> {
            int summoned = test.runCommand("summon native_showcase:golem_friend ~ ~1 ~");
            test.assertTrue(summoned == 1, "/summon returned " + summoned);
            BlockPos at = test.absolute(ABOVE_ORIGIN);
            boolean found = test.world().entitiesNear(at.x() + 0.5, at.y(), at.z() + 0.5, 3).stream()
                    .anyMatch(entity -> entity.type().equals(CreatureContent.GOLEM));
            test.assertTrue(found, "a golem friend stands near the test area");
            test.succeed();
        });
        tests.register("mod_mob_has_attributes", test -> {
            BlockPos at = test.absolute(ABOVE_ORIGIN);
            LivingEntityView golem =
                    (LivingEntityView) test.world().spawnEntity(CreatureContent.GOLEM, at.x(), at.y(), at.z());
            test.assertTrue(golem.maxHealth() == 20f, "max health is " + golem.maxHealth());
            test.assertTrue(golem.isAlive(), "golem is alive");
            test.succeed();
        });
        tests.register("allow_damage_event_cancels", test -> {
            BlockPos at = test.absolute(ABOVE_ORIGIN);
            LivingEntityView golem =
                    (LivingEntityView) test.world().spawnEntity(CreatureContent.GOLEM, at.x(), at.y(), at.z());
            test.assertTrue(!golem.hurt(500), "500 damage was cancelled by the listener");
            test.assertTrue(golem.health() == 20f, "health unchanged: " + golem.health());
            test.assertTrue(golem.hurt(5), "5 damage is allowed");
            test.assertTrue(golem.health() == 15f, "health is 15: " + golem.health());
            test.succeed();
        });
        tests.register("death_event_fires", test -> {
            int before = CreatureContent.GOLEM_DEATHS.get();
            BlockPos at = test.absolute(ABOVE_ORIGIN);
            LivingEntityView golem =
                    (LivingEntityView) test.world().spawnEntity(CreatureContent.GOLEM, at.x(), at.y(), at.z());
            golem.hurt(50);
            test.assertTrue(CreatureContent.GOLEM_DEATHS.get() == before + 1, "death counted once");
            test.succeed();
        });
        tests.register("mod_effect_heals", 80, test -> {
            BlockPos at = test.absolute(ABOVE_ORIGIN);
            LivingEntityView golem =
                    (LivingEntityView) test.world().spawnEntity(CreatureContent.GOLEM, at.x(), at.y(), at.z());
            golem.setHealth(10);
            golem.addEffect(CreatureContent.GLOWING_HEART, 200, 0);
            test.assertTrue(golem.hasEffect(CreatureContent.GLOWING_HEART), "effect applied");
            test.runAfter(45, () -> {
                test.assertTrue(golem.health() >= 11f, "effect healed the golem to " + golem.health());
                test.succeed();
            });
        });
        tests.register("game_rules", test -> {
            test.assertTrue(test.world().gameRule(CreatureContent.golemsGreetPlayers), "boolean rule defaults to true");
            test.assertTrue(test.world().gameRule(CreatureContent.sparkMultiplier) == 1, "integer rule defaults to 1");
            test.runCommand("gamerule native_showcase:spark_multiplier 3");
            test.assertTrue(
                    test.world().gameRule(CreatureContent.sparkMultiplier) == 3,
                    "/gamerule set it to " + test.world().gameRule(CreatureContent.sparkMultiplier));
            test.world().setGameRule(CreatureContent.sparkMultiplier, 1);
            test.succeed();
        });
        tests.register("spawn_egg_sound_and_particles", test -> {
            PlayerView player = test.createMockPlayer();
            player.give(CreatureContent.GOLEM_EGG, 1);
            test.assertTrue(player.countItem(CreatureContent.GOLEM_EGG) == 1, "spawn egg item exists");
            BlockPos at = test.absolute(ABOVE_ORIGIN);
            test.world().playSound(at, CreatureContent.CHIME, 1f, 1f);
            test.world().spawnParticles(ResourceId.minecraft("heart"), at.x(), at.y(), at.z(), 5, 0.3);
            boolean rejected = false;
            try {
                test.world().spawnParticles(ResourceId.minecraft("dust"), at.x(), at.y(), at.z(), 1, 0);
            } catch (IllegalArgumentException expected) {
                rejected = true;
            }
            test.assertTrue(rejected, "particles that need options are rejected");
            test.succeed();
        });
        tests.register("mod_damage_type_from_data", test -> {
            BlockPos at = test.absolute(ABOVE_ORIGIN);
            LivingEntityView golem =
                    (LivingEntityView) test.world().spawnEntity(CreatureContent.GOLEM, at.x(), at.y(), at.z());
            test.assertTrue(golem.hurt(ResourceId.of(NativeShowcaseMod.MOD_ID, "zap"), 3), "zap damage applied");
            test.assertTrue(
                    CreatureContent.lastGolemDamageType.equals(ResourceId.of(NativeShowcaseMod.MOD_ID, "zap")),
                    "listener saw " + CreatureContent.lastGolemDamageType);
            test.succeed();
        });
        tests.register("configured_feature_from_data", 100, TestArea.cube(9), test -> {
            test.runCommand("fill ~1 ~1 ~1 ~7 ~7 ~7 minecraft:stone");
            // An ore vein may find no stone at its random positions; a few tries make that unlikely.
            int placed = 0;
            for (int attempt = 0; attempt < 5 && placed == 0; attempt++) {
                placed = test.runCommand("place feature native_showcase:glow_ore ~4 ~4 ~4");
            }
            test.assertTrue(placed == 1, "/place feature returned " + placed);
            test.succeed();
        });
        tests.register("change_dimension", test -> {
            ResourceId nether = ResourceId.minecraft("the_nether");
            Optional<WorldView> netherWorld = test.world().otherDimension(nether);
            test.assertTrue(netherWorld.isPresent(), "the nether is loaded");
            test.assertTrue(
                    test.world()
                            .otherDimension(ResourceId.of(NativeShowcaseMod.MOD_ID, "missing"))
                            .isEmpty(),
                    "unknown dimensions are empty");
            BlockPos at = test.absolute(ABOVE_ORIGIN);
            EntityView golem = test.world().spawnEntity(CreatureContent.GOLEM, at.x(), at.y(), at.z());
            EntityView moved = golem.changeDimension(nether, 0.5, 70, 0.5);
            test.assertTrue(
                    moved.world().dimension().equals(nether),
                    "golem moved to " + moved.world().dimension());
            moved.remove();
            test.succeed();
        });
        tests.register("choice_property_and_light", test -> {
            test.setBlock(ABOVE_ORIGIN, AdvancedContent.LAMP);
            BlockPos at = test.absolute(ABOVE_ORIGIN);
            WorldView world = test.world();
            test.assertTrue(world.stateAt(at).get(AdvancedContent.COLOR).equals("red"), "default colour is red");
            test.assertTrue(world.emittedLight(at) == 0, "an unlit lamp emits no light");
            world.setProperty(at, AdvancedContent.COLOR, "blue");
            world.setProperty(at, AdvancedContent.LIT, true);
            test.assertTrue(world.stateAt(at).get(AdvancedContent.COLOR).equals("blue"), "colour changed to blue");
            test.assertTrue(world.emittedLight(at) == 15, "a lit lamp emits 15, found " + world.emittedLight(at));
            boolean rejected = false;
            try {
                world.setProperty(at, AdvancedContent.COLOR, "purple");
            } catch (IllegalArgumentException expected) {
                rejected = true;
            }
            test.assertTrue(rejected, "unknown colours are rejected");
            test.succeed();
        });
        tests.register("block_state_snapshot_with", test -> {
            test.setBlock(ABOVE_ORIGIN, AdvancedContent.LAMP);
            BlockPos at = test.absolute(ABOVE_ORIGIN);
            BlockStateView unlit = test.world().stateAt(at);
            BlockStateView litBlue = unlit.with(AdvancedContent.LIT, true).with(AdvancedContent.COLOR, "blue");
            test.assertTrue(litBlue.get(AdvancedContent.LIT), "the changed snapshot is lit");
            test.assertTrue(litBlue.get(AdvancedContent.COLOR).equals("blue"), "the changed snapshot is blue");
            test.assertTrue(!unlit.get(AdvancedContent.LIT), "the original snapshot is still unlit");
            test.assertTrue(!test.world().stateAt(at).get(AdvancedContent.LIT), "the world did not change");
            boolean rejected = false;
            try {
                unlit.with(AdvancedContent.COLOR, "purple");
            } catch (IllegalArgumentException expected) {
                rejected = true;
            }
            test.assertTrue(rejected, "values the block does not allow are rejected");
            test.succeed();
        });
        tests.register("choice_property_reads_game_blocks", test -> {
            test.setBlock(ABOVE_ORIGIN, ResourceId.minecraft("oak_stairs"));
            BlockPos at = test.absolute(ABOVE_ORIGIN);
            BlockProperty.ChoiceProperty half = BlockProperty.choice("half", "top", "bottom");
            test.assertTrue(test.world().stateAt(at).get(half).equals("bottom"), "stairs default to the bottom half");
            test.world().setProperty(at, half, "top");
            test.assertTrue(test.world().stateAt(at).get(half).equals("top"), "stairs moved to the top half");
            test.succeed();
        });
        tests.register("menu_layout_grinder", 80, test -> {
            test.setBlock(ABOVE_ORIGIN, AdvancedContent.GRINDER);
            BlockPos at = test.absolute(ABOVE_ORIGIN);
            InventoryView inventory = test.world()
                    .blockEntityAt(at)
                    .flatMap(entity -> entity.inventory())
                    .orElseThrow();
            test.assertTrue(inventory.size() == 2, "the grinder has 2 slots, found " + inventory.size());
            inventory.set(0, ResourceId.minecraft("cobblestone"), 2);
            PlayerView player = test.createMockPlayer();
            test.assertTrue(player.openMenu(at), "the grinder has a menu");
            test.runAfter(AdvancedContent.GRIND_TICKS + 5, () -> {
                test.assertTrue(
                        inventory.get(1).item().equals(ResourceId.minecraft("gravel"))
                                && inventory.get(1).count() == 1,
                        "one gravel after one grind, found " + inventory.get(1));
                test.assertTrue(inventory.get(0).count() == 1, "one cobblestone left");
                test.succeed();
            });
        });
        tests.register("item_component_roundtrip", test -> {
            PlayerView player = test.createMockPlayer();
            player.give(AdvancedContent.SPARK_WAND, 1);
            ItemStackRef wand = player.mainHandStack();
            test.assertTrue(
                    wand.item().equals(AdvancedContent.SPARK_WAND), "the wand is in the main hand: " + wand.item());
            test.assertTrue(wand.get(AdvancedContent.charge).isEmpty(), "a new wand has no charge");
            wand.set(AdvancedContent.charge, 3);
            wand.setCustomName("Sparky");
            ItemStackRef again = player.inventory().stackAt(0);
            test.assertTrue(again.get(AdvancedContent.charge).orElse(0) == 3, "charge reads back as 3");
            test.assertTrue(again.customName().orElse("").equals("Sparky"), "custom name reads back");
            again.remove(AdvancedContent.charge);
            test.assertTrue(wand.get(AdvancedContent.charge).isEmpty(), "charge removed");
            test.succeed();
        });
        tests.register("projectile_hits_entity", 60, new TestArea(3, 4, 6), test -> {
            BlockPos shooterAt = test.absolute(BlockPos.of(1, 1, 0));
            BlockPos targetAt = test.absolute(BlockPos.of(1, 1, 3));
            LivingEntityView target = (LivingEntityView) test.world()
                    .spawnEntity(CreatureContent.GOLEM, targetAt.x() + 0.5, targetAt.y(), targetAt.z() + 0.5);
            LivingEntityView shooter = (LivingEntityView) test.world()
                    .spawnEntity(CreatureContent.GOLEM, shooterAt.x() + 0.5, shooterAt.y(), shooterAt.z() + 0.5);
            int before = AdvancedContent.BOLT_HITS.get();
            EntityView bolt = shooter.launchProjectile(AdvancedContent.SPARK_BOLT, 1.0f, 0.0f);
            test.assertTrue(bolt.type().equals(AdvancedContent.SPARK_BOLT), "launched " + bolt.type());
            test.runAfter(20, () -> {
                test.assertTrue(AdvancedContent.BOLT_HITS.get() > before, "the bolt hit something");
                test.assertTrue(target.hasEffect(ResourceId.minecraft("glowing")), "the target glows");
                test.assertTrue(!bolt.isAlive(), "the bolt is gone after the hit");
                target.remove();
                shooter.remove();
                test.succeed();
            });
        });
        tests.register("natural_spawns_in_plains", test -> {
            List<ResourceId> creatures = test.world()
                    .naturalSpawns(
                            test.absolute(ABOVE_ORIGIN), dev.polymodloader.api.content.entity.MobCategory.CREATURE);
            test.assertTrue(creatures.contains(CreatureContent.GOLEM), "plains creatures: " + creatures);
            test.assertTrue(
                    creatures.contains(ResourceId.minecraft("cow")), "the game's own spawns stay: " + creatures);
            test.succeed();
        });
        tests.register("custom_goal_seeks_glow_block", 200, new TestArea(10, 4, 5), test -> {
            test.runCommand("fill ~ ~ ~ ~9 ~ ~4 minecraft:stone");
            test.runCommand("fill ~ ~1 ~ ~9 ~3 ~4 minecraft:air");
            // 5 blocks away: inside the goal's 6-block search radius even if the golem drifts a block.
            BlockPos glow = BlockPos.of(6, 1, 2);
            test.setBlock(glow, NativeShowcaseMod.GLOW_BLOCK);
            BlockPos start = test.absolute(BlockPos.of(1, 1, 2));
            MobView golem = (MobView)
                    test.world().spawnEntity(CreatureContent.GOLEM, start.x() + 0.5, start.y(), start.z() + 0.5);
            BlockPos glowAt = test.absolute(glow);
            poll(test, () -> SeekGlowBlockGoal.distanceSquared(golem, glowAt) < 2.5 * 2.5, () -> {
                golem.remove();
                test.succeed();
            });
        });
        tests.register("modify_damage_halves_magic", test -> {
            BlockPos at = test.absolute(ABOVE_ORIGIN);
            LivingEntityView golem =
                    (LivingEntityView) test.world().spawnEntity(CreatureContent.GOLEM, at.x(), at.y(), at.z());
            test.assertTrue(golem.hurt(ResourceId.minecraft("magic"), 8), "magic damage applied");
            test.assertTrue(golem.health() == 16, "8 magic damage became 4, health " + golem.health());
            golem.remove();
            test.succeed();
        });
        tests.register("velocity_and_gravity", 40, new TestArea(3, 8, 3), test -> {
            BlockPos at = test.absolute(BlockPos.of(1, 1, 1));
            EntityView golem = test.world().spawnEntity(CreatureContent.GOLEM, at.x() + 0.5, at.y(), at.z() + 0.5);
            golem.setNoGravity(true);
            golem.setVelocity(0, 0.3, 0);
            test.assertTrue(golem.hasNoGravity(), "gravity is off");
            test.assertTrue(golem.velocity().y() == 0.3, "velocity reads back " + golem.velocity());
            double startY = golem.y();
            test.runAfter(10, () -> {
                test.assertTrue(golem.y() > startY + 1, "the golem rose from " + startY + " to " + golem.y());
                golem.remove();
                test.succeed();
            });
        });
        tests.register("riding_and_passengers", test -> {
            BlockPos at = test.absolute(ABOVE_ORIGIN);
            EntityView golem = test.world().spawnEntity(CreatureContent.GOLEM, at.x(), at.y(), at.z());
            EntityView pig = test.world().spawnEntity(ResourceId.minecraft("pig"), at.x(), at.y(), at.z());
            test.assertTrue(pig.startRiding(golem), "the pig climbs on the rideable golem");
            test.assertTrue(golem.passengers().size() == 1, "the golem carries one passenger");
            test.assertTrue(
                    pig.vehicle()
                            .map(vehicle -> vehicle.uuid().equals(golem.uuid()))
                            .orElse(false),
                    "the pig rides the golem");
            pig.stopRiding();
            test.assertTrue(golem.passengers().isEmpty(), "the pig got off");
            golem.remove();
            pig.remove();
            test.succeed();
        });
        tests.register("mob_view_target", test -> {
            BlockPos at = test.absolute(ABOVE_ORIGIN);
            MobView golem = (MobView) test.world().spawnEntity(CreatureContent.GOLEM, at.x(), at.y(), at.z());
            LivingEntityView cow = (LivingEntityView)
                    test.world().spawnEntity(ResourceId.minecraft("cow"), at.x() + 2, at.y(), at.z());
            golem.setTarget(cow);
            test.assertTrue(
                    golem.target()
                            .map(target -> target.uuid().equals(cow.uuid()))
                            .orElse(false),
                    "the cow is the target");
            golem.setTarget(null);
            test.assertTrue(golem.target().isEmpty(), "the target was cleared");
            golem.remove();
            cow.remove();
            test.succeed();
        });
        tests.register("advancement_trigger", test -> {
            PlayerView player = test.createMockServerPlayer();
            ResourceId advancement = ResourceId.of(NativeShowcaseMod.MOD_ID, "used_wand");
            test.assertTrue(!player.hasAdvancement(advancement), "a new player has not used the wand");
            AdvancedContent.usedWand.trigger(player);
            test.assertTrue(player.hasAdvancement(advancement), "the trigger completed the advancement");
            test.assertTrue(player.revokeAdvancement(advancement), "revoked");
            test.assertTrue(!player.hasAdvancement(advancement), "no longer completed");
            test.assertTrue(player.grantAdvancement(advancement), "granted");
            test.assertTrue(player.hasAdvancement(advancement), "completed again");
            test.succeed();
        });
        tests.register("code_biome_is_registered", 100, TestArea.cube(9), test -> {
            // The game blurs biome borders by up to one 4-block cell, so fill well around the probe.
            int filled = test.runCommand("fillbiome ~ ~ ~ ~8 ~8 ~8 native_showcase:glow_meadow");
            test.assertTrue(filled > 0, "/fillbiome with the code biome returned " + filled);
            ResourceId biome = test.world().biomeAt(test.absolute(BlockPos.of(4, 4, 4)));
            test.assertTrue(biome.equals(WorldContent.GLOW_MEADOW), "the biome reads back as " + biome);
            test.succeed();
        });
        tests.register("chunk_forcing_and_events", test -> {
            BlockPos origin = test.absolute(ABOVE_ORIGIN);
            int chunkX = Math.floorDiv(origin.x(), 16) + 40;
            int chunkZ = Math.floorDiv(origin.z(), 16) + 40;
            WorldView world = test.world();
            test.assertTrue(!world.isChunkLoaded(chunkX, chunkZ), "the far chunk starts unloaded");
            WorldContent.WATCHED_CHUNK.set(WorldContent.pack(chunkX, chunkZ));
            test.assertTrue(world.setChunkForced(chunkX, chunkZ, true), "forcing changed the chunk");
            test.assertTrue(world.isChunkLoaded(chunkX, chunkZ), "the forced chunk is loaded");
            test.assertTrue(WorldContent.WATCHED_CHUNK.get() == WorldContent.NOT_WATCHING, "CHUNK_LOADED fired for it");
            test.assertTrue(world.setChunkForced(chunkX, chunkZ, false), "released");
            test.succeed();
        });
        tests.register("reload_listener_reads_mod_data", test -> {
            Map<ResourceId, String> messages = OperationsContent.messages;
            test.assertTrue(messages.size() == 2, "two message files, found " + messages.keySet());
            test.assertTrue(
                    messages.getOrDefault(ResourceId.of(NativeShowcaseMod.MOD_ID, "tips/first"), "")
                            .contains("golem"),
                    "nested files keep their path");
            test.succeed();
        });
        tests.register("entity_data_is_saved_with_the_entity", test -> {
            BlockPos at = test.absolute(ABOVE_ORIGIN);
            EntityView golem = test.world().spawnEntity(CreatureContent.GOLEM, at.x(), at.y(), at.z());
            StoredData data = golem.data(OperationsContent.visits);
            data.data().putInt("count", 7);
            data.markChanged();
            int found = test.runCommand(
                    "execute if data entity " + golem.uuid() + " poly_entity_data.\"native_showcase:visits\"{count:7}");
            test.assertTrue(found == 1, "the entity's save data holds the mod data");
            test.assertTrue(golem.data(OperationsContent.visits).data().getInt("count", 0) == 7, "reads back");
            golem.remove();
            test.succeed();
        });
        tests.register("versioned_world_storage", test -> {
            StoredData info = test.world().storage(OperationsContent.WORLD_INFO, OperationsContent.WORLD_INFO_OPTIONS);
            test.assertTrue(!info.data().has("poly_version"), "the version key is Poly Mod Loader's, not the mod's");
            boolean rejected = false;
            try {
                test.world().storage(OperationsContent.WORLD_INFO, StorageOptions.DEFAULT);
            } catch (IllegalArgumentException expected) {
                rejected = true;
            }
            test.assertTrue(rejected, "the same storage cannot be opened with other options");
            test.succeed();
        });
        tests.register("permission_nodes_and_providers", test -> {
            PlayerView player = test.createMockServerPlayer();
            test.assertTrue(!player.hasPermission(OperationsContent.place), "players need level 2 to place");
            test.assertTrue(player.hasPermission(OperationsContent.secret), "the provider allows the secret");
            test.assertTrue(OperationsContent.place.id().equals("native_showcase.place"), "node ids are dotted");
            test.succeed();
        });
        tests.register("binary_reload_files", test -> {
            byte[] hello = OperationsContent.banners.get(ResourceId.of(NativeShowcaseMod.MOD_ID, "hello"));
            test.assertTrue(hello != null, "the .txt file was loaded: " + OperationsContent.banners.keySet());
            test.assertTrue(
                    new String(hello, java.nio.charset.StandardCharsets.UTF_8).startsWith("Native Showcase"),
                    "its bytes are the file's");
            test.succeed();
        });
        tests.register("entity_data_migrates_on_load", test -> {
            BlockPos at = test.absolute(ABOVE_ORIGIN);
            EntityView golem = test.world().spawnEntity(CreatureContent.GOLEM, at.x(), at.y(), at.z());
            // Loads version 1 data into the golem, as if from an old save.
            int merged = test.runCommand("data merge entity " + golem.uuid()
                    + " {poly_entity_data:{\"native_showcase:golem_mood\":{feeling:\"sleepy\",poly_version:1}}}");
            test.assertTrue(merged == 1, "/data merge returned " + merged);
            StoredData mood = golem.data(OperationsContent.golemMood);
            test.assertTrue(mood.data().getString("mood", "").equals("sleepy"), "migrated: " + mood.data());
            test.assertTrue(!mood.data().has("feeling") && !mood.data().has("poly_version"), "old keys are gone");
            golem.remove();
            test.succeed();
        });
        tests.register("delayed_assertion", 20, test -> {
            test.setBlock(ABOVE_ORIGIN, NativeShowcaseMod.GLOW_BLOCK);
            test.runAfter(5, () -> {
                test.assertBlock(ABOVE_ORIGIN, NativeShowcaseMod.GLOW_BLOCK);
                test.succeed();
            });
        });
    }
}
