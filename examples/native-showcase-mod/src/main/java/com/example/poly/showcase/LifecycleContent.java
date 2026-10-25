package com.example.poly.showcase;

import dev.polymodloader.api.content.ContentRegistrar;
import dev.polymodloader.api.content.EntityDataKey;
import dev.polymodloader.api.devtools.gametest.GameTestRegistrar;
import dev.polymodloader.api.entity.PlayerView;
import dev.polymodloader.api.event.EventRegistry;
import dev.polymodloader.api.event.entity.EntityEvents;
import dev.polymodloader.api.event.lifecycle.LifecycleEvents;
import dev.polymodloader.api.event.player.PlayerEvents;
import dev.polymodloader.api.event.server.ServerEvents;
import dev.polymodloader.api.event.world.WorldEvents;
import dev.polymodloader.api.resource.ResourceId;
import dev.polymodloader.api.world.BlockPos;
import dev.polymodloader.api.world.StoredData;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Mod, server, world, respawn, and entity lifecycle events. Logs when every mod is initialised and the server and
 * overworld lifecycle,
 * counts each player's respawns in entity data (copied by {@code PlayerEvents.COPY}), and records
 * entities joining a world for the tests.
 */
final class LifecycleContent {

    private static final ResourceId OVERWORLD = ResourceId.minecraft("overworld");
    private static final ResourceId PLAYER = ResourceId.minecraft("player");
    private static final ResourceId COW = ResourceId.minecraft("cow");

    /** Player ID → {@code returnedFromEnd} of their last copy and respawn; for the tests. */
    static final Map<UUID, Boolean> COPIED = new ConcurrentHashMap<>();

    static final Map<UUID, Boolean> RESPAWNED = new ConcurrentHashMap<>();
    /** Player ID → deaths ({@code EntityEvents.DIED}); for the tests. */
    static final Map<UUID, Integer> DEATHS = new ConcurrentHashMap<>();

    private static final ResourceId KILL = ResourceId.minecraft("generic_kill");
    /** How many entities of each type joined a world since the game started; for the tests. */
    static final Map<ResourceId, AtomicInteger> JOINED_WORLD = new ConcurrentHashMap<>();

    static EntityDataKey respawns;

    private LifecycleContent() {}

    static void declare(ContentRegistrar content, EventRegistry events) {
        respawns = content.entityData("respawns", false);

        // After every mod of every loader ran its initialisers (Fabric mods run theirs inside Minecraft's start).
        events.register(
                LifecycleEvents.MODS_INITIALIZED,
                () -> NativeShowcaseMod.LOGGER.info("[{}] Every mod is initialised", NativeShowcaseMod.MOD_ID));

        events.register(
                ServerEvents.STARTING,
                server -> NativeShowcaseMod.LOGGER.info(
                        "[{}] Server starting (dedicated={})", NativeShowcaseMod.MOD_ID, server.dedicated()));
        events.register(
                ServerEvents.STOPPED,
                server -> NativeShowcaseMod.LOGGER.info(
                        "[{}] Server stopped after {} ticks", NativeShowcaseMod.MOD_ID, server.tickCount()));
        events.register(WorldEvents.LOADED, world -> {
            if (world.dimension().equals(OVERWORLD)) {
                NativeShowcaseMod.LOGGER.info("[{}] Overworld loaded", NativeShowcaseMod.MOD_ID);
            }
        });
        events.register(WorldEvents.SAVING, world -> {
            if (world.dimension().equals(OVERWORLD)) {
                NativeShowcaseMod.LOGGER.info("[{}] Overworld saving", NativeShowcaseMod.MOD_ID);
            }
        });
        events.register(WorldEvents.UNLOADED, world -> {
            if (world.dimension().equals(OVERWORLD)) {
                NativeShowcaseMod.LOGGER.info("[{}] Overworld unloaded", NativeShowcaseMod.MOD_ID);
            }
        });
        events.register(PlayerEvents.COPY, (oldPlayer, newPlayer, returnedFromEnd) -> {
            // Entity data was copied already; count the respawn on the new player object.
            StoredData data = newPlayer.data(respawns);
            data.data().putInt("count", data.data().getInt("count", 0) + 1);
            data.markChanged();
            COPIED.put(newPlayer.uuid(), returnedFromEnd);
        });
        events.register(
                PlayerEvents.RESPAWNED, (player, returnedFromEnd) -> RESPAWNED.put(player.uuid(), returnedFromEnd));
        events.register(
                EntityEvents.JOINED_WORLD,
                (entity, world) -> JOINED_WORLD
                        .computeIfAbsent(entity.type(), type -> new AtomicInteger())
                        .incrementAndGet());
        events.register(EntityEvents.DIED, (entity, cause) -> {
            if (entity instanceof PlayerView player) {
                DEATHS.merge(player.uuid(), 1, Integer::sum);
            }
        });
    }

    static void registerTests(GameTestRegistrar tests) {
        tests.register("respawn_fires_copy_and_respawned", 40, test -> {
            int playersBefore = joined(PLAYER);
            PlayerView player = test.createConnectedMockPlayer();
            test.assertTrue(joined(PLAYER) > playersBefore, "the joining player fired JOINED_WORLD");
            // generic_kill (as /kill) bypasses the invulnerability of freshly joined players.
            player.hurt(KILL, 50);
            test.assertTrue(deaths(player.uuid()) == 1, "the player died once: " + deaths(player.uuid()));
            PlayerView respawned = test.respawn(player, false);
            test.assertTrue(respawned.uuid().equals(player.uuid()), "the respawned player keeps the ID");
            test.assertTrue(Boolean.FALSE.equals(COPIED.get(player.uuid())), "COPY fired after death");
            test.assertTrue(Boolean.FALSE.equals(RESPAWNED.get(player.uuid())), "RESPAWNED fired after death");
            int count = respawned.data(respawns).data().getInt("count", 0);
            test.assertTrue(count == 1, "the new player counted 1 respawn, found " + count);
            // The new player object takes over the old one's entity ID; its death still counts.
            respawned.hurt(KILL, 50);
            test.assertTrue(deaths(player.uuid()) == 2, "the respawned player died too: " + deaths(player.uuid()));
            PlayerView again = test.respawn(respawned, false);
            PlayerView fromEnd = test.respawn(again, true);
            test.assertTrue(Boolean.TRUE.equals(COPIED.get(player.uuid())), "COPY fired leaving the End");
            test.assertTrue(Boolean.TRUE.equals(RESPAWNED.get(player.uuid())), "RESPAWNED fired leaving the End");
            int kept = fromEnd.data(respawns).data().getInt("count", 0);
            test.assertTrue(kept == 3, "the count was copied and increased twice more, found " + kept);
            // Before the world ticks: a connected player would make far-away mobs of other tests despawn.
            test.disconnect(fromEnd);
            test.succeed();
        });
        tests.register("entity_joined_world_fires", 20, test -> {
            BlockPos at = test.absolute(BlockPos.of(0, 1, 0));
            int cowsBefore = joined(COW);
            test.world().spawnEntity(COW, at.x() + 0.5, at.y(), at.z() + 0.5);
            test.assertTrue(joined(COW) > cowsBefore, "the spawned cow fired JOINED_WORLD");
            test.succeed();
        });
    }

    private static int deaths(UUID player) {
        return DEATHS.getOrDefault(player, 0);
    }

    private static int joined(ResourceId type) {
        AtomicInteger count = JOINED_WORLD.get(type);
        return count == null ? 0 : count.get();
    }
}
