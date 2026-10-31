package com.example.mymod;

import dev.plexusmodloader.api.content.CreativeTab;
import dev.plexusmodloader.api.content.block.BlockDefinition;
import dev.plexusmodloader.api.content.block.BlockSound;
import dev.plexusmodloader.api.content.item.ItemDefinition;
import dev.plexusmodloader.api.content.item.ItemRarity;
import dev.plexusmodloader.api.entity.PlayerView;
import dev.plexusmodloader.api.event.server.ServerEvents;
import dev.plexusmodloader.api.lifecycle.CommonModInitializer;
import dev.plexusmodloader.api.lifecycle.ModContext;
import dev.plexusmodloader.api.resource.ResourceId;
import dev.plexusmodloader.api.world.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Entry point of the mod; listed under entrypoints.common in plexus.mod.json. */
public final class MyMod implements CommonModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("my_mod");

    @Override
    public void onInitializeCommon(ModContext context) {
        LOGGER.info("[{}] Hello from {} {}", context.modId(), context.modId(), context.modVersion());
        context.events()
                .register(
                        ServerEvents.STARTED,
                        server -> LOGGER.info("[{}] Server started at tick {}", context.modId(), server.tickCount()));

        // An item and a block. Their models, names and the block's drop come from plexusResources in
        // build.gradle.kts, so no JSON file is needed for them.
        ResourceId ruby = context.content()
                .item(
                        "ruby",
                        ItemDefinition.builder()
                                .rarity(ItemRarity.RARE)
                                .creativeTab(CreativeTab.INGREDIENTS)
                                .build())
                .id();
        ResourceId rubyBlock = context.content()
                .block(
                        "ruby_block",
                        BlockDefinition.builder()
                                .strength(5.0f, 6.0f)
                                .requiresCorrectTool()
                                .sound(BlockSound.METAL)
                                .item(ItemDefinition.builder()
                                        .creativeTab(CreativeTab.BUILDING_BLOCKS)
                                        .build())
                                .build())
                .id();

        // In-game tests; run them with `./gradlew gameTests -PplexusTests=my_mod:*`.
        context.gameTests().register("ruby_can_be_given", test -> {
            PlayerView player = test.createMockPlayer();
            player.give(ruby, 2);
            test.assertTrue(player.countItem(ruby) == 2, "the player holds 2 rubies");
            test.succeed();
        });
        context.gameTests().register("ruby_block_can_be_placed", test -> {
            BlockPos here = BlockPos.of(0, 1, 0);
            test.setBlock(here, rubyBlock);
            test.assertBlock(here, rubyBlock);
            test.succeed();
        });
    }
}
