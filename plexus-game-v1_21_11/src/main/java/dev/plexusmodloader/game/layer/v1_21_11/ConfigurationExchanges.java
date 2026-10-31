package dev.plexusmodloader.game.layer.v1_21_11;

import com.mojang.authlib.GameProfile;
import dev.plexusmodloader.api.resource.ResourceId;
import dev.plexusmodloader.game.layer.v1_21_11.Networking.PlexusPayload;
import dev.plexusmodloader.game.version.layer.ConfigurationEndpoint;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs mods' configuration exchanges as the game's configuration tasks: one task per exchange,
 * after the registries are synchronized and before the player is placed in the world. A task sends
 * the request, waits for the client's answer or the timeout, and then lets the player in or refuses
 * the connection.
 */
final class ConfigurationExchanges {

    private static final Logger LOGGER = LoggerFactory.getLogger("Plexus Mod Loader");

    /** The task waiting for an answer on each configuring connection. */
    private static final Map<ServerConfigurationPacketListenerImpl, ExchangeTask> WAITING =
            Collections.synchronizedMap(new WeakHashMap<>());

    private ConfigurationExchanges() {}

    /** Adds one task per declared exchange to the connection's task queue. */
    @SuppressWarnings("unchecked")
    static void addTasks(ServerConfigurationPacketListenerImpl listener) {
        Map<ResourceId, ConfigurationEndpoint> exchanges = LayerState.configurationExchanges();
        if (exchanges.isEmpty()) {
            return;
        }
        Queue<ConfigurationTask> tasks;
        try {
            Field field = ServerConfigurationPacketListenerImpl.class.getDeclaredField("configurationTasks");
            field.setAccessible(true);
            tasks = (Queue<ConfigurationTask>) field.get(listener);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("PLEXUS-NETWORK-005 cannot reach the configuration task queue", e);
        }
        for (ConfigurationEndpoint endpoint : exchanges.values()) {
            tasks.add(new ExchangeTask(listener, endpoint));
        }
    }

    /**
     * Head of the common server payload handler, which the configuration phase uses; {@code true} when
     * the payload was an answer to a waiting exchange.
     */
    static boolean receive(ServerCommonPacketListenerImpl listener, ServerboundCustomPayloadPacket packet) {
        if (!(listener instanceof ServerConfigurationPacketListenerImpl configuration)
                || !(packet.payload() instanceof PlexusPayload payload)) {
            return false;
        }
        ExchangeTask task = WAITING.get(configuration);
        if (task == null || !task.endpoint.id().equals(Views.id(payload.type().id()))) {
            return false;
        }
        task.answer.set(payload.data());
        return true;
    }

    private static final class ExchangeTask implements ConfigurationTask {

        private final ServerConfigurationPacketListenerImpl listener;
        private final ConfigurationEndpoint endpoint;
        private final ConfigurationTask.Type type;
        private final AtomicReference<byte[]> answer = new AtomicReference<>();
        private boolean skipped;
        private int waited;

        ExchangeTask(ServerConfigurationPacketListenerImpl listener, ConfigurationEndpoint endpoint) {
            this.listener = listener;
            this.endpoint = endpoint;
            this.type = new ConfigurationTask.Type(endpoint.id().toString());
        }

        @Override
        public void start(Consumer<Packet<?>> connection) {
            GameProfile profile = listener.getOwner();
            Optional<byte[]> request = endpoint.request(profile.id(), profile.name());
            if (request.isEmpty()) {
                skipped = true;
                return;
            }
            WAITING.put(listener, this);
            connection.accept(new ClientboundCustomPayloadPacket(
                    new PlexusPayload(Networking.type(endpoint.id()), request.get())));
        }

        @Override
        public boolean tick() {
            if (skipped) {
                return true;
            }
            byte[] reply = answer.get();
            if (reply == null && ++waited < endpoint.timeoutTicks()) {
                return false;
            }
            WAITING.remove(listener);
            GameProfile profile = listener.getOwner();
            Optional<String> refusal = endpoint.check(profile.id(), profile.name(), Optional.ofNullable(reply));
            LOGGER.info(
                    "[Plexus Mod Loader] Configuration exchange {} with {}: {}, {}",
                    endpoint.id(),
                    profile.name(),
                    reply == null ? "no answer" : "answered",
                    refusal.isEmpty() ? "accepted" : "refused");
            if (refusal.isPresent()) {
                listener.disconnect(Component.literal(refusal.get()));
                return false;
            }
            return true;
        }

        @Override
        public ConfigurationTask.Type type() {
            return type;
        }
    }
}
