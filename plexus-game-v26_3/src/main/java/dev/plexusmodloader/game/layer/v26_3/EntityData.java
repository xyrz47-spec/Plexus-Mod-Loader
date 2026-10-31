package dev.plexusmodloader.game.layer.v26_3;

import com.google.common.collect.MapMaker;
import dev.plexusmodloader.api.content.EntityDataKey;
import dev.plexusmodloader.api.data.DataTree;
import dev.plexusmodloader.api.resource.ResourceId;
import dev.plexusmodloader.api.world.StoredData;
import dev.plexusmodloader.game.layer.v26_3.content.Nbt;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Mod data attached to entities, kept beside the game's entity objects (weakly, so unloaded
 * entities are collected) and saved under {@code plexus_entity_data} in the entity's own save data.
 */
final class EntityData {

    private static final String KEY = "plexus_entity_data";
    /** Reserved key that holds a data version inside each saved tree. */
    private static final String VERSION_KEY = "plexus_version";

    // Weak identity keys: Entity.equals compares entity IDs, and a respawn gives the new player object
    // the old one's ID after its data was copied, so an equality-keyed map would mix the two up.
    private static final Map<Entity, Map<ResourceId, DataTree>> DATA =
            new MapMaker().weakKeys().makeMap();

    private EntityData() {}

    private static List<EntityDataKey> keys() {
        return LayerState.context()
                .map(context -> context.declarations().entityData())
                .orElse(List.of());
    }

    private static EntityDataKey declared(ResourceId id) {
        return keys().stream().filter(key -> key.id().equals(id)).findFirst().orElse(null);
    }

    static StoredData of(Entity entity, EntityDataKey key) {
        if (declared(key.id()) == null) {
            throw new IllegalArgumentException("Entity data key " + key.id() + " is not declared");
        }
        if (entity.level().isClientSide()) {
            DataTree copy = SyncChannel.entityData(entity.getId(), key.id())
                    .or(() -> SyncChannel.isLocalPlayer(entity) ? SyncChannel.playerData(key.id()) : Optional.empty())
                    .orElseGet(DataTree::create);
            return new StoredData() {
                @Override
                public DataTree data() {
                    return copy;
                }

                @Override
                public void markChanged() {
                    throw new IllegalStateException("Entity data is changed on the server");
                }
            };
        }
        DataTree tree;
        synchronized (DATA) {
            tree = DATA.computeIfAbsent(entity, ignored -> new LinkedHashMap<>())
                    .computeIfAbsent(key.id(), ignored -> DataTree.create());
        }
        return new StoredData() {
            @Override
            public DataTree data() {
                return tree;
            }

            @Override
            public void markChanged() {
                if (key.options().syncToTrackers() && entity.level() instanceof ServerLevel level) {
                    level.getChunkSource()
                            .sendToTrackingPlayers(
                                    entity, SyncChannel.entityDataPacket(entity.getId(), key.id(), tree));
                }
                if (key.syncToOwner() && entity instanceof ServerPlayer player) {
                    SyncChannel.sendPlayerData(player, key.id(), tree);
                }
            }
        };
    }

    /** Before the return of {@code Entity.saveWithoutId}. */
    static void save(Entity entity, ValueOutput output) {
        Map<ResourceId, DataTree> data;
        synchronized (DATA) {
            Map<ResourceId, DataTree> stored = DATA.get(entity);
            data = stored == null ? Map.of() : new HashMap<>(stored);
        }
        if (data.isEmpty()) {
            return;
        }
        CompoundTag tag = new CompoundTag();
        data.forEach((id, tree) -> {
            CompoundTag saved = Nbt.toTag(tree);
            EntityDataKey key = declared(id);
            if (key != null && key.options().version() > 0) {
                saved.putInt(VERSION_KEY, key.options().version());
            }
            tag.put(id.toString(), saved);
        });
        output.store(KEY, CompoundTag.CODEC, tag);
    }

    /** Before the return of {@code Entity.load}. */
    static void load(Entity entity, ValueInput input) {
        input.read(KEY, CompoundTag.CODEC).ifPresent(tag -> {
            Map<ResourceId, DataTree> data = new LinkedHashMap<>();
            for (String id : tag.keySet()) {
                Tag value = tag.get(id);
                int colon = id.indexOf(':');
                if (value instanceof CompoundTag compound && colon > 0) {
                    ResourceId keyId = ResourceId.of(id.substring(0, colon), id.substring(colon + 1));
                    CompoundTag copy = compound.copy();
                    int version = copy.getIntOr(VERSION_KEY, 0);
                    copy.remove(VERSION_KEY);
                    DataTree tree = Nbt.fromTag(copy);
                    EntityDataKey key = declared(keyId);
                    if (key != null && version < key.options().version()) {
                        key.options().migration().ifPresent(migration -> migration.migrate(tree, version));
                    }
                    data.put(keyId, tree);
                }
            }
            DATA.put(entity, data);
        });
    }

    /** A respawned player keeps its data. */
    static void copy(Entity from, Entity to) {
        synchronized (DATA) {
            Map<ResourceId, DataTree> stored = DATA.get(from);
            if (stored != null) {
                Map<ResourceId, DataTree> copied = new LinkedHashMap<>();
                stored.forEach((id, tree) -> copied.put(id, tree.copy()));
                DATA.put(to, copied);
            }
        }
    }

    /** Sends a player who starts seeing {@code entity} its data synchronised to trackers. */
    static void sendToNewTracker(ServerPlayer player, Entity entity) {
        Map<ResourceId, DataTree> stored;
        synchronized (DATA) {
            Map<ResourceId, DataTree> current = DATA.get(entity);
            stored = current == null ? Map.of() : new HashMap<>(current);
        }
        stored.forEach((id, tree) -> {
            EntityDataKey key = declared(id);
            if (key != null && key.options().syncToTrackers()) {
                SyncChannel.sendEntityData(player, entity.getId(), id, tree);
            }
        });
    }

    /** Sends a joining player all of its synchronised data. */
    static void sendAll(ServerPlayer player) {
        for (EntityDataKey key : keys()) {
            if (key.syncToOwner()) {
                of(player, key).markChanged();
            }
        }
    }
}
