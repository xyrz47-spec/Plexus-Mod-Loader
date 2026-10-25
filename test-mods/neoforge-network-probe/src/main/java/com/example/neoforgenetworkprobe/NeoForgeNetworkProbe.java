package com.example.neoforgenetworkprobe;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NeoForge's side of the headless join check (ADR-0026 41, {@code corpusFabricNeoForgeJoin}): a play-phase payload
 * {@code neoforge_network_probe:echo}, registered as NeoForge mods do. The server sends {@code hello} to each player who
 * joins and answers each echo it receives with {@code pong: <text>}; Fabric's network probe, the fake client, echoes
 * {@code hello} back. A play-phase payload Fabric API does not take is what reached Fabric API's configuration-phase
 * handler under NeoForge ({@code Unknown addon}).
 *
 * <p>Thread: the handlers on the server thread, as NeoForge runs them by default.
 */
@Mod(NeoForgeNetworkProbe.MOD_ID)
public final class NeoForgeNetworkProbe {

    static final String MOD_ID = "neoforge_network_probe";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final String PREFIX = "[neoforge_network_probe] ";

    /** The probe's payload: one string. */
    public record Echo(String text) implements CustomPacketPayload {

        public static final Type<Echo> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MOD_ID, "echo"));
        public static final StreamCodec<ByteBuf, Echo> CODEC = ByteBufCodecs.STRING_UTF8.map(Echo::new, Echo::text);

        @Override
        public Type<Echo> type() {
            return TYPE;
        }
    }

    public NeoForgeNetworkProbe(IEventBus modBus) {
        modBus.addListener(NeoForgeNetworkProbe::registerPayloads);
        NeoForge.EVENT_BUS.addListener(NeoForgeNetworkProbe::playerJoined);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playBidirectional(Echo.TYPE, Echo.CODEC, NeoForgeNetworkProbe::received);
    }

    private static void received(Echo echo, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            LOGGER.info(
                    PREFIX + "the server received echo \"{}\" from {}",
                    echo.text(),
                    player.getName().getString());
            context.reply(new Echo("pong: " + echo.text()));
        }
    }

    private static void playerJoined(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, new Echo("hello"));
            LOGGER.info(PREFIX + "sent echo \"hello\" to {}", player.getName().getString());
        }
    }
}
