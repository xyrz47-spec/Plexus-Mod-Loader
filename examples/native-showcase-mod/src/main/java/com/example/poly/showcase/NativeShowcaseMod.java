package com.example.poly.showcase;

import dev.polymodloader.api.command.CommandArgumentType;
import dev.polymodloader.api.command.CommandNode;
import dev.polymodloader.api.content.ContentHandle;
import dev.polymodloader.api.content.CreativeTab;
import dev.polymodloader.api.content.CreativeTabDefinition;
import dev.polymodloader.api.content.block.BlockBehavior;
import dev.polymodloader.api.content.block.BlockDefinition;
import dev.polymodloader.api.content.block.BlockHandle;
import dev.polymodloader.api.content.block.BlockSound;
import dev.polymodloader.api.content.block.BlockUse;
import dev.polymodloader.api.content.item.FoodDefinition;
import dev.polymodloader.api.content.item.ItemBehavior;
import dev.polymodloader.api.content.item.ItemDefinition;
import dev.polymodloader.api.content.item.ItemHandle;
import dev.polymodloader.api.content.item.ItemRarity;
import dev.polymodloader.api.content.item.ItemUse;
import dev.polymodloader.api.entity.EntityView;
import dev.polymodloader.api.entity.MobView;
import dev.polymodloader.api.event.block.BlockEvents;
import dev.polymodloader.api.event.player.PlayerEvents;
import dev.polymodloader.api.event.world.WorldEvents;
import dev.polymodloader.api.lifecycle.CommonModInitializer;
import dev.polymodloader.api.lifecycle.ModContext;
import dev.polymodloader.api.network.ConfigurationExchange;
import dev.polymodloader.api.network.PayloadChannel;
import dev.polymodloader.api.network.PayloadCodec;
import dev.polymodloader.api.resource.ResourceId;
import dev.polymodloader.api.world.BlockPos;
import dev.polymodloader.api.world.InteractionResult;
import dev.polymodloader.api.world.StoredData;
import dev.polymodloader.api.worldgen.BiomeSelector;
import dev.polymodloader.api.worldgen.GenerationStepName;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Declares the showcase content and server-side behaviour. Runs on both sides. */
public final class NativeShowcaseMod implements CommonModInitializer {

    static final String MOD_ID = "native_showcase";
    static final ResourceId GLOW_BLOCK = ResourceId.of(MOD_ID, "glow_block");
    static final ResourceId SPARK = ResourceId.of(MOD_ID, "spark");
    static final ResourceId STAR_FRUIT = ResourceId.of(MOD_ID, "star_fruit");
    static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    static BlockHandle glowBlock;
    static ItemHandle spark;
    static PayloadChannel<String> greetings;
    static ConfigurationExchange<String> handshake;
    static ContentHandle showcaseTab;

    @Override
    public void onInitializeCommon(ModContext mod) {
        // First: the commands below use its permission nodes.
        OperationsContent.declare(mod);
        glowBlock = mod.content()
                .block(
                        "glow_block",
                        BlockDefinition.builder()
                                .strength(1.5f, 6.0f)
                                .requiresCorrectTool()
                                .sound(BlockSound.AMETHYST)
                                .lightLevel(12)
                                .behavior(new GlowBlockBehavior())
                                .item(ItemDefinition.builder()
                                        .creativeTab(CreativeTab.BUILDING_BLOCKS)
                                        .build())
                                .build());
        spark = mod.content()
                .item(
                        "spark",
                        ItemDefinition.builder()
                                .rarity(ItemRarity.UNCOMMON)
                                .creativeTab(CreativeTab.INGREDIENTS)
                                .behavior(new SparkBehavior())
                                .build());
        mod.content()
                .item(
                        "star_fruit",
                        ItemDefinition.builder()
                                .food(new FoodDefinition(4, 0.3f, true))
                                .creativeTab(CreativeTab.FOOD_AND_DRINKS)
                                .build());

        mod.commands()
                .register(CommandNode.literal("showcase")
                        .then(CommandNode.literal("info").executes(context -> {
                            context.source()
                                    .sendMessage("Native Showcase: glow block registered=" + glowBlock.isRegistered());
                            return 1;
                        }))
                        .then(CommandNode.literal("add")
                                .then(CommandNode.argument("a", CommandArgumentType.integer())
                                        .then(CommandNode.argument("b", CommandArgumentType.integer())
                                                .executes(
                                                        context -> context.getInteger("a") + context.getInteger("b")))))
                        .then(CommandNode.literal("place")
                                .requiresPermission(OperationsContent.place)
                                .then(CommandNode.argument("pos", CommandArgumentType.blockPos())
                                        .executes(context -> {
                                            context.source().world().setBlock(context.getBlockPos("pos"), GLOW_BLOCK);
                                            return 1;
                                        }))));

        greetings = mod.network().channel("greetings", PayloadCodec.string());
        // Before a player enters the world, the server asks for the client's showcase version.
        handshake = mod.network()
                .configurationExchange("handshake", PayloadCodec.string())
                .serverRequest((id, name) -> Optional.of("showcase version?"))
                .serverCheck((id, name, reply) -> {
                    LOGGER.info("[{}] Handshake with {}: {}", MOD_ID, name, reply.orElse("no answer"));
                    return reply.filter(version -> version.startsWith("showcase "))
                                    .isPresent()
                            ? Optional.empty()
                            : Optional.of("Install Native Showcase on your client to join.");
                });
        greetings.onServerReceive((message, sender) -> {
            LOGGER.info("[{}] Server received '{}' from {}", MOD_ID, message, sender.name());
            if (message.equals("thanks")) {
                sender.world().entitiesNear(sender.x(), sender.y(), sender.z(), 6).stream()
                        .filter(entity -> entity.type().equals(CreatureContent.GOLEM))
                        .findFirst()
                        .ifPresent(golem -> LOGGER.info(
                                "[{}] {} rides the golem: {}", MOD_ID, sender.name(), sender.startRiding(golem)));
            }
            if (message.equals("open grinder")) {
                // In front of the player, who is turned to look at it, so the client draws it.
                sender.setRotation(0, 30);
                BlockPos at = sender.blockPos().offset(0, 0, 2);
                MobView wisp = (MobView)
                        sender.world().spawnEntity(VisualContent.WISP, at.x() + 0.5, at.y() + 1.5, at.z() + 2.5);
                // Once the client tracks the wisp, every client that sees it plays the wave.
                sender.world().schedule(10, () -> wisp.playAnimation("wave"));
                sender.world().spawnParticles(VisualContent.SPARKLE, at.x() + 0.5, at.y() + 1.2, at.z() + 0.5, 12, 0.3);
                sender.world().setBlock(at, AdvancedContent.GRINDER);
                sender.world()
                        .blockEntityAt(at)
                        .flatMap(entity -> entity.inventory())
                        .ifPresent(inventory -> inventory.set(0, ResourceId.minecraft("cobblestone"), 4));
                LOGGER.info("[{}] Grinder menu opened: {}", MOD_ID, sender.openMenu(at));
            }
        });

        mod.events().register(PlayerEvents.JOINED, player -> {
            LOGGER.info("[{}] Player joined: {}", MOD_ID, player.name());
            LOGGER.info(
                    "[{}] Pocket dimension loaded: {}",
                    MOD_ID,
                    player.world()
                            .otherDimension(ResourceId.of(MOD_ID, "pocket"))
                            .isPresent());
            if (player.world().gameRule(CreatureContent.golemsGreetPlayers)) {
                EntityView golem =
                        player.world().spawnEntity(CreatureContent.GOLEM, player.x() + 2, player.y(), player.z());
                golem.data(OperationsContent.golemMood).data().putString("mood", "friendly");
                golem.data(OperationsContent.golemMood).markChanged();
                LOGGER.info("[{}] Spawned a golem friend next to {}", MOD_ID, player.name());
            }
            player.world().otherDimension(WorldContent.TERRACES).ifPresent(terraces -> {
                BlockPos corner = BlockPos.of(0, 65, 0);
                LOGGER.info(
                        "[{}] Terraces at 0 65 0: {} in {}",
                        MOD_ID,
                        terraces.blockAt(corner),
                        terraces.biomeAt(corner));
            });
            player.world()
                    .otherDimension(CodeWorldgenContent.WAVES)
                    .ifPresent(waves -> LOGGER.info(
                            "[{}] Waves terrain follows the density function: {}, surface {}",
                            MOD_ID,
                            CodeWorldgenContent.wavesFollowDensity(waves),
                            waves.blockAt(BlockPos.of(0, (int) Math.floor(CodeWorldgenContent.waveHeight(0)) - 1, 0))));
            greetings.sendToPlayer(player, "Welcome, " + player.name());
        });
        mod.events()
                .register(
                        BlockEvents.BROKEN,
                        (world, pos, block, player) ->
                                LOGGER.info("[{}] {} broke {} at {}", MOD_ID, player.name(), block, pos));

        MachineContent.declare(mod.content());
        CreatureContent.declare(mod.content(), mod.events());
        AdvancedContent.declare(mod.content());
        WorldContent.declare(mod.worldgen(), mod.events());
        CodeWorldgenContent.declare(mod.worldgen());
        VisualContent.declare(mod.content());
        MovementContent.declare(mod.content());
        InteractionContent.declare(mod.content(), mod.events());
        LifecycleContent.declare(mod.content(), mod.events());
        TagContent.declare(mod.content());
        // A tab of the mod's own, on an extra page of the creative screen; its title is
        // itemGroup.native_showcase.showcase in the language files.
        showcaseTab = mod.content()
                .creativeTab(
                        "showcase",
                        CreativeTabDefinition.builder()
                                .icon(GLOW_BLOCK)
                                .entries(GLOW_BLOCK, SPARK, STAR_FRUIT, InteractionContent.WARDED_BLOCK)
                                .entry(ResourceId.minecraft("amethyst_shard"))
                                .build());
        mod.worldgen()
                .addFeature(
                        BiomeSelector.tag(ResourceId.minecraft("is_overworld")),
                        GenerationStepName.UNDERGROUND_ORES,
                        ResourceId.of(MOD_ID, "glow_ore"));
        mod.events().register(WorldEvents.READY, world -> {
            if (world.dimension().equals(ResourceId.minecraft("overworld"))) {
                StoredData stats = world.storage(ResourceId.of(MOD_ID, "stats"));
                int loads = stats.data().getInt("loads", 0) + 1;
                stats.data().putInt("loads", loads);
                stats.markChanged();
                LOGGER.info("[{}] Overworld ready, load count {}", MOD_ID, loads);
            }
        });

        ShowcaseGameTests.register(mod.gameTests());
        MovementContent.registerTests(mod.gameTests());
        InteractionContent.registerTests(mod.gameTests());
        LifecycleContent.registerTests(mod.gameTests());
        TagContent.registerTests(mod.gameTests());
        CodeWorldgenContent.registerTests(mod.gameTests());
        try {
            java.nio.file.Path config = mod.configDirectory().resolve("showcase.properties");
            if (!java.nio.file.Files.exists(config)) {
                java.nio.file.Files.writeString(config, "greeting=Welcome\n");
            }
            LOGGER.info("[{}] Config file present: {}", MOD_ID, java.nio.file.Files.exists(config));
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        LOGGER.info(
                "[{}] Declared 5 blocks, 8 items, 2 mobs, 1 projectile, 1 particle, 1 effect, 2 game rules, 1 sound",
                MOD_ID);
    }

    /** Using the glow block hands out a spark. */
    static final class GlowBlockBehavior implements BlockBehavior {
        @Override
        public InteractionResult onUse(BlockUse use) {
            if (!use.world().isClientSide()) {
                use.player().give(SPARK, 1);
                use.player().sendMessage("The glow block gives you a spark");
            }
            return InteractionResult.SUCCESS;
        }
    }

    /** Using a spark reports where the player stands. */
    static final class SparkBehavior implements ItemBehavior {
        @Override
        public InteractionResult onUse(ItemUse use) {
            if (!use.world().isClientSide()) {
                use.player().sendMessage("Spark at " + use.player().blockPos());
            }
            return InteractionResult.SUCCESS;
        }
    }
}
