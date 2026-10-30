package dev.plexusmodloader.runtime.nativeapi;

import dev.plexusmodloader.api.entity.PlayerView;
import dev.plexusmodloader.api.network.ClientPayloadHandler;
import dev.plexusmodloader.api.network.PayloadChannel;
import dev.plexusmodloader.api.network.PayloadCodec;
import dev.plexusmodloader.api.network.ServerPayloadHandler;
import dev.plexusmodloader.api.resource.ResourceId;
import dev.plexusmodloader.game.version.layer.ChannelEndpoint;
import dev.plexusmodloader.game.version.layer.PayloadTransport;
import java.util.Objects;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A declared channel: encodes with the mod's codec and sends through the game layer's transport. */
final class PayloadChannelImpl<T> implements PayloadChannel<T>, ChannelEndpoint {

    /** Largest encoded payload a client may send; the game's serverbound custom payload limit. */
    static final int MAX_TO_SERVER = 32767;

    /** Largest encoded payload the server may send; the game's clientbound custom payload limit. */
    static final int MAX_TO_CLIENT = 1048576;

    private static final Logger LOGGER = LoggerFactory.getLogger("Poly Mod Loader");

    private final ResourceId id;
    private final String ownerModId;
    private final PayloadCodec<T> codec;
    private final Supplier<PayloadTransport> transport;
    private volatile ServerPayloadHandler<T> serverHandler;
    private volatile ClientPayloadHandler<T> clientHandler;

    PayloadChannelImpl(ResourceId id, String ownerModId, PayloadCodec<T> codec, Supplier<PayloadTransport> transport) {
        this.id = id;
        this.ownerModId = ownerModId;
        this.codec = codec;
        this.transport = transport;
    }

    @Override
    public ResourceId id() {
        return id;
    }

    @Override
    public String ownerModId() {
        return ownerModId;
    }

    @Override
    public void onServerReceive(ServerPayloadHandler<T> handler) {
        this.serverHandler = Objects.requireNonNull(handler, "handler");
    }

    @Override
    public void onClientReceive(ClientPayloadHandler<T> handler) {
        this.clientHandler = Objects.requireNonNull(handler, "handler");
    }

    @Override
    public void sendToPlayer(PlayerView player, T payload) {
        transport.get().sendToPlayer(player, id, encode(payload, MAX_TO_CLIENT));
    }

    @Override
    public void sendToServer(T payload) {
        transport.get().sendToServer(id, encode(payload, MAX_TO_SERVER));
    }

    @Override
    public void receiveOnServer(byte[] data, PlayerView sender) {
        ServerPayloadHandler<T> handler = serverHandler;
        if (handler == null) {
            LOGGER.debug("[Poly Mod Loader] Dropped payload on {}: no server handler", id);
            return;
        }
        decode(data).ifPresent(payload -> handler.receive(payload, sender));
    }

    @Override
    public void receiveOnClient(byte[] data) {
        ClientPayloadHandler<T> handler = clientHandler;
        if (handler == null) {
            LOGGER.debug("[Poly Mod Loader] Dropped payload on {}: no client handler", id);
            return;
        }
        decode(data).ifPresent(handler::receive);
    }

    private byte[] encode(T payload, int limit) {
        return PayloadBytes.encode(codec, payload, limit, id);
    }

    private java.util.Optional<T> decode(byte[] data) {
        return PayloadBytes.decode(codec, data, id, ownerModId);
    }
}
