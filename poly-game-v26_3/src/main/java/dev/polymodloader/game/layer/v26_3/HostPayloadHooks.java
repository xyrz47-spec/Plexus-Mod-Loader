package dev.polymodloader.game.layer.v26_3;

import dev.polymodloader.compat.bridge.ForeignPayloads;
import io.netty.channel.ChannelHandlerContext;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientCommonPacketListener;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerCommonPacketListener;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Custom payloads of a host loader and another loader on one connection (ADR-0026 40, {@link ForeignPayloads}).
 *
 * <ul>
 *   <li>{@link #encode}: rule {@code poly:custom_payload_encoding} calls it instead of the payload codec's {@code
 *       encode} in {@code CustomPacketPayload}'s codec ({@code writeCap}), so a host's payload is written with the
 *       host's codec where another loader's codec owns its id (NeoForge's {@code MinecraftRegisterPayload} next to
 *       Fabric API's {@code minecraft:register} codec). Without a host, it is the plain call.
 *   <li>The others: NeoForge's channel checks ({@code NetworkRegistry.checkPacket} and {@code handleModdedPayload}, on
 *       both sides) start with them (rules of the NeoForge host) and leave other loaders' payloads to those loaders:
 *       NeoForge knows only its own channels and refused to send Fabric API's registry sync ({@code Payload ... may not
 *       be sent to the client!}).
 *   <li>{@link #keepOtherLoadersBundledPayloads}: NeoForge's filter of a bundle's packets ({@code
 *       NetworkRegistry.filterGameBundlePackets}) ends with it and keeps other loaders' payloads, which NeoForge drops
 *       without a word because it did not negotiate their channels (ADR-0026 41).
 * </ul>
 *
 * <p>Thread: the connection's network thread or the game thread, as the game calls them.
 */
public final class HostPayloadHooks {

    private static final Logger LOGGER = LoggerFactory.getLogger("Poly Mod Loader");
    private static final AtomicBoolean DROP_REPORTED = new AtomicBoolean();

    private HostPayloadHooks() {}

    /** Encodes {@code payload} into {@code buffer} with the codec {@link ForeignPayloads#encoderFor} picks. */
    @SuppressWarnings("unchecked")
    public static void encode(StreamCodec<Object, Object> codec, Object buffer, Object payload) {
        ((StreamCodec<Object, Object>) ForeignPayloads.encoderFor(payload, codec)).encode(buffer, payload);
    }

    /** Head of the server's send check: true leaves the check out for another loader's payload. */
    public static boolean otherLoadersClientboundPayload(Packet<?> packet, ServerCommonPacketListener listener) {
        return packet instanceof ClientboundCustomPayloadPacket custom
                && ForeignPayloads.foreign(custom.payload(), listener.protocol());
    }

    /** Head of the client's send check: true leaves the check out for another loader's payload. */
    public static boolean otherLoadersServerboundPayload(Packet<?> packet, ClientCommonPacketListener listener) {
        return packet instanceof ServerboundCustomPayloadPacket custom
                && ForeignPayloads.foreign(custom.payload(), listener.protocol());
    }

    /**
     * Head of the server's handling of a modded payload no other loader took: true drops another loader's payload, as
     * that loader does with a payload it has no receiver for, where NeoForge would disconnect.
     */
    public static boolean dropOtherLoadersPayload(
            ServerCommonPacketListener listener, ServerboundCustomPayloadPacket packet) {
        return dropped(packet.payload(), listener.protocol());
    }

    /** As {@link #dropOtherLoadersPayload}, on the client. */
    public static boolean dropOtherLoadersPayload(
            ClientCommonPacketListener listener, ClientboundCustomPayloadPacket packet) {
        return dropped(packet.payload(), listener.protocol());
    }

    /**
     * Before NeoForge's filter of a bundle's packets returns ({@code kept}, from {@code packets}, rule {@code
     * poly:neoforge_bundle_filter}): puts back the play-phase payloads of other loaders that it left out, in their
     * places. The filter only leaves packets out, in order; when {@code kept} is not such a subsequence of {@code
     * packets}, it is returned as it is.
     */
    public static List<Packet<?>> keepOtherLoadersBundledPayloads(
            ChannelHandlerContext context, Iterable<Packet<?>> packets, List<Packet<?>> kept) {
        List<Packet<?>> result = new ArrayList<>();
        int next = 0;
        boolean restored = false;
        for (Packet<?> packet : packets) {
            if (next < kept.size() && kept.get(next) == packet) {
                result.add(packet);
                next++;
            } else if (packet instanceof ClientboundCustomPayloadPacket custom
                    && ForeignPayloads.foreign(custom.payload(), ConnectionProtocol.PLAY)) {
                result.add(packet);
                restored = true;
            }
        }
        return restored && next == kept.size() ? result : kept;
    }

    private static boolean dropped(
            net.minecraft.network.protocol.common.custom.CustomPacketPayload payload, Object protocol) {
        if (!ForeignPayloads.foreign(payload, protocol)) {
            return false;
        }
        if (DROP_REPORTED.compareAndSet(false, true)) {
            LOGGER.info(
                    "[Poly Mod Loader] Payload {} of another loader had no receiver; it is dropped, as that loader"
                            + " does (ADR-0026 40)",
                    payload.type().id());
        }
        return true;
    }
}
