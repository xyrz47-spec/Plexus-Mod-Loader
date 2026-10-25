package com.example.poly.showcase;

import dev.polymodloader.api.content.ContentRegistrar;
import dev.polymodloader.api.content.CreativeTab;
import dev.polymodloader.api.content.block.BlockBehavior;
import dev.polymodloader.api.content.block.BlockDefinition;
import dev.polymodloader.api.content.block.BlockSound;
import dev.polymodloader.api.content.block.BlockUse;
import dev.polymodloader.api.content.block.entity.BlockEntityDefinition;
import dev.polymodloader.api.content.block.state.BlockProperty;
import dev.polymodloader.api.content.item.ItemDefinition;
import dev.polymodloader.api.resource.ResourceId;
import dev.polymodloader.api.world.InteractionResult;

/**
 * Block states and block entities: a machine that faces the player, toggles {@code powered} when
 * used, and counts ticks while powered; and a crate with a 27-slot inventory.
 */
final class MachineContent {

    static final ResourceId MACHINE = ResourceId.of(NativeShowcaseMod.MOD_ID, "machine");
    static final ResourceId CRATE = ResourceId.of(NativeShowcaseMod.MOD_ID, "crate");
    static final BlockProperty.DirectionProperty FACING = BlockProperty.horizontalFacing("facing");
    static final BlockProperty.BooleanProperty POWERED = BlockProperty.bool("powered");

    private MachineContent() {}

    static void declare(ContentRegistrar content) {
        content.block(
                "machine",
                BlockDefinition.builder()
                        .strength(3.5f, 3.5f)
                        .sound(BlockSound.METAL)
                        .facesPlayerWhenPlaced(FACING)
                        .properties(POWERED)
                        .behavior(new MachineBehavior())
                        .blockEntity(BlockEntityDefinition.builder()
                                .ticker(entity -> {
                                    if (entity.world().stateAt(entity.pos()).get(POWERED)) {
                                        entity.data()
                                                .putInt("ticks", entity.data().getInt("ticks", 0) + 1);
                                        entity.markChanged();
                                    }
                                })
                                .syncToClients()
                                .build())
                        .item(ItemDefinition.builder()
                                .creativeTab(CreativeTab.FUNCTIONAL_BLOCKS)
                                .build())
                        .build());
        content.block(
                "crate",
                BlockDefinition.builder()
                        .strength(2.5f, 2.5f)
                        .sound(BlockSound.WOOD)
                        .blockEntity(BlockEntityDefinition.builder()
                                .inventory(3, "container.native_showcase.crate")
                                .build())
                        .item(ItemDefinition.builder()
                                .creativeTab(CreativeTab.FUNCTIONAL_BLOCKS)
                                .build())
                        .build());
    }

    /** Using the machine toggles it; the block entity counts ticks while it is powered. */
    static final class MachineBehavior implements BlockBehavior {
        @Override
        public InteractionResult onUse(BlockUse use) {
            if (!use.world().isClientSide()) {
                boolean powered = use.world().stateAt(use.pos()).get(POWERED);
                use.world().setProperty(use.pos(), POWERED, !powered);
                use.player().sendMessage("Machine " + (powered ? "off" : "on"));
            }
            return InteractionResult.SUCCESS;
        }
    }
}
