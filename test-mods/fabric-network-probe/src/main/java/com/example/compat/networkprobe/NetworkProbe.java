package com.example.compat.networkprobe;

import io.netty.buffer.ByteBuf;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.RecordComponent;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.configuration.ClientConfigurationPacketListener;
import net.minecraft.network.protocol.configuration.ClientboundFinishConfigurationPacket;
import net.minecraft.network.protocol.configuration.ClientboundSelectKnownPacks;
import net.minecraft.network.protocol.configuration.ConfigurationProtocols;
import net.minecraft.network.protocol.configuration.ServerboundFinishConfigurationPacket;
import net.minecraft.network.protocol.configuration.ServerboundSelectKnownPacks;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.network.protocol.login.ClientLoginPacketListener;
import net.minecraft.network.protocol.login.ClientboundCustomQueryPacket;
import net.minecraft.network.protocol.login.ClientboundLoginCompressionPacket;
import net.minecraft.network.protocol.login.ClientboundLoginDisconnectPacket;
import net.minecraft.network.protocol.login.ServerboundCustomQueryAnswerPacket;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import net.minecraft.network.protocol.login.ServerboundLoginAcknowledgedPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * A fake client that joins the server it runs in, over a memory channel, as the integrated server's own client does
 * (ADR-0026 40, 41): login, the configuration phase, where the server's mod loaders announce their channels ({@code
 * minecraft:register}), negotiate and run their configuration tasks, and the play phase. It answers keep-alives, pings,
 * login queries and the configuration tasks of vanilla, Fabric API and NeoForge as a client with the same mods does.
 * In the play phase it sends a payload of its own Fabric API channel ({@link FabricEcho}), which the server answers,
 * echoes the payload of the NeoForge test mod {@code neoforge_network_probe} back, which the server answers too, and
 * the server side of the probe sends it a Fabric payload inside a bundle. It records the custom payloads of both
 * phases and whether the server keeps the connection, and reports once the play phase has run for {@value
 * #PLAY_SECONDS} seconds, after {@value #GIVE_UP_SECONDS} seconds at the latest, or when the server disconnects it.
 * The headless check of the join that {@code corpusFabricNeoForgeClient} needs a window for.
 *
 * <p>Thread: {@link #tick} on the server thread; the listeners on the connection's event loop.
 */
public final class NetworkProbe {

    private static final long PLAY_SECONDS = 3;
    private static final long GIVE_UP_SECONDS = 40;
    private static final String PREFIX = "[fabric_network_probe] ";
    private static final String NAME = "PolyNetProbe";
    private static final String NEOFORGE_ECHO = "neoforge_network_probe:echo";

    private static final List<String> PAYLOADS = new CopyOnWriteArrayList<>();
    private static final List<String> PLAY_PAYLOADS = new CopyOnWriteArrayList<>();
    private static final Set<String> REGISTERED = new ConcurrentSkipListSet<>();
    private static final Set<String> UNHANDLED = new ConcurrentSkipListSet<>();
    private static final Set<String> ANSWERED = ConcurrentHashMap.newKeySet();
    private static final List<String> ANSWER_FAILURES = new CopyOnWriteArrayList<>();
    private static final List<String> SENT = new CopyOnWriteArrayList<>();
    private static volatile String disconnected;
    private static volatile boolean configuration;
    private static volatile long playStarted;
    private static volatile boolean fabricAnswered;
    private static volatile boolean neoForgeAnswered;
    private static volatile boolean bundled;
    private static volatile String setupFailure;
    private static boolean bundleSent;
    private static Connection connection;
    private static long started;
    private static int ticks = -1;

    /** Whether the probe has reported (the game test server waits for it before it stops). */
    public static boolean reported() {
        return ticks == -2;
    }

    private NetworkProbe() {}

    /** Called at the start of each server tick. */
    public static void tick(MinecraftServer server) {
        if (ticks == -1) {
            ticks = 0;
            started = System.nanoTime();
            registerFabricEcho();
            start(server);
            return;
        }
        if (ticks < 0) {
            return;
        }
        ticks++;
        if (connection != null && connection.isConnected()) {
            connection.tick();
        }
        if (playStarted != 0 && !bundleSent) {
            ServerPlayer player = server.getPlayerList().getPlayerByName(NAME);
            if (player != null) {
                bundleSent = true;
                // NeoForge filters a bundle's payloads when it writes the bundle (filterGameBundlePackets), and sends
                // a bundle it filtered down to one packet as that packet alone: two keep it a bundle.
                player.connection.send(new ClientboundBundlePacket(List.of(
                        new ClientboundCustomPayloadPacket(new FabricEcho("bundled 1")),
                        new ClientboundCustomPayloadPacket(new FabricEcho("bundled 2")))));
            }
        }
        long now = System.nanoTime();
        boolean played = playStarted != 0 && now - playStarted > PLAY_SECONDS * 1_000_000_000L;
        if (played || now - started > GIVE_UP_SECONDS * 1_000_000_000L || disconnected != null) {
            report(server);
            ticks = -2;
            if (connection != null && connection.isConnected()) {
                connection.disconnect(Component.literal("probe done"));
            }
        }
    }

    /**
     * Registers {@link FabricEcho} with Fabric API's networking both ways, and a server receiver that answers {@code
     * pong: <text>}, as a Fabric mod does in its initializer (reflectively: the probe compiles against Fabric Loader
     * only).
     */
    private static void registerFabricEcho() {
        try {
            for (String registry : List.of("SERVERBOUND_PLAY", "CLIENTBOUND_PLAY")) {
                Object types = fabricRegistry(registry);
                types.getClass()
                        .getMethod(
                                "register",
                                CustomPacketPayload.Type.class,
                                net.minecraft.network.codec.StreamCodec.class)
                        .invoke(types, FabricEcho.TYPE, FabricEcho.CODEC);
            }
            ClassLoader loader = NetworkProbe.class.getClassLoader();
            Class<?> networking =
                    Class.forName("net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking", true, loader);
            Class<?> handlerType = Class.forName(
                    "net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking$PlayPayloadHandler", true, loader);
            Class<?> contextType =
                    Class.forName("net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking$Context", true, loader);
            Class<?> senderType = Class.forName("net.fabricmc.fabric.api.networking.v1.PacketSender", true, loader);
            Method responseSender = contextType.getMethod("responseSender");
            Method sendPacket = senderType.getMethod("sendPacket", CustomPacketPayload.class);
            InvocationHandler receiver = (proxy, method, arguments) -> switch (method.getName()) {
                case "receive" -> {
                    FabricEcho echo = (FabricEcho) arguments[0];
                    System.out.println(PREFIX + "the server received Fabric echo \"" + echo.text() + "\"");
                    sendPacket.invoke(responseSender.invoke(arguments[1]), new FabricEcho("pong: " + echo.text()));
                    yield null;
                }
                case "toString" -> "fabric_network_probe echo receiver";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == arguments[0];
                default -> null;
            };
            Object handler = Proxy.newProxyInstance(loader, new Class<?>[] {handlerType}, receiver);
            networking
                    .getMethod("registerGlobalReceiver", CustomPacketPayload.Type.class, handlerType)
                    .invoke(null, FabricEcho.TYPE, handler);
        } catch (ReflectiveOperationException | RuntimeException e) {
            setupFailure = "could not register the Fabric echo channel: " + e;
        }
    }

    private static void start(MinecraftServer server) {
        try {
            SocketAddress address = server.getConnection().startMemoryChannel();
            connection = Connection.connectToLocalServer(address);
            connection.initiateServerboundPlayConnection(
                    "localhost", 25565, listener(ClientLoginPacketListener.class, new Login(server)));
            connection.send(
                    new ServerboundHelloPacket(NAME, UUID.nameUUIDFromBytes(NAME.getBytes(StandardCharsets.UTF_8))));
            System.out.println(PREFIX + "fake client connecting over a memory channel");
        } catch (RuntimeException e) {
            disconnected = "could not connect: " + e;
        }
    }

    private static void report(MinecraftServer server) {
        System.out.println(PREFIX + "reached the configuration phase: " + configuration);
        System.out.println(PREFIX + "clientbound custom payloads: " + PAYLOADS);
        System.out.println(PREFIX + "serverbound custom payloads sent: " + SENT);
        System.out.println(PREFIX + "answers that could not be made: " + ANSWER_FAILURES);
        System.out.println(PREFIX + "packets without a handler: " + UNHANDLED);
        if (setupFailure != null) {
            System.out.println(PREFIX + setupFailure);
        }
        System.out.println(
                PREFIX + "channels the server registered (minecraft:register): " + new TreeSet<>(REGISTERED));
        System.out.println(PREFIX + "the server registered Fabric channels: "
                + REGISTERED.stream().anyMatch(channel -> channel.startsWith("fabric:")));
        if (playStarted == 0) {
            System.out.println(PREFIX + "configuration task the server waits for: " + configurationTask(server));
        }
        System.out.println(PREFIX + "reached the play phase: " + (playStarted != 0));
        System.out.println(PREFIX + "play-phase custom payloads: " + PLAY_PAYLOADS);
        System.out.println(PREFIX + "the server answered Fabric's play payload: " + fabricAnswered);
        System.out.println(PREFIX + "the server answered NeoForge's play payload: " + neoForgeAnswered);
        System.out.println(PREFIX + "a Fabric payload in a bundle arrived: " + bundled);
        if (disconnected == null && (connection == null || !connection.isConnected())) {
            disconnected = connection == null || connection.getDisconnectionDetails() == null
                    ? "the connection closed"
                    : connection.getDisconnectionDetails().reason().getString();
        }
        System.out.println(PREFIX
                + (disconnected == null
                        ? "the server kept the connection"
                        : "the server disconnected the probe: " + disconnected));
    }

    /** The task the server's configuration listener of the probe runs, read for the report. */
    private static String configurationTask(MinecraftServer server) {
        try {
            for (Connection candidate : server.getConnection().getConnections()) {
                Object listener = candidate.getPacketListener();
                if (listener == null || !listener.getClass().getSimpleName().contains("Configuration")) {
                    continue;
                }
                for (Class<?> type = listener.getClass(); type != null; type = type.getSuperclass()) {
                    for (java.lang.reflect.Field field : type.getDeclaredFields()) {
                        if (field.getName().equals("currentTask")) {
                            field.setAccessible(true);
                            return String.valueOf(field.get(listener));
                        }
                    }
                }
            }
            return "none found";
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "unreadable: " + e;
        }
    }

    /** The login phase: offline login, then the configuration phase, as the vanilla client does. */
    private static final class Login {

        private final MinecraftServer server;

        Login(MinecraftServer server) {
            this.server = server;
        }

        @SuppressWarnings("unused") // called reflectively by the listener proxy
        public void handleLoginFinished(Object packet) {
            connection.setupInboundProtocol(
                    ConfigurationProtocols.CLIENTBOUND,
                    listener(ClientConfigurationPacketListener.class, new Configuration(server)));
            connection.send(ServerboundLoginAcknowledgedPacket.INSTANCE);
            connection.setupOutboundProtocol(ConfigurationProtocols.SERVERBOUND);
            configuration = true;
        }

        @SuppressWarnings("unused")
        public void handleCompression(ClientboundLoginCompressionPacket packet) {
            connection.setupCompression(packet.getCompressionThreshold(), false);
        }

        @SuppressWarnings("unused")
        public void handleCustomQuery(ClientboundCustomQueryPacket packet) {
            connection.send(new ServerboundCustomQueryAnswerPacket(packet.transactionId(), null));
        }

        @SuppressWarnings("unused")
        public void handleDisconnect(ClientboundLoginDisconnectPacket packet) {
            disconnected = "login: " + packet.reason().getString();
        }
    }

    /**
     * The configuration phase: records the payloads, answers the configuration tasks, keep-alives and pings, and goes
     * on to the play phase as the vanilla client does, with the server's registries (the client has the same game).
     */
    private static final class Configuration {

        private final MinecraftServer server;

        Configuration(MinecraftServer server) {
            this.server = server;
        }

        @SuppressWarnings("unused")
        public void handleCustomPayload(ClientboundCustomPayloadPacket packet) {
            CustomPacketPayload payload = packet.payload();
            String id = payload.type().id().toString();
            PAYLOADS.add(id + " (" + payload.getClass().getSimpleName() + ")");
            if (payload.getClass().getSimpleName().equals("ModdedNetworkSetupFailedPayload")
                    || payload.getClass().getSimpleName().equals("ModdedNetworkQueryPayload")) {
                System.out.println(PREFIX + "NeoForge payload: " + payload);
            }
            if (id.equals("minecraft:register")) {
                REGISTERED.addAll(channels(payload));
            }
            answer(id, payload);
        }

        @SuppressWarnings("unused")
        public void handleSelectKnownPacks(ClientboundSelectKnownPacks packet) {
            connection.send(new ServerboundSelectKnownPacks(packet.knownPacks()));
        }

        @SuppressWarnings("unused")
        public void handleConfigurationFinished(ClientboundFinishConfigurationPacket packet) {
            try {
                Function<ByteBuf, RegistryFriendlyByteBuf> decorator = decorator(server.registryAccess());
                connection.setupInboundProtocol(
                        GameProtocols.CLIENTBOUND_TEMPLATE.bind(decorator),
                        listener(ClientGamePacketListener.class, new Play()));
                connection.send(ServerboundFinishConfigurationPacket.INSTANCE);
                connection.setupOutboundProtocol(GameProtocols.SERVERBOUND_TEMPLATE.bind(decorator, () -> false));
            } catch (ReflectiveOperationException | RuntimeException e) {
                ANSWER_FAILURES.add("finish configuration: " + e);
            }
        }

        @SuppressWarnings("unused")
        public void handleKeepAlive(ClientboundKeepAlivePacket packet) {
            connection.send(new ServerboundKeepAlivePacket(packet.getId()));
        }

        @SuppressWarnings("unused")
        public void handlePing(ClientboundPingPacket packet) {
            connection.send(new ServerboundPongPacket(packet.getId()));
        }

        @SuppressWarnings("unused")
        public void handleDisconnect(ClientboundDisconnectPacket packet) {
            disconnected = "configuration: " + packet.reason().getString();
        }
    }

    /**
     * The play phase: answers Fabric API's play-phase channel registration, sends the probe's Fabric payload, echoes
     * NeoForge's test payload, and records what arrives, also inside bundles.
     */
    private static final class Play {

        @SuppressWarnings("unused")
        public void handleLogin(ClientboundLoginPacket packet) {
            playStarted = System.nanoTime();
            send(new FabricEcho("hello"));
        }

        @SuppressWarnings("unused")
        public void handleCustomPayload(ClientboundCustomPayloadPacket packet) {
            received(packet.payload(), "");
        }

        @SuppressWarnings("unused")
        public void handleBundlePacket(ClientboundBundlePacket bundle) {
            for (Packet<?> packet : bundle.subPackets()) {
                if (packet instanceof ClientboundCustomPayloadPacket custom) {
                    received(custom.payload(), "bundled ");
                }
            }
        }

        private static void received(CustomPacketPayload payload, String how) {
            String id = payload.type().id().toString();
            PLAY_PAYLOADS.add(how + id + " (" + payload.getClass().getSimpleName() + ")");
            try {
                if (id.equals("minecraft:register") && ANSWERED.add("play " + id)) {
                    REGISTERED.addAll(channels(payload));
                    send(withChannels(payload, fabricChannels("CLIENTBOUND_PLAY")));
                } else if (payload instanceof FabricEcho echo) {
                    fabricAnswered |= echo.text().equals("pong: hello");
                    bundled |= how.equals("bundled ") && echo.text().startsWith("bundled");
                } else if (id.equals(NEOFORGE_ECHO)) {
                    String text = String.valueOf(component(payload, "text"));
                    if (text.equals("hello")) {
                        send(payload);
                    }
                    neoForgeAnswered |= text.equals("pong: hello");
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                ANSWER_FAILURES.add("play " + id + ": " + e);
            }
        }

        @SuppressWarnings("unused")
        public void handleKeepAlive(ClientboundKeepAlivePacket packet) {
            connection.send(new ServerboundKeepAlivePacket(packet.getId()));
        }

        @SuppressWarnings("unused")
        public void handlePing(ClientboundPingPacket packet) {
            connection.send(new ServerboundPongPacket(packet.getId()));
        }

        @SuppressWarnings("unused")
        public void handleDisconnect(ClientboundDisconnectPacket packet) {
            disconnected = "play: " + packet.reason().getString();
        }
    }

    /**
     * The play-phase buffer decorator the client uses: NeoForge's patch adds one that also takes the connection's type
     * ({@code ConnectionType.NEOFORGE} after NeoForge's negotiation), the vanilla one otherwise.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Function<ByteBuf, RegistryFriendlyByteBuf> decorator(RegistryAccess access)
            throws ReflectiveOperationException {
        for (Method method : RegistryFriendlyByteBuf.class.getMethods()) {
            if (method.getName().equals("decorator")
                    && method.getParameterCount() == 2
                    && method.getParameterTypes()[1].isEnum()) {
                Object type = Enum.valueOf((Class) method.getParameterTypes()[1], "NEOFORGE");
                // CreativeCore's client mixin (ClientConfigurationPacketListenerImplMixin) gives the play phase its
                // own buffer, which knows the connection, and its payload codecs cast to it.
                try {
                    Class<?> creative = Class.forName(
                            "team.creative.creativecore.common.network.CreativeByteBuf",
                            true,
                            RegistryFriendlyByteBuf.class.getClassLoader());
                    var constructor = creative.getConstructor(
                            ByteBuf.class, RegistryAccess.class, method.getParameterTypes()[1], Connection.class);
                    Connection own = connection;
                    return buffer -> {
                        try {
                            return (RegistryFriendlyByteBuf) constructor.newInstance(buffer, access, type, own);
                        } catch (ReflectiveOperationException e) {
                            throw new IllegalStateException("CreativeCore's buffer could not be made", e);
                        }
                    };
                } catch (ClassNotFoundException | NoSuchMethodException e) {
                    return (Function<ByteBuf, RegistryFriendlyByteBuf>) method.invoke(null, access, type);
                }
            }
        }
        return RegistryFriendlyByteBuf.decorator(access);
    }

    /**
     * What a client with the same mods answers, enough for the server's loaders to go through the configuration phase:
     * its own channel registration (the same payload class, listing the channels Fabric API can receive in this phase),
     * the common networking version and channels echoed back ({@code c:version}, {@code c:register}), NeoForge's
     * network query answered from NeoForge's registrations, and the configuration tasks of Fabric API (registry sync,
     * custom ingredients, data attachments) and NeoForge (frozen registries, data maps, extensible enums, feature
     * flags) acknowledged without anything of their own.
     */
    private static void answer(String id, CustomPacketPayload payload) {
        String type = payload.getClass().getSimpleName();
        try {
            if (id.equals("minecraft:register") && ANSWERED.add(id)) {
                send(withChannels(payload, fabricChannels("CLIENTBOUND_CONFIGURATION")));
            } else if (id.equals("c:version")) {
                send(payload);
            } else if (type.equals("ModdedNetworkQueryPayload")) {
                // As NeoForge's client answers: ModdedNetworkQueryPayload.fromRegistry(its payload registrations),
                // which are the server's here (the same mods).
                Class<?> registry = Class.forName(
                        "net.neoforged.neoforge.network.registration.NetworkRegistry",
                        true,
                        payload.getClass().getClassLoader());
                java.lang.reflect.Field registrations = registry.getDeclaredField("PAYLOAD_REGISTRATIONS");
                registrations.setAccessible(true);
                send((CustomPacketPayload)
                        payload.getClass().getMethod("fromRegistry", Map.class).invoke(null, registrations.get(null)));
            } else if (id.equals("c:register")) {
                send(withChannels(payload, fabricChannels("CLIENTBOUND_CONFIGURATION")));
            } else if (id.startsWith("fabric:registry/sync") && !id.endsWith("/complete")) {
                emptyFabricPayload("SERVERBOUND_CONFIGURATION", "fabric:registry/sync/complete")
                        .ifPresent(NetworkProbe::send);
            } else if (type.equals("ClientboundCustomIngredientPayload")) {
                send(sibling(
                        payload,
                        "net.fabricmc.fabric.impl.recipe.ingredient.ServerboundCustomIngredientPayload",
                        component(payload, "protocolVersion"),
                        Set.of()));
            } else if (type.equals("ClientboundRequestAcceptedAttachmentsPayload")) {
                send(sibling(
                        payload,
                        "net.fabricmc.fabric.impl.attachment.sync.serverbound.ServerboundAcceptedAttachmentsPayload",
                        Set.of()));
            } else if (type.equals("FrozenRegistrySyncCompletedPayload")) {
                send(payload);
            } else if (type.equals("KnownRegistryDataMapsPayload")) {
                send(sibling(
                        payload, "net.neoforged.neoforge.network.payload.KnownRegistryDataMapsReplyPayload", Map.of()));
            } else if (type.equals("ExtensibleEnumDataPayload")) {
                send(sibling(payload, "net.neoforged.neoforge.network.payload.ExtensibleEnumAcknowledgePayload"));
            } else if (type.equals("FeatureFlagDataPayload")) {
                send(sibling(payload, "net.neoforged.neoforge.network.payload.FeatureFlagAcknowledgePayload"));
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            ANSWER_FAILURES.add(id + ": " + e);
        }
    }

    private static void send(CustomPacketPayload payload) {
        SENT.add((playStarted != 0 ? "play " : "") + payload.type().id() + " ("
                + payload.getClass().getSimpleName() + ")");
        connection.send(new ServerboundCustomPayloadPacket(payload));
    }

    /**
     * The payload {@code className}, loaded with {@code payload}'s loader: its {@code INSTANCE}, or one made from
     * {@code arguments} with the constructor of that many parameters.
     */
    private static CustomPacketPayload sibling(CustomPacketPayload payload, String className, Object... arguments)
            throws ReflectiveOperationException {
        Class<?> type = Class.forName(className, true, payload.getClass().getClassLoader());
        if (arguments.length == 0) {
            return (CustomPacketPayload) type.getField("INSTANCE").get(null);
        }
        for (var constructor : type.getConstructors()) {
            if (constructor.getParameterCount() == arguments.length) {
                return (CustomPacketPayload) constructor.newInstance(arguments);
            }
        }
        throw new NoSuchMethodException(className + " has no constructor of " + arguments.length + " parameter(s)");
    }

    /** The record component {@code name} of {@code payload}. */
    private static Object component(CustomPacketPayload payload, String name) throws ReflectiveOperationException {
        for (RecordComponent component : payload.getClass().getRecordComponents()) {
            if (component.getName().equals(name)) {
                Method accessor = component.getAccessor();
                accessor.setAccessible(true);
                return accessor.invoke(payload);
            }
        }
        throw new NoSuchFieldException(payload.getClass().getName() + " has no record component " + name);
    }

    /** A payload of {@code payload}'s record class with the same components, its collections replaced by {@code channels}. */
    private static CustomPacketPayload withChannels(CustomPacketPayload payload, List<String> channels)
            throws ReflectiveOperationException {
        RecordComponent[] components = payload.getClass().getRecordComponents();
        Class<?>[] types = new Class<?>[components.length];
        Object[] values = new Object[components.length];
        for (int index = 0; index < components.length; index++) {
            types[index] = components[index].getType();
            Object value = components[index].getAccessor().invoke(payload);
            if (value instanceof Collection<?>) {
                Collection<Object> replaced =
                        Set.class.isAssignableFrom(types[index]) ? new java.util.HashSet<>() : new ArrayList<>();
                channels.forEach(channel -> replaced.add(Identifier.parse(channel)));
                value = replaced;
            }
            values[index] = value;
        }
        var constructor = payload.getClass().getDeclaredConstructor(types);
        constructor.setAccessible(true);
        return (CustomPacketPayload) constructor.newInstance(values);
    }

    /** The channel ids Fabric API's payload type registry {@code registry} (a field of PayloadTypeRegistryImpl) has. */
    private static List<String> fabricChannels(String registry) throws ReflectiveOperationException {
        Object types = fabricRegistry(registry);
        java.lang.reflect.Field field = types.getClass().getDeclaredField("packetTypes");
        field.setAccessible(true);
        List<String> channels = new ArrayList<>();
        ((Map<?, ?>) field.get(types)).keySet().forEach(key -> channels.add(key.toString()));
        return channels;
    }

    private static Object fabricRegistry(String registry) throws ReflectiveOperationException {
        Class<?> registries = Class.forName(
                "net.fabricmc.fabric.impl.networking.PayloadTypeRegistryImpl",
                true,
                NetworkProbe.class.getClassLoader());
        return registries.getField(registry).get(null);
    }

    /** A payload {@code id} of Fabric API's registry {@code registry}, decoded from no bytes (a payload without data). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static java.util.Optional<CustomPacketPayload> emptyFabricPayload(String registry, String id)
            throws ReflectiveOperationException {
        Object types = fabricRegistry(registry);
        Object typeAndCodec =
                types.getClass().getMethod("get", Identifier.class).invoke(types, Identifier.parse(id));
        if (typeAndCodec == null) {
            return java.util.Optional.empty();
        }
        net.minecraft.network.codec.StreamCodec codec = ((CustomPacketPayload.TypeAndCodec) typeAndCodec).codec();
        Object decoded = codec.decode(new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer()));
        return java.util.Optional.of((CustomPacketPayload) decoded);
    }

    /** The channel ids a register payload of either loader holds: its record component that is a collection. */
    private static List<String> channels(CustomPacketPayload payload) {
        List<String> channels = new ArrayList<>();
        RecordComponent[] components = payload.getClass().getRecordComponents();
        if (components == null) {
            return channels;
        }
        for (RecordComponent component : components) {
            try {
                Object value = component.getAccessor().invoke(payload);
                if (value instanceof Collection<?> collection) {
                    collection.forEach(channel -> channels.add(channel.toString()));
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                channels.add("<unreadable: " + e + ">");
            }
        }
        return channels;
    }

    /**
     * A listener of {@code type} that calls {@code handler}'s public method of the same name for each packet, answers
     * {@code flow()} with clientbound, {@code isAcceptingMessages()} with true, records a disconnection and the packets
     * it has no method for, and leaves the rest to the interface's default methods or does nothing.
     */
    private static <T> T listener(Class<T> type, Object handler) {
        InvocationHandler invocation = (proxy, method, arguments) -> {
            switch (method.getName()) {
                case "flow":
                    return PacketFlow.CLIENTBOUND;
                case "isAcceptingMessages":
                    return true;
                case "onDisconnect":
                    if (disconnected == null) {
                        disconnected =
                                ((DisconnectionDetails) arguments[0]).reason().getString();
                    }
                    return null;
                case "toString":
                    return "fabric_network_probe " + type.getSimpleName();
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == arguments[0];
                default:
                    break;
            }
            for (Method candidate : handler.getClass().getMethods()) {
                if (candidate.getName().equals(method.getName())
                        && candidate.getParameterCount() == method.getParameterCount()
                        && candidate.getDeclaringClass() == handler.getClass()) {
                    candidate.setAccessible(true);
                    return candidate.invoke(handler, arguments);
                }
            }
            if (method.isDefault()) {
                return InvocationHandler.invokeDefault(proxy, method, arguments);
            }
            if (method.getName().startsWith("handle") && type == ClientConfigurationPacketListener.class) {
                UNHANDLED.add(method.getName());
            }
            return null;
        };
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, invocation));
    }
}
