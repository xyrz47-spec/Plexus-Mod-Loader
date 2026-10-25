package com.example.poly.showcase;

import dev.polymodloader.api.content.EntityDataKey;
import dev.polymodloader.api.content.EntityDataOptions;
import dev.polymodloader.api.event.player.PlayerEvents;
import dev.polymodloader.api.event.world.WorldEvents;
import dev.polymodloader.api.lifecycle.ModContext;
import dev.polymodloader.api.permission.PermissionNode;
import dev.polymodloader.api.permission.PermissionResult;
import dev.polymodloader.api.resource.ResourceId;
import dev.polymodloader.api.world.StorageOptions;
import dev.polymodloader.api.world.StoredData;
import java.util.Map;
import java.util.Optional;

/**
 * Data, synchronisation, and operations: a data reload listener, entity data synchronised to the
 * player, versioned world storage synchronised to clients, and permissions with a provider.
 */
final class OperationsContent {

    static final ResourceId WORLD_INFO = ResourceId.of(NativeShowcaseMod.MOD_ID, "world_info");
    /** Version 2 renamed {@code message} to {@code motd}. */
    static final StorageOptions WORLD_INFO_OPTIONS = StorageOptions.DEFAULT
            .withVersion(2, (data, fromVersion) -> {
                if (fromVersion < 2 && data.has("message")) {
                    data.putString("motd", data.getString("message", ""));
                    data.remove("message");
                }
            })
            .syncedToClients();

    static EntityDataKey visits;
    /** Seen by every client near the golem; version 2 renamed {@code feeling} to {@code mood}. */
    static EntityDataKey golemMood;

    static volatile Map<ResourceId, byte[]> banners = Map.of();
    static PermissionNode place;
    static PermissionNode secret;
    static volatile Map<ResourceId, String> messages = Map.of();

    private OperationsContent() {}

    static void declare(ModContext mod) {
        visits = mod.content().entityData("visits", true);
        golemMood = mod.content()
                .entityData(
                        "golem_mood",
                        EntityDataOptions.DEFAULT.syncedToTrackers().withVersion(2, (data, fromVersion) -> {
                            if (fromVersion < 2 && data.has("feeling")) {
                                data.putString("mood", data.getString("feeling", ""));
                                data.remove("feeling");
                            }
                        }));
        mod.resources().onDataFiles("native_showcase_banners", ".txt", files -> {
            banners = files;
            NativeShowcaseMod.LOGGER.info("[{}] Loaded {} banner file(s)", NativeShowcaseMod.MOD_ID, files.size());
        });
        // Refuses one made-up name, to show the login check.
        mod.events()
                .register(
                        PlayerEvents.LOGIN_CHECK,
                        (id, name) -> name.equals("blocked_by_showcase")
                                ? Optional.of("The Native Showcase does not let you in")
                                : Optional.empty());
        place = mod.permissions().node("place", 2);
        secret = mod.permissions().node("secret", 4);
        // A tiny permission provider: game test mock players may see the secret.
        mod.permissions()
                .provider((player, node) -> node.equals(secret) && player.name().startsWith("test-mock")
                        ? PermissionResult.ALLOW
                        : PermissionResult.UNDECIDED);
        mod.resources().onDataReload("native_showcase_messages", files -> {
            messages = files;
            NativeShowcaseMod.LOGGER.info("[{}] Loaded {} showcase message(s)", NativeShowcaseMod.MOD_ID, files.size());
        });
        mod.events().register(PlayerEvents.JOINED, player -> {
            StoredData data = player.data(visits);
            data.data().putInt("count", data.data().getInt("count", 0) + 1);
            data.markChanged();
        });
        mod.events().register(WorldEvents.READY, world -> {
            if (world.dimension().equals(ResourceId.minecraft("overworld"))) {
                StoredData info = world.storage(WORLD_INFO, WORLD_INFO_OPTIONS);
                info.data().putString("motd", "Welcome to the showcase");
                info.markChanged();
            }
        });
    }
}
