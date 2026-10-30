package dev.polymodloader.game.layer.v26_3;

import dev.polymodloader.api.data.DataTree;
import dev.polymodloader.api.event.world.WorldEvents;
import dev.polymodloader.api.resource.ResourceId;
import dev.polymodloader.api.world.StorageOptions;
import dev.polymodloader.api.world.StoredData;
import dev.polymodloader.game.layer.v26_3.content.Nbt;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** World storage ({@link StoredData}) and per-dimension task scheduling. */
final class WorldServices {

    private static final Logger LOGGER = LoggerFactory.getLogger("Poly Mod Loader");
    /** Reserved key that holds the data version in saved storage. */
    static final String VERSION_KEY = "poly_version";

    private static final Map<ResourceId, SavedDataType<ModSavedData>> TYPES = new ConcurrentHashMap<>();
    private static final Map<ServerLevel, LevelTasks> TASKS = new WeakHashMap<>();

    private WorldServices() {}

    /** The options each storage ID was first used with; later calls must use the same. */
    private static final Map<ResourceId, StorageOptions> OPTIONS = new ConcurrentHashMap<>();
    /** Synchronised storages that exist, to send to joining players. */
    private static final Map<ServerLevel, Map<ResourceId, ModSavedData>> SYNCED = new WeakHashMap<>();

    static StoredData storage(ServerLevel level, ResourceId id) {
        return storage(level, id, StorageOptions.DEFAULT);
    }

    static StoredData storage(ServerLevel level, ResourceId id, StorageOptions options) {
        StorageOptions previous = OPTIONS.putIfAbsent(id, options);
        if (previous != null && !previous.equals(options)) {
            throw new IllegalArgumentException(
                    "World storage " + id + " was opened with other options before: " + previous);
        }
        SavedDataType<ModSavedData> type = TYPES.computeIfAbsent(
                id,
                key -> new SavedDataType<>(
                        Views.id(key),
                        ModSavedData::new,
                        CompoundTag.CODEC.xmap(
                                tag -> new ModSavedData(load(tag, key, OPTIONS.get(key))),
                                saved -> save(saved.tree, OPTIONS.get(key))),
                        // Only applied to data saved by older game versions; mod versions use
                        // StorageOptions.
                        DataFixTypes.SAVED_DATA_COMMAND_STORAGE));
        ModSavedData saved = level.getDataStorage().computeIfAbsent(type);
        if (saved.migrated) {
            saved.migrated = false;
            saved.setDirty();
        }
        if (options.syncToClients()) {
            synchronized (SYNCED) {
                SYNCED.computeIfAbsent(level, ignored -> new java.util.HashMap<>())
                        .put(id, saved);
            }
        }
        return new StoredData() {
            @Override
            public DataTree data() {
                return saved.tree;
            }

            @Override
            public void markChanged() {
                saved.setDirty();
                if (options.syncToClients()) {
                    for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
                        SyncChannel.sendStorage(
                                player, Views.id(level.dimension().identifier()), id, saved.tree);
                    }
                }
            }
        };
    }

    /** Reads saved storage, running the migration when it was saved with an older version. */
    static DataTree load(CompoundTag tag, ResourceId id, StorageOptions options) {
        CompoundTag copy = tag.copy();
        int version = copy.getIntOr(VERSION_KEY, 0);
        copy.remove(VERSION_KEY);
        DataTree tree = Nbt.fromTag(copy);
        StorageOptions current = options == null ? StorageOptions.DEFAULT : options;
        if (version < current.version() && current.migration().isPresent()) {
            current.migration().get().migrate(tree, version);
            LOGGER.info(
                    "[Poly Mod Loader] Migrated world storage {} from version {} to {}",
                    id,
                    version,
                    current.version());
        } else if (version > current.version()) {
            LOGGER.warn(
                    "[Poly Mod Loader] PLEXUS-STORAGE-001 world storage {} was saved by a newer version ({} > {}); kept as is",
                    id,
                    version,
                    current.version());
        }
        return tree;
    }

    /** Writes storage with its version. */
    static CompoundTag save(DataTree tree, StorageOptions options) {
        CompoundTag tag = Nbt.toTag(tree);
        tag.remove(VERSION_KEY);
        tag.putInt(VERSION_KEY, options == null ? 0 : options.version());
        return tag;
    }

    /** Sends a joining player every synchronised storage. */
    static void sendSynced(ServerPlayer player) {
        synchronized (SYNCED) {
            SYNCED.forEach((level, storages) -> storages.forEach((id, saved) ->
                    SyncChannel.sendStorage(player, Views.id(level.dimension().identifier()), id, saved.tree)));
        }
    }

    static void schedule(ServerLevel level, int ticks, Runnable task) {
        if (ticks < 0) {
            throw new IllegalArgumentException("ticks must be >= 0: " + ticks);
        }
        synchronized (TASKS) {
            LevelTasks tasks = TASKS.computeIfAbsent(level, ignored -> new LevelTasks());
            tasks.queue.add(new Task(tasks.ticks + ticks + 1, tasks.sequence++, task));
        }
    }

    /** Called at the start of every tick of {@code level}. */
    static void tick(ServerLevel level) {
        LevelTasks tasks;
        boolean first;
        synchronized (TASKS) {
            first = !TASKS.containsKey(level);
            tasks = TASKS.computeIfAbsent(level, ignored -> new LevelTasks());
        }
        if (first) {
            LayerState.context()
                    .ifPresent(context -> context.events()
                            .publish(WorldEvents.READY, listener -> listener.onWorldReady(Views.world(level))));
        }
        tasks.ticks++;
        while (!tasks.queue.isEmpty() && tasks.queue.peek().dueTick() <= tasks.ticks) {
            Task task = tasks.queue.poll();
            try {
                task.action().run();
            } catch (RuntimeException e) {
                LOGGER.error(
                        "[Poly Mod Loader] PLEXUS-SCHEDULE-001 a scheduled task failed in {}",
                        level.dimension().identifier(),
                        e);
            }
        }
    }

    private static final class LevelTasks {
        long ticks;
        long sequence;
        final PriorityQueue<Task> queue = new PriorityQueue<>((a, b) -> a.dueTick() != b.dueTick()
                ? Long.compare(a.dueTick(), b.dueTick())
                : Long.compare(a.sequence(), b.sequence()));
    }

    private record Task(long dueTick, long sequence, Runnable action) {}

    static final class ModSavedData extends SavedData {

        final DataTree tree;
        /** Set when loading ran a migration, so the upgraded data is saved. */
        boolean migrated;

        ModSavedData() {
            this(DataTree.create());
        }

        ModSavedData(DataTree tree) {
            this.tree = tree;
        }
    }
}
