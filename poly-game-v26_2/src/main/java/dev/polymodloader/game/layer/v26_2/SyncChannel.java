package dev.polymodloader.game.layer.v26_2;

import dev.polymodloader.api.data.DataTree;
import dev.polymodloader.api.entity.PlayerView;
import dev.polymodloader.api.resource.ResourceId;
import dev.polymodloader.game.layer.v26_2.content.Nbt;
import dev.polymodloader.game.version.layer.ChannelEndpoint;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Poly Mod Loader's own channel {@code poly:sync}: the server sends players their synchronised entity data and
 * synchronised world storage; the client keeps read-only copies.
 */
public final class SyncChannel implements ChannelEndpoint {

    public static final ResourceId ID = ResourceId.of("poly", "sync");
    private static final Logger LOGGER = LoggerFactory.getLogger("Poly Mod Loader");
    private static final byte PLAYER_DATA = 0;
    private static final byte WORLD_STORAGE = 1;
    private static final byte ENTITY_DATA = 2;

    /** The local player's synchronised entity data, by key (client side). */
    private static final Map<ResourceId, DataTree> PLAYER = new ConcurrentHashMap<>();
    /** Entity data synchronised to trackers, by entity network ID and key (client side). */
    private static final Map<Integer, Map<ResourceId, DataTree>> ENTITIES = new ConcurrentHashMap<>();
    /** Synchronised world storage, by dimension and ID (client side). */
    private static final Map<String, DataTree> STORAGE = new ConcurrentHashMap<>();

    @Override
    public ResourceId id() {
        return ID;
    }

    @Override
    public String ownerModId() {
        return "poly";
    }

    @Override
    public void receiveOnServer(byte[] data, PlayerView sender) {
        // Clients never send on this channel; ignore anything that arrives.
    }

    @Override
    public void receiveOnClient(byte[] data) {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(data))) {
            byte kind = input.readByte();
            String key = input.readUTF();
            DataTree tree = Nbt.fromTag(NbtIo.read(input, NbtAccounter.defaultQuota()));
            if (kind == PLAYER_DATA) {
                PLAYER.put(parse(key), tree);
            } else if (kind == WORLD_STORAGE) {
                STORAGE.put(key, tree);
            } else if (kind == ENTITY_DATA) {
                int separator = key.indexOf('|');
                ENTITIES.computeIfAbsent(Integer.parseInt(key.substring(0, separator)), id -> new ConcurrentHashMap<>())
                        .put(parse(key.substring(separator + 1)), tree);
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("[Poly Mod Loader] POLY-NETWORK-004 ignored a malformed poly:sync payload", e);
        }
    }

    private static ResourceId parse(String id) {
        int colon = id.indexOf(':');
        return ResourceId.of(id.substring(0, colon), id.substring(colon + 1));
    }

    /** Sends {@code player} its data for {@code key}. */
    static void sendPlayerData(ServerPlayer player, ResourceId key, DataTree tree) {
        send(player, PLAYER_DATA, key.toString(), tree);
    }

    /** Sends {@code player} the data of an entity it can see. */
    static void sendEntityData(ServerPlayer player, int entityId, ResourceId key, DataTree tree) {
        send(player, ENTITY_DATA, entityId + "|" + key, tree);
    }

    /** The packet with an entity's data, for every player that sees it. */
    static ClientboundCustomPayloadPacket entityDataPacket(int entityId, ResourceId key, DataTree tree) {
        return packet(ENTITY_DATA, entityId + "|" + key, tree);
    }

    /** Set by the client when it joins: tells the local player apart from other players. */
    private static volatile java.util.function.Predicate<Object> localPlayer = entity -> false;

    public static void setLocalPlayerCheck(java.util.function.Predicate<Object> check) {
        localPlayer = check;
    }

    static boolean isLocalPlayer(Object entity) {
        return localPlayer.test(entity);
    }

    /** The client's copy of a visible entity's data. */
    static Optional<DataTree> entityData(int entityId, ResourceId key) {
        return Optional.ofNullable(ENTITIES.getOrDefault(entityId, Map.of()).get(key));
    }

    /** Sends {@code player} a synchronised world storage. */
    static void sendStorage(ServerPlayer player, ResourceId dimension, ResourceId id, DataTree tree) {
        send(player, WORLD_STORAGE, storageKey(dimension, id), tree);
    }

    private static void send(ServerPlayer player, byte kind, String key, DataTree tree) {
        if (player.connection != null) {
            player.connection.send(packet(kind, key, tree));
        }
    }

    private static ClientboundCustomPayloadPacket packet(byte kind, String key, DataTree tree) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeByte(kind);
            output.writeUTF(key);
            NbtIo.write(Nbt.toTag(tree), output);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new ClientboundCustomPayloadPacket(new Networking.PolyPayload(Networking.type(ID), bytes.toByteArray()));
    }

    private static String storageKey(ResourceId dimension, ResourceId id) {
        return dimension + "|" + id;
    }

    /** The client's copy of the local player's data. */
    static Optional<DataTree> playerData(ResourceId key) {
        return Optional.ofNullable(PLAYER.get(key));
    }

    /** The client's copy of a synchronised world storage. */
    static Optional<DataTree> storage(ResourceId dimension, ResourceId id) {
        return Optional.ofNullable(STORAGE.get(storageKey(dimension, id)));
    }

    /** Forgets everything when the client leaves a world. */
    public static void clearClientCopies() {
        PLAYER.clear();
        STORAGE.clear();
        ENTITIES.clear();
    }
}
