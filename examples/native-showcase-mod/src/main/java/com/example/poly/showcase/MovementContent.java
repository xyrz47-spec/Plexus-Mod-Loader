package com.example.poly.showcase;

import dev.polymodloader.api.content.ContentRegistrar;
import dev.polymodloader.api.content.block.BlockDefinition;
import dev.polymodloader.api.content.block.BlockShape;
import dev.polymodloader.api.content.block.BlockSound;
import dev.polymodloader.api.content.entity.EntityDefinition;
import dev.polymodloader.api.content.entity.MobCategory;
import dev.polymodloader.api.content.entity.model.EntityModelDefinition;
import dev.polymodloader.api.content.entity.model.KeyframeAnimation;
import dev.polymodloader.api.devtools.gametest.GameTestContext;
import dev.polymodloader.api.devtools.gametest.GameTestRegistrar;
import dev.polymodloader.api.devtools.gametest.TestArea;
import dev.polymodloader.api.entity.EntityView;
import dev.polymodloader.api.entity.MobView;
import dev.polymodloader.api.resource.ResourceId;
import dev.polymodloader.api.world.BlockPos;

/** Block shapes, flying and swimming mobs, and keyframe animations, with their in-game tests. */
final class MovementContent {

    static final ResourceId STONE_STEP = ResourceId.of(NativeShowcaseMod.MOD_ID, "stone_step");
    static final ResourceId MINNOW = ResourceId.of(NativeShowcaseMod.MOD_ID, "minnow");

    private MovementContent() {}

    static void declare(ContentRegistrar content) {
        // A half-height block: entities stand on it at y + 0.5 and the outline is half as tall.
        content.block(
                "stone_step",
                BlockDefinition.builder()
                        .sound(BlockSound.STONE)
                        .shape(BlockShape.box(0, 0, 0, 16, 8, 16))
                        .build());

        // A fish-like mob that swims under water and wags its tail while moving.
        content.entity(
                "minnow",
                EntityDefinition.builder(ResourceId.of(NativeShowcaseMod.MOD_ID, "textures/entity/wisp.png"))
                        .size(0.5f, 0.4f)
                        .category(MobCategory.WATER_CREATURE)
                        .maxHealth(4)
                        .movementSpeed(1.0)
                        .swimming()
                        .model(EntityModelDefinition.builder(32, 32)
                                .part("body", part -> part.pivot(0, 21, 0).box(0, 0, -2, -2, -3, 4, 4, 6))
                                .part(
                                        "tail",
                                        part -> part.parent("body")
                                                .pivot(0, 0, 3)
                                                .box(0, 12, 0, -2, 0, 1, 4, 3))
                                .animation(
                                        "swim",
                                        KeyframeAnimation.builder(0.5f)
                                                .looping()
                                                .trigger(KeyframeAnimation.Trigger.WHILE_MOVING)
                                                .smooth()
                                                .rotation("tail", 0, 0, -25, 0)
                                                .rotation("tail", 0.25f, 0, 25, 0)
                                                .rotation("tail", 0.5f, 0, -25, 0)
                                                .build())
                                .build())
                        .build());
    }

    static void registerTests(GameTestRegistrar tests) {
        tests.register("block_shape_collision", 60, new TestArea(3, 4, 3), test -> {
            BlockPos step = BlockPos.of(1, 1, 1);
            test.setBlock(step, STONE_STEP);
            BlockPos at = test.absolute(step);
            EntityView stand = test.world()
                    .spawnEntity(ResourceId.minecraft("armor_stand"), at.x() + 0.5, at.y() + 1.5, at.z() + 0.5);
            poll(test, () -> Math.abs(stand.y() - (at.y() + 0.5)) < 0.01, () -> {
                stand.remove();
                test.succeed();
            });
        });
        tests.register("flying_mob_flies_to_a_point_in_the_air", 160, TestArea.cube(9), test -> {
            test.runCommand("fill ~ ~ ~ ~8 ~ ~8 minecraft:stone");
            BlockPos start = test.absolute(BlockPos.of(2, 1, 2));
            BlockPos goal = test.absolute(BlockPos.of(6, 6, 6));
            MobView wisp =
                    (MobView) test.world().spawnEntity(VisualContent.WISP, start.x() + 0.5, start.y(), start.z() + 0.5);
            poll(
                    test,
                    () -> {
                        wisp.navigateTo(goal.x() + 0.5, goal.y(), goal.z() + 0.5, 1.0);
                        return distanceSquared(wisp, goal) < 1.5 * 1.5;
                    },
                    () -> {
                        wisp.remove();
                        test.succeed();
                    });
        });
        tests.register("swimming_mob_swims_under_water", 200, TestArea.cube(9), test -> {
            // A closed stone tank, so no water flows into neighbouring tests.
            test.runCommand("fill ~ ~ ~ ~8 ~7 ~8 minecraft:stone");
            test.runCommand("fill ~1 ~1 ~1 ~7 ~6 ~7 minecraft:water");
            BlockPos start = test.absolute(BlockPos.of(2, 3, 2));
            BlockPos goal = test.absolute(BlockPos.of(6, 3, 6));
            MobView minnow = (MobView) test.world().spawnEntity(MINNOW, start.x() + 0.5, start.y(), start.z() + 0.5);
            poll(
                    test,
                    () -> {
                        minnow.navigateTo(goal.x() + 0.5, goal.y(), goal.z() + 0.5, 1.0);
                        return distanceSquared(minnow, goal) < 1.5 * 1.5;
                    },
                    () -> {
                        test.assertTrue(minnow.isAlive(), "the minnow breathes under water");
                        minnow.remove();
                        test.succeed();
                    });
        });
        tests.register("model_animations_by_name", test -> {
            BlockPos at = test.absolute(BlockPos.of(0, 2, 0));
            MobView wisp = (MobView) test.world().spawnEntity(VisualContent.WISP, at.x(), at.y(), at.z());
            wisp.playAnimation("wave");
            wisp.stopAnimation("wave");
            assertThrows(test, () -> wisp.playAnimation("pulse"), "an ALWAYS animation cannot be started");
            assertThrows(test, () -> wisp.playAnimation("missing"), "an unknown animation is refused");
            MobView golem = (MobView) test.world().spawnEntity(CreatureContent.GOLEM, at.x(), at.y(), at.z());
            assertThrows(test, () -> golem.playAnimation("wave"), "a mob without a model has no animations");
            wisp.remove();
            golem.remove();
            test.succeed();
        });
    }

    private static void assertThrows(GameTestContext test, Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        test.assertTrue(false, message);
    }

    private static double distanceSquared(EntityView entity, BlockPos block) {
        double dx = entity.x() - (block.x() + 0.5);
        double dy = entity.y() - block.y();
        double dz = entity.z() - (block.z() + 0.5);
        return dx * dx + dy * dy + dz * dz;
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
}
