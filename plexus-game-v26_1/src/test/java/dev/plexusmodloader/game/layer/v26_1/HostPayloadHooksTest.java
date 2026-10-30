package dev.plexusmodloader.game.layer.v26_1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import dev.plexusmodloader.compat.bridge.ForeignPayloads;
import java.util.List;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * ADR-0026 41: NeoForge's filter of a bundle's packets keeps only payloads of channels NeoForge negotiated, so a Fabric
 * API payload in a bundle was dropped without a word. The hook at its end puts other loaders' payloads back in place.
 */
class HostPayloadHooksTest {

    private record HostPayload() implements CustomPacketPayload {
        static final Type<HostPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("host_mod", "ping"));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private record OtherPayload() implements CustomPacketPayload {
        static final Type<OtherPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("fabric_mod", "ping"));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @BeforeEach
    void installHost() {
        ForeignPayloads.installHost(new ForeignPayloads.Host() {
            @Override
            public Object encoderFor(Object payload, Object chosen) {
                return chosen;
            }

            @Override
            public boolean owns(Object payload, Object protocol) {
                return payload instanceof HostPayload;
            }
        });
    }

    @Test
    void otherLoadersBundledPayloadsTheHostLeftOutAreKeptInPlace() {
        Packet<?> keepAlive = new ClientboundKeepAlivePacket(1);
        Packet<?> host = new ClientboundCustomPayloadPacket(new HostPayload());
        Packet<?> other = new ClientboundCustomPayloadPacket(new OtherPayload());
        Packet<?> unnegotiated = new ClientboundCustomPayloadPacket(new HostPayload());
        List<Packet<?>> bundle = List.of(keepAlive, other, host, unnegotiated);

        // NeoForge kept its negotiated payload and left out the other loader's and its own unnegotiated one.
        assertEquals(
                List.of(keepAlive, other, host),
                HostPayloadHooks.keepOtherLoadersBundledPayloads(null, bundle, List.of(keepAlive, host)));
        List<Packet<?>> all = List.of(keepAlive, host);
        assertSame(all, HostPayloadHooks.keepOtherLoadersBundledPayloads(null, all, all));
        // Not a subsequence: left as NeoForge returned it.
        List<Packet<?>> reordered = List.of(host, keepAlive);
        assertSame(reordered, HostPayloadHooks.keepOtherLoadersBundledPayloads(null, bundle, reordered));
    }
}
