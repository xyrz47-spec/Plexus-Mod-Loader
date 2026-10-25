package com.example.poly.showcase;

import dev.polymodloader.api.content.ContentRegistrar;
import dev.polymodloader.api.content.CreativeTab;
import dev.polymodloader.api.content.block.BlockDefinition;
import dev.polymodloader.api.content.block.BlockSound;
import dev.polymodloader.api.content.block.state.BlockProperty;
import dev.polymodloader.api.content.item.ItemDefinition;
import dev.polymodloader.api.devtools.gametest.GameTestRegistrar;
import dev.polymodloader.api.devtools.gametest.TestArea;
import dev.polymodloader.api.entity.EntityView;
import dev.polymodloader.api.entity.LivingEntityView;
import dev.polymodloader.api.entity.PlayerView;
import dev.polymodloader.api.event.EventPriority;
import dev.polymodloader.api.event.EventRegistry;
import dev.polymodloader.api.event.EventResult;
import dev.polymodloader.api.event.block.BlockEvents;
import dev.polymodloader.api.event.interaction.InteractionEvents;
import dev.polymodloader.api.resource.ResourceId;
import dev.polymodloader.api.world.BlockPos;
import dev.polymodloader.api.world.Direction;
import dev.polymodloader.api.world.InteractionResult;
import dev.polymodloader.api.world.WorldView;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Interaction events with results: a <em>warded block</em> protects itself and what stands on it.
 * Players cannot break it unless they hold a spark ({@code BlockEvents.BREAK}), cannot build on it
 * ({@code PLACE}), use blocks on it ({@code USE_BLOCK}), or use or attack entities standing on it
 * ({@code USE_ENTITY}, {@code ATTACK_ENTITY}). Items named "Sealed" cannot be used ({@code USE_ITEM}).
 */
final class InteractionContent {

    static final ResourceId WARDED_BLOCK = ResourceId.of(NativeShowcaseMod.MOD_ID, "warded_block");
    static final String SEALED = "Sealed";
    /** Where players placed blocks ({@code BlockEvents.PLACED}); world positions, for the tests. */
    static final Set<BlockPos> PLACED_AT = ConcurrentHashMap.newKeySet();

    private static final BlockProperty<Boolean> OPEN = BlockProperty.bool("open");

    private InteractionContent() {}

    static void declare(ContentRegistrar content, EventRegistry events) {
        content.block(
                "warded_block",
                BlockDefinition.builder()
                        .strength(2.0f, 1200.0f)
                        .sound(BlockSound.STONE)
                        .item(ItemDefinition.builder()
                                .creativeTab(CreativeTab.BUILDING_BLOCKS)
                                .build())
                        .build());

        // High priority: protection decides before other mods see the break.
        events.register(BlockEvents.BREAK, EventPriority.HIGH, (world, pos, block, player) -> {
            if (block.equals(WARDED_BLOCK) && !player.mainHandItem().equals(NativeShowcaseMod.SPARK)) {
                return EventResult.DENY;
            }
            return EventResult.PASS;
        });
        events.register(
                BlockEvents.PLACE,
                (world, pos, block, player) -> onWard(world, pos) ? EventResult.DENY : EventResult.PASS);
        events.register(BlockEvents.PLACED, (world, pos, block, player) -> PLACED_AT.add(pos));
        events.register(
                InteractionEvents.USE_BLOCK,
                (player, hand, stack, pos, face) ->
                        onWard(player.world(), pos) ? InteractionResult.FAIL : InteractionResult.PASS);
        events.register(
                InteractionEvents.USE_ITEM,
                (player, hand, stack) ->
                        stack.customName().filter(SEALED::equals).isPresent()
                                ? InteractionResult.FAIL
                                : InteractionResult.PASS);
        events.register(
                InteractionEvents.USE_ENTITY,
                (player, hand, stack, target) ->
                        onWard(target.world(), target.blockPos()) ? InteractionResult.FAIL : InteractionResult.PASS);
        events.register(
                InteractionEvents.ATTACK_ENTITY,
                (player, target) -> onWard(target.world(), target.blockPos()) ? EventResult.DENY : EventResult.PASS);
    }

    /** Whether the block below {@code pos} is in {@code #native_showcase:warded} (from {@link TagContent}). */
    private static boolean onWard(WorldView world, BlockPos pos) {
        return world.isBlockIn(pos.offset(0, -1, 0), TagContent.WARDED);
    }

    static void registerTests(GameTestRegistrar tests) {
        BlockPos ward = BlockPos.of(0, 1, 0);
        BlockPos onWard = BlockPos.of(0, 2, 0);
        BlockPos ground = BlockPos.of(2, 1, 0);
        BlockPos onGround = BlockPos.of(2, 2, 0);
        TestArea area = new TestArea(3, 3, 1);

        tests.register("break_event_cancels_breaking", 20, area, test -> {
            test.setBlock(ward, WARDED_BLOCK);
            PlayerView player = test.createMockServerPlayer();
            test.assertTrue(!test.breakBlock(player, ward), "an empty hand cannot break the warded block");
            test.assertBlock(ward, WARDED_BLOCK);
            player.give(NativeShowcaseMod.SPARK, 1);
            test.assertTrue(test.breakBlock(player, ward), "a spark breaks the warded block");
            test.assertBlock(ward, ResourceId.minecraft("air"));
            test.succeed();
        });
        tests.register("place_event_cancels_placing", 20, area, test -> {
            test.setBlock(ward, WARDED_BLOCK);
            test.setBlock(ground, ResourceId.minecraft("stone"));
            PlayerView player = test.createMockServerPlayer();
            player.give(NativeShowcaseMod.GLOW_BLOCK, 1);
            InteractionResult refused = test.interactBlock(player, ward, Direction.UP);
            test.assertTrue(refused == InteractionResult.FAIL, "placing on the ward returned " + refused);
            test.assertBlock(onWard, ResourceId.minecraft("air"));
            test.assertTrue(player.countItem(NativeShowcaseMod.GLOW_BLOCK) == 1, "the refused block is kept");
            InteractionResult placed = test.interactBlock(player, ground, Direction.UP);
            test.assertTrue(placed == InteractionResult.SUCCESS, "placing on stone returned " + placed);
            test.assertBlock(onGround, NativeShowcaseMod.GLOW_BLOCK);
            test.assertTrue(player.countItem(NativeShowcaseMod.GLOW_BLOCK) == 0, "the placed block is used up");
            test.assertTrue(PLACED_AT.contains(test.absolute(onGround)), "PLACED fired for the placed block");
            test.succeed();
        });
        tests.register("use_block_event_cancels_use", 20, area, test -> {
            test.setBlock(ward, WARDED_BLOCK);
            test.setBlock(onWard, ResourceId.minecraft("oak_trapdoor"));
            test.setBlock(onGround, ResourceId.minecraft("oak_trapdoor"));
            PlayerView player = test.createMockServerPlayer();
            InteractionResult refused = test.interactBlock(player, onWard, Direction.NORTH);
            test.assertTrue(refused == InteractionResult.FAIL, "using the warded trapdoor returned " + refused);
            test.assertTrue(!test.world().stateAt(test.absolute(onWard)).get(OPEN), "the warded trapdoor stays shut");
            InteractionResult used = test.interactBlock(player, onGround, Direction.NORTH);
            test.assertTrue(used == InteractionResult.SUCCESS, "using the other trapdoor returned " + used);
            test.assertTrue(test.world().stateAt(test.absolute(onGround)).get(OPEN), "the other trapdoor opened");
            test.succeed();
        });
        tests.register("use_item_event_cancels_use", test -> {
            PlayerView player = test.createMockServerPlayer();
            player.give(NativeShowcaseMod.STAR_FRUIT, 2);
            player.mainHandStack().setCustomName(SEALED);
            InteractionResult refused = test.interactItem(player);
            test.assertTrue(refused == InteractionResult.FAIL, "eating sealed fruit returned " + refused);
            player.mainHandStack().setCustomName(null);
            InteractionResult eating = test.interactItem(player);
            test.assertTrue(eating != InteractionResult.FAIL, "eating plain fruit returned " + eating);
            test.succeed();
        });
        tests.register("use_entity_event_cancels_use", 20, area, test -> {
            test.setBlock(ward, WARDED_BLOCK);
            test.setBlock(ground, ResourceId.minecraft("stone"));
            PlayerView player = test.createMockServerPlayer();
            player.give(ResourceId.minecraft("bucket"), 1);
            EntityView wardedCow = spawnOn(test, onWard, ResourceId.minecraft("cow"));
            InteractionResult refused = test.interactEntity(player, wardedCow);
            test.assertTrue(refused == InteractionResult.FAIL, "milking the warded cow returned " + refused);
            test.assertTrue(player.mainHandItem().equals(ResourceId.minecraft("bucket")), "bucket still empty");
            EntityView cow = spawnOn(test, onGround, ResourceId.minecraft("cow"));
            InteractionResult milked = test.interactEntity(player, cow);
            test.assertTrue(milked != InteractionResult.FAIL && milked != InteractionResult.PASS, "milked: " + milked);
            test.assertTrue(
                    player.countItem(ResourceId.minecraft("milk_bucket")) == 1,
                    "the other cow gave milk, hand holds " + player.mainHandItem());
            test.succeed();
        });
        tests.register("attack_entity_event_cancels_attack", 20, area, test -> {
            test.setBlock(ward, WARDED_BLOCK);
            test.setBlock(ground, ResourceId.minecraft("stone"));
            PlayerView player = test.createMockServerPlayer();
            LivingEntityView warded = (LivingEntityView) spawnOn(test, onWard, CreatureContent.GOLEM);
            test.attackEntity(player, warded);
            test.assertTrue(warded.health() == warded.maxHealth(), "the warded golem took no damage");
            LivingEntityView golem = (LivingEntityView) spawnOn(test, onGround, CreatureContent.GOLEM);
            test.attackEntity(player, golem);
            test.assertTrue(golem.health() < golem.maxHealth(), "the other golem was hurt: " + golem.health());
            test.succeed();
        });
    }

    private static EntityView spawnOn(
            dev.polymodloader.api.devtools.gametest.GameTestContext test, BlockPos relative, ResourceId type) {
        BlockPos at = test.absolute(relative);
        return test.world().spawnEntity(type, at.x() + 0.5, at.y(), at.z() + 0.5);
    }
}
