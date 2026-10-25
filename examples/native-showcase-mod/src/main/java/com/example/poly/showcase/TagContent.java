package com.example.poly.showcase;

import dev.polymodloader.api.content.ContentRegistrar;
import dev.polymodloader.api.devtools.gametest.GameTestRegistrar;
import dev.polymodloader.api.entity.EntityView;
import dev.polymodloader.api.entity.PlayerView;
import dev.polymodloader.api.resource.ResourceId;
import dev.polymodloader.api.tag.TagKey;
import dev.polymodloader.api.tag.TagView;
import dev.polymodloader.api.world.BlockPos;

/**
 * Tags declared from code and read back: {@code #native_showcase:warded} (blocks that protect what
 * stands on them, used by {@link InteractionContent}), {@code #native_showcase:sparkly} (items), and
 * the warded block added to the game's {@code #minecraft:mineable/pickaxe} next to the glow block,
 * which comes from the mod jar's data file.
 */
final class TagContent {

    static final TagKey WARDED = TagKey.block(ResourceId.of(NativeShowcaseMod.MOD_ID, "warded"));
    static final TagKey SPARKLY = TagKey.item(ResourceId.of(NativeShowcaseMod.MOD_ID, "sparkly"));
    static final TagKey PICKAXE_MINEABLE = TagKey.block(ResourceId.minecraft("mineable/pickaxe"));
    static final TagKey SKELETONS = TagKey.entityType(ResourceId.minecraft("skeletons"));

    private TagContent() {}

    static void declare(ContentRegistrar content) {
        content.addToTag(WARDED, InteractionContent.WARDED_BLOCK);
        content.addToTag(SPARKLY, NativeShowcaseMod.SPARK, NativeShowcaseMod.GLOW_BLOCK);
        content.addToTag(PICKAXE_MINEABLE, InteractionContent.WARDED_BLOCK);
    }

    static void registerTests(GameTestRegistrar tests) {
        tests.register("tags_from_code_and_data", test -> {
            TagView tags = test.world().tags();
            test.assertTrue(tags.contains(SPARKLY, NativeShowcaseMod.SPARK), "spark is sparkly");
            test.assertTrue(!tags.contains(SPARKLY, NativeShowcaseMod.STAR_FRUIT), "star fruit is not sparkly");
            test.assertTrue(
                    tags.entries(SPARKLY)
                            .containsAll(java.util.List.of(NativeShowcaseMod.SPARK, NativeShowcaseMod.GLOW_BLOCK)),
                    "sparkly lists " + tags.entries(SPARKLY));
            test.assertTrue(
                    tags.contains(PICKAXE_MINEABLE, NativeShowcaseMod.GLOW_BLOCK), "the jar's tag file still applies");
            test.assertTrue(
                    tags.contains(PICKAXE_MINEABLE, InteractionContent.WARDED_BLOCK), "code added to a game tag");
            test.assertTrue(tags.contains(PICKAXE_MINEABLE, ResourceId.minecraft("stone")), "the game's entries stay");
            test.assertTrue(
                    tags.entries(TagKey.item(ResourceId.of("native_showcase", "unknown")))
                            .isEmpty(),
                    "unknown");
            test.succeed();
        });
        tests.register("is_in_checks_blocks_items_and_entities", test -> {
            BlockPos at = BlockPos.of(0, 1, 0);
            test.setBlock(at, InteractionContent.WARDED_BLOCK);
            BlockPos absolute = test.absolute(at);
            test.assertTrue(test.world().isBlockIn(absolute, WARDED), "the warded block is in #native_showcase:warded");
            test.assertTrue(test.world().isBlockIn(absolute, PICKAXE_MINEABLE), "and in #minecraft:mineable/pickaxe");
            boolean rejected = false;
            try {
                test.world().isBlockIn(absolute, SPARKLY);
            } catch (IllegalArgumentException expected) {
                rejected = true;
            }
            test.assertTrue(rejected, "an item tag is refused for a block");

            PlayerView player = test.createMockServerPlayer();
            player.give(NativeShowcaseMod.SPARK, 1);
            test.assertTrue(player.mainHandStack().isIn(SPARKLY), "the held spark is sparkly");

            EntityView skeleton = test.world()
                    .spawnEntity(
                            ResourceId.minecraft("skeleton"), absolute.x() + 0.5, absolute.y() + 1, absolute.z() + 0.5);
            EntityView cow = test.world()
                    .spawnEntity(ResourceId.minecraft("cow"), absolute.x() + 0.5, absolute.y() + 1, absolute.z() + 0.5);
            test.assertTrue(skeleton.isIn(SKELETONS), "a skeleton is in #minecraft:skeletons");
            test.assertTrue(!cow.isIn(SKELETONS), "a cow is not");
            test.succeed();
        });
    }
}
