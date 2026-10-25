package com.example.crystalgarden;

import dev.polymodloader.api.content.CreativeTab;
import dev.polymodloader.api.content.EntityDataKey;
import dev.polymodloader.api.content.block.BlockDefinition;
import dev.polymodloader.api.content.block.BlockSound;
import dev.polymodloader.api.content.item.ItemDefinition;
import dev.polymodloader.api.content.item.ItemRarity;
import dev.polymodloader.api.lifecycle.CommonModInitializer;
import dev.polymodloader.api.lifecycle.ModContext;
import dev.polymodloader.api.network.ConfigurationExchange;
import dev.polymodloader.api.network.PayloadCodec;
import dev.polymodloader.api.permission.PermissionNode;
import dev.polymodloader.api.resource.ResourceId;
import dev.polymodloader.api.world.StorageOptions;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Crystal Garden: plant a crystal sprout, watch it grow, harvest crystals. Runs on both sides; listed under
 * {@code entrypoints.common} in poly.mod.json.
 */
public final class CrystalGardenMod implements CommonModInitializer {

    static final String MOD_ID = "crystal_garden";
    static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    static final ResourceId CRYSTAL = ResourceId.of(MOD_ID, "crystal");
    static final ResourceId CRYSTAL_BLOCK = ResourceId.of(MOD_ID, "crystal_block");
    static final ResourceId CRYSTAL_SPROUT = ResourceId.of(MOD_ID, "crystal_sprout");

    /** Garden totals of the world, copied to every client so the HUD can show them. */
    static final ResourceId GARDEN_STATS = ResourceId.of(MOD_ID, "garden_stats");

    static final StorageOptions GARDEN_STATS_OPTIONS = StorageOptions.DEFAULT.syncedToClients();

    /** Crystals each player harvested; saved with the player and sent to that player's client. */
    static EntityDataKey harvested;

    /** Who may ripen sprouts with {@code /garden grow}: operators (level 2) unless a permission mod says otherwise. */
    static PermissionNode growPermission;

    /** Before a player joins, the server checks that their client has Crystal Garden too. */
    static ConfigurationExchange<String> handshake;

    @Override
    public void onInitializeCommon(ModContext mod) {
        LOGGER.info("[{}] Planting Crystal Garden {}", MOD_ID, mod.modVersion());

        // 1. Content. Models, names and drops of the item and the block come from polyResources in
        //    build.gradle.kts; the sprout's growth stages use the block state file in src/main/resources.
        mod.content()
                .item(
                        "crystal",
                        ItemDefinition.builder()
                                .rarity(ItemRarity.UNCOMMON)
                                .creativeTab(CreativeTab.INGREDIENTS)
                                .build());
        mod.content()
                .block(
                        "crystal_block",
                        BlockDefinition.builder()
                                .strength(1.5f, 1.5f)
                                .requiresCorrectTool()
                                .sound(BlockSound.AMETHYST)
                                .lightLevel(6)
                                .item(ItemDefinition.builder()
                                        .creativeTab(CreativeTab.BUILDING_BLOCKS)
                                        .build())
                                .build());
        CrystalSprout.declare(mod.content());

        // 2. Saved data: per player (entity data) and per world (world storage, see CrystalSprout.harvest).
        harvested = mod.content().entityData("harvested", true);

        // 3. A permission node and a command that needs it.
        growPermission = mod.permissions().node("grow", 2);
        GardenCommands.register(mod.commands());

        // 4. A server-client check before joining. A client without this mod does not answer and is
        //    turned away with a clear message instead of failing later on unknown blocks.
        handshake = mod.network()
                .configurationExchange("handshake", PayloadCodec.string())
                .serverRequest((id, name) -> Optional.of("crystal_garden version?"))
                .serverCheck((id, name, reply) -> {
                    LOGGER.info("[{}] Handshake with {}: {}", MOD_ID, name, reply.orElse("no answer"));
                    return reply.filter(answer -> answer.startsWith(MOD_ID + " "))
                                    .isPresent()
                            ? Optional.empty()
                            : Optional.of("Install Crystal Garden on your client to join this server.");
                });

        // 5. In-game tests: ./gradlew gameTests "-PpolyTests=crystal_garden:*"
        GardenTests.register(mod.gameTests());
    }
}
