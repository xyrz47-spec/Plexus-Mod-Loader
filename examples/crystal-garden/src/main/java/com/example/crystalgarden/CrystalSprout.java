package com.example.crystalgarden;

import dev.polymodloader.api.content.ContentRegistrar;
import dev.polymodloader.api.content.CreativeTab;
import dev.polymodloader.api.content.block.BlockBehavior;
import dev.polymodloader.api.content.block.BlockDefinition;
import dev.polymodloader.api.content.block.BlockShape;
import dev.polymodloader.api.content.block.BlockSound;
import dev.polymodloader.api.content.block.BlockUse;
import dev.polymodloader.api.content.block.entity.BlockEntityDefinition;
import dev.polymodloader.api.content.block.entity.BlockEntityView;
import dev.polymodloader.api.content.block.state.BlockProperty;
import dev.polymodloader.api.content.item.ItemDefinition;
import dev.polymodloader.api.data.DataTree;
import dev.polymodloader.api.entity.PlayerView;
import dev.polymodloader.api.resource.ResourceId;
import dev.polymodloader.api.world.BlockPos;
import dev.polymodloader.api.world.InteractionResult;
import dev.polymodloader.api.world.StoredData;
import dev.polymodloader.api.world.WorldView;

/**
 * The crystal sprout: a block with a growth stage (block state) and a block entity that counts ticks. A ripe
 * sprout gives crystals when used and starts again from stage 0.
 */
final class CrystalSprout {

    static final BlockProperty.IntegerProperty STAGE = BlockProperty.integer("stage", 0, 3);
    static final int RIPE = 3;
    /** Ticks per stage: 100 ticks are 5 seconds. */
    static final int TICKS_PER_STAGE = 100;

    static final int CRYSTALS_PER_HARVEST = 3;

    private CrystalSprout() {}

    static void declare(ContentRegistrar content) {
        content.block(
                "crystal_sprout",
                BlockDefinition.builder()
                        .strength(0.5f, 0.5f)
                        .sound(BlockSound.AMETHYST)
                        .noOcclusion()
                        .shape(state -> BlockShape.box(3, 0, 3, 13, 4 + 3 * state.get(STAGE), 13))
                        .lightLevel(state -> 2 + 3 * state.get(STAGE))
                        .properties(STAGE)
                        .behavior(new SproutBehavior())
                        .blockEntity(BlockEntityDefinition.builder()
                                .ticker(CrystalSprout::grow)
                                // The client draws the growth bar from this data (CrystalGardenClient).
                                .syncToClients()
                                .build())
                        .item(ItemDefinition.builder()
                                .creativeTab(CreativeTab.NATURAL_BLOCKS)
                                .build())
                        .build());
    }

    /** Runs every server tick for every sprout. */
    static void grow(BlockEntityView sprout) {
        WorldView world = sprout.world();
        int stage = world.stateAt(sprout.pos()).get(STAGE);
        if (stage >= RIPE) {
            return;
        }
        DataTree data = sprout.data();
        int growth = data.getInt("growth", 0) + 1;
        if (growth >= TICKS_PER_STAGE) {
            growth = 0;
            world.setProperty(sprout.pos(), STAGE, stage + 1);
        }
        data.putInt("growth", growth);
        // markChanged() saves the data and, with syncToClients(), sends it to clients. Once a second is
        // enough for a growth bar, and sending every tick would cost network traffic for nothing.
        if (growth % 20 == 0) {
            sprout.markChanged();
        }
    }

    /** Ripens the sprout at {@code pos} at once; false when there is no sprout. */
    static boolean ripen(WorldView world, BlockPos pos) {
        if (!world.blockAt(pos).equals(CrystalGardenMod.CRYSTAL_SPROUT)) {
            return false;
        }
        return world.setProperty(pos, STAGE, RIPE);
    }

    /** Gives the crystals of a ripe sprout to {@code player} and records the harvest. */
    static void harvest(WorldView world, BlockPos pos, PlayerView player) {
        world.setProperty(pos, STAGE, 0);
        player.give(CrystalGardenMod.CRYSTAL, CRYSTALS_PER_HARVEST);

        StoredData mine = player.data(CrystalGardenMod.harvested);
        mine.data().putInt("count", mine.data().getInt("count", 0) + CRYSTALS_PER_HARVEST);
        mine.markChanged();

        StoredData stats = world.storage(CrystalGardenMod.GARDEN_STATS, CrystalGardenMod.GARDEN_STATS_OPTIONS);
        stats.data().putInt("harvests", stats.data().getInt("harvests", 0) + 1);
        stats.markChanged();

        world.playSound(pos, ResourceId.minecraft("block.amethyst_block.chime"), 1.0f, 1.2f);
        world.spawnParticles(
                ResourceId.minecraft("happy_villager"), pos.x() + 0.5, pos.y() + 0.6, pos.z() + 0.5, 8, 0.3);
    }

    /** Using a ripe sprout harvests it; using a growing one tells how far along it is. */
    static final class SproutBehavior implements BlockBehavior {
        @Override
        public InteractionResult onUse(BlockUse use) {
            WorldView world = use.world();
            if (world.isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            int stage = world.stateAt(use.pos()).get(STAGE);
            if (stage >= RIPE) {
                harvest(world, use.pos(), use.player());
            } else {
                int growth = world.blockEntityAt(use.pos())
                        .map(entity -> entity.data().getInt("growth", 0))
                        .orElse(0);
                use.player()
                        .sendMessage("Crystal sprout: stage " + stage + "/" + RIPE + ", "
                                + growth * 100 / TICKS_PER_STAGE + "% to the next stage");
            }
            return InteractionResult.SUCCESS;
        }
    }
}
