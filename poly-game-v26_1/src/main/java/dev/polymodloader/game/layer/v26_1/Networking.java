package dev.polymodloader.game.layer.v26_1;

import dev.polymodloader.api.entity.PlayerView;
import dev.polymodloader.api.resource.ResourceId;
import dev.polymodloader.api.runtime.GameSide;
import dev.polymodloader.game.version.layer.ChannelEndpoint;
import dev.polymodloader.game.version.layer.PayloadTransport;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Player;

/**
 * Carries Native API payloads as the game's custom payloads. Every declared channel is added to the
 * game's payload codecs, so the bytes travel as {@code <channel id> + payload} like any custom
 * payload; unknown channels on the other side are discarded by the game.
 */
public final class Networking {

    private static final ThreadLocal<Boolean> EXTENDING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private Networking() {}

    /** A Native API payload: the channel's type and the bytes the mod's codec produced. */
    public record PolyPayload(CustomPacketPayload.Type<PolyPayload> type, byte[] data) implements CustomPacketPayload {}

    static CustomPacketPayload.Type<PolyPayload> type(ResourceId channel) {
        return new CustomPacketPayload.Type<>(Views.id(channel));
    }

    private static StreamCodec<FriendlyByteBuf, PolyPayload> codec(CustomPacketPayload.Type<PolyPayload> type) {
        return StreamCodec.of((buffer, payload) -> buffer.writeBytes(payload.data()), buffer -> {
            byte[] data = new byte[buffer.readableBytes()];
            buffer.readBytes(data);
            return new PolyPayload(type, data);
        });
    }

    /**
     * Head hook of {@code CustomPacketPayload.codec(FallbackProvider, List)}: builds the same codec
     * with Poly Mod Loader's channels added to the list, by calling the method again with a flag that makes this
     * hook step aside.
     */
    static <B extends FriendlyByteBuf> StreamCodec<B, CustomPacketPayload> extendCodec(
            CustomPacketPayload.FallbackProvider<B> fallback,
            List<CustomPacketPayload.TypeAndCodec<? super B, ?>> types) {
        if (EXTENDING.get()
                || (LayerState.channels().isEmpty()
                        && LayerState.configurationExchanges().isEmpty())) {
            return null;
        }
        List<CustomPacketPayload.TypeAndCodec<? super B, ?>> extended = new ArrayList<>(types);
        List<ResourceId> ids = new ArrayList<>(LayerState.channels().keySet());
        ids.addAll(LayerState.configurationExchanges().keySet());
        for (ResourceId channel : ids) {
            CustomPacketPayload.Type<PolyPayload> type = type(channel);
            extended.add(new CustomPacketPayload.TypeAndCodec<>(type, codec(type)));
        }
        EXTENDING.set(Boolean.TRUE);
        try {
            return CustomPacketPayload.codec(fallback, extended);
        } finally {
            EXTENDING.set(Boolean.FALSE);
        }
    }

    /** Head hook of the server's custom payload handler; {@code true} when Poly Mod Loader consumed the payload. */
    static boolean receiveOnServer(ServerGamePacketListenerImpl listener, ServerboundCustomPayloadPacket packet) {
        if (!(packet.payload() instanceof PolyPayload payload)) {
            return false;
        }
        ChannelEndpoint endpoint =
                LayerState.channels().get(Views.id(payload.type().id()));
        if (endpoint != null) {
            ServerPlayer player = listener.player;
            player.level().getServer().execute(() -> endpoint.receiveOnServer(payload.data(), Views.player(player)));
        }
        return true;
    }

    /** Sends payloads for the Native API. */
    static final class Transport implements PayloadTransport {

        private final GameSide side;

        Transport(GameSide side) {
            this.side = side;
        }

        @Override
        public void sendToPlayer(PlayerView player, ResourceId channel, byte[] data) {
            Player gamePlayer = Views.gamePlayer(player);
            if (!(gamePlayer instanceof ServerPlayer serverPlayer)) {
                throw new IllegalStateException("sendToPlayer needs a server-side player, got " + player);
            }
            serverPlayer.connection.send(new ClientboundCustomPayloadPacket(new PolyPayload(type(channel), data)));
        }

        @Override
        public void sendToServer(ResourceId channel, byte[] data) {
            if (side != GameSide.CLIENT) {
                throw new IllegalStateException("sendToServer is only available on the client");
            }
            dev.polymodloader.game.layer.v26_1.client.ClientHooks.sendToServer(
                    new ServerboundCustomPayloadPacket(new PolyPayload(type(channel), data)));
        }
    }
}
