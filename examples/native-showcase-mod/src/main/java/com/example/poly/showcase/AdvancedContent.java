package com.example.poly.showcase;

import dev.polymodloader.api.content.AdvancementTrigger;
import dev.polymodloader.api.content.ContentRegistrar;
import dev.polymodloader.api.content.CreativeTab;
import dev.polymodloader.api.content.block.BlockBehavior;
import dev.polymodloader.api.content.block.BlockDefinition;
import dev.polymodloader.api.content.block.BlockSound;
import dev.polymodloader.api.content.block.BlockUse;
import dev.polymodloader.api.content.block.entity.BlockEntityDefinition;
import dev.polymodloader.api.content.block.entity.MenuLayout;
import dev.polymodloader.api.content.block.state.BlockProperty;
import dev.polymodloader.api.content.entity.ProjectileBehavior;
import dev.polymodloader.api.content.entity.ProjectileDefinition;
import dev.polymodloader.api.content.entity.ProjectileHit;
import dev.polymodloader.api.content.item.ComponentType;
import dev.polymodloader.api.content.item.ItemBehavior;
import dev.polymodloader.api.content.item.ItemComponentKey;
import dev.polymodloader.api.content.item.ItemDefinition;
import dev.polymodloader.api.content.item.ItemUse;
import dev.polymodloader.api.data.DataTree;
import dev.polymodloader.api.entity.LivingEntityView;
import dev.polymodloader.api.inventory.InventoryView;
import dev.polymodloader.api.inventory.ItemStackView;
import dev.polymodloader.api.resource.ResourceId;
import dev.polymodloader.api.world.InteractionResult;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Deeper content: a lamp with a named colour property and state-dependent light, a grinder with its
 * own menu layout and progress bar, an item data component, and a thrown projectile.
 */
final class AdvancedContent {

    static final ResourceId LAMP = ResourceId.of(NativeShowcaseMod.MOD_ID, "lamp");
    static final ResourceId GRINDER = ResourceId.of(NativeShowcaseMod.MOD_ID, "grinder");
    static final ResourceId SPARK_WAND = ResourceId.of(NativeShowcaseMod.MOD_ID, "spark_wand");
    static final ResourceId SPARK_BOLT = ResourceId.of(NativeShowcaseMod.MOD_ID, "spark_bolt");
    static final BlockProperty.ChoiceProperty COLOR = BlockProperty.choice("color", "red", "green", "blue");
    static final BlockProperty.BooleanProperty LIT = BlockProperty.bool("lit");
    static final int GRIND_TICKS = 40;
    static final AtomicInteger BOLT_HITS = new AtomicInteger();

    static ItemComponentKey<Integer> charge;
    static AdvancementTrigger usedWand;

    private AdvancedContent() {}

    static void declare(ContentRegistrar content) {
        content.block(
                "lamp",
                BlockDefinition.builder()
                        .strength(0.3f, 0.3f)
                        .sound(BlockSound.GLASS)
                        .properties(COLOR, LIT)
                        .lightLevel(state -> state.get(LIT) ? 15 : 0)
                        .behavior(new LampBehavior())
                        .item(ItemDefinition.builder()
                                .creativeTab(CreativeTab.REDSTONE_BLOCKS)
                                .build())
                        .build());
        content.block(
                "grinder",
                BlockDefinition.builder()
                        .strength(3.5f, 3.5f)
                        .sound(BlockSound.STONE)
                        .blockEntity(BlockEntityDefinition.builder()
                                .menu(
                                        MenuLayout.builder(176, 166)
                                                .slot(56, 35)
                                                .outputSlot(116, 35)
                                                .progressBar(
                                                        79, 34, MenuLayout.ProgressStyle.ARROW, "progress", "total")
                                                .build(),
                                        "container.native_showcase.grinder")
                                .ticker(AdvancedContent::grind)
                                .syncToClients()
                                .build())
                        .item(ItemDefinition.builder()
                                .creativeTab(CreativeTab.FUNCTIONAL_BLOCKS)
                                .build())
                        .build());
        charge = content.itemComponent("charge", ComponentType.INTEGER);
        usedWand = content.advancementTrigger("used_wand");
        content.projectile(
                "spark_bolt",
                ProjectileDefinition.builder(NativeShowcaseMod.SPARK)
                        .damage(2)
                        .behavior(new BoltBehavior())
                        .build());
        content.item(
                "spark_wand",
                ItemDefinition.builder()
                        .maxStackSize(1)
                        .creativeTab(CreativeTab.TOOLS_AND_UTILITIES)
                        .behavior(new WandBehavior())
                        .build());
    }

    /** Grinds one cobblestone into gravel every {@link #GRIND_TICKS} ticks. */
    private static void grind(dev.polymodloader.api.content.block.entity.BlockEntityView entity) {
        InventoryView inventory = entity.inventory().orElseThrow();
        DataTree data = entity.data();
        ItemStackView input = inventory.get(0);
        ItemStackView output = inventory.get(1);
        ResourceId gravel = ResourceId.minecraft("gravel");
        boolean canGrind = input.item().equals(ResourceId.minecraft("cobblestone"))
                && (output.isEmpty() || (output.item().equals(gravel) && output.count() < 64));
        if (!canGrind) {
            if (data.getInt("progress", 0) != 0) {
                data.putInt("progress", 0);
                entity.markChanged();
            }
            return;
        }
        int progress = data.getInt("progress", 0) + 1;
        if (progress >= GRIND_TICKS) {
            inventory.set(0, input.item(), input.count() - 1);
            inventory.set(1, gravel, output.count() + 1);
            progress = 0;
        }
        data.putInt("progress", progress).putInt("total", GRIND_TICKS);
        entity.markChanged();
    }

    /** Using the lamp switches it on and cycles its colour. */
    static final class LampBehavior implements BlockBehavior {
        @Override
        public InteractionResult onUse(BlockUse use) {
            if (!use.world().isClientSide()) {
                String color = use.world().stateAt(use.pos()).get(COLOR);
                String next = COLOR.values()
                        .get((COLOR.values().indexOf(color) + 1)
                                % COLOR.values().size());
                use.world().setProperty(use.pos(), COLOR, next);
                use.world().setProperty(use.pos(), LIT, true);
            }
            return InteractionResult.SUCCESS;
        }
    }

    /** The wand launches a spark bolt and counts its uses in a data component. */
    static final class WandBehavior implements ItemBehavior {
        @Override
        public InteractionResult onUse(ItemUse use) {
            if (!use.world().isClientSide()) {
                use.player().launchProjectile(SPARK_BOLT, 1.5f, 1.0f);
                int uses = use.stack().get(charge).orElse(0) + 1;
                use.stack().set(charge, uses);
                use.player().sendMessage("Spark wand used " + uses + " time(s)");
                usedWand.trigger(use.player());
            }
            return InteractionResult.SUCCESS;
        }
    }

    /** A spark bolt makes the entity it hits glow. */
    static final class BoltBehavior implements ProjectileBehavior {
        @Override
        public void onHitEntity(ProjectileHit hit) {
            BOLT_HITS.incrementAndGet();
            if (hit.entity().orElseThrow() instanceof LivingEntityView living) {
                living.addEffect(ResourceId.minecraft("glowing"), 100, 0);
            }
        }
    }
}
