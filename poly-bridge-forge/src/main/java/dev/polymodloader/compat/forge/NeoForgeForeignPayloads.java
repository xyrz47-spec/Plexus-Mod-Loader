package dev.polymodloader.compat.forge;

import dev.polymodloader.api.runtime.GameSide;
import dev.polymodloader.compat.bridge.ForeignPayloads;
import dev.polymodloader.internal.transform.HeadHookResult;
import dev.polymodloader.internal.transform.InvokeHookAtHead;
import dev.polymodloader.internal.transform.InvokeHookBeforeReturn;
import dev.polymodloader.internal.transform.MethodTarget;
import dev.polymodloader.internal.transform.TransformFailurePolicy;
import dev.polymodloader.internal.transform.TransformRuleDefinition;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * NeoForge's side of other loaders' custom payloads on one connection (ADR-0026 40, {@link ForeignPayloads}), when
 * Fabric or Quilt mods run with NeoForge's mods.
 *
 * <p>Each loader keeps the payload ids it registers: Fabric API's networking owns the ids it registers, including the
 * plugin-channel ids both loaders speak ({@code minecraft:register}, {@code minecraft:unregister}, {@code c:version},
 * {@code c:register}), because its codec comes first in {@code CustomPacketPayload}'s codec and its configuration flow
 * waits for its own payloads. NeoForge's own payloads for those ids ({@code MinecraftRegisterPayload} and the others in
 * {@code NetworkRegistry.BUILTIN_PAYLOADS}) are written with NeoForge's codec ({@link #install}; the wire format is the
 * same). NeoForge's channel checks know only the channels NeoForge negotiated or registered, so they would refuse to send
 * Fabric API's payloads ({@code Payload ... may not be sent to the client!}) and disconnect on Fabric payloads nobody
 * receives, and NeoForge's filter of a bundle's packets drops them without a word (ADR-0026 41); the rules ({@link
 * #rules}) leave other loaders' payloads to those loaders. Checked with {@code javap} on
 * NeoForge 26.1.0.19-beta's, 26.1.2.122's and 26.2.0.93's universal jars: {@code NetworkRegistry.checkPacket(Packet,
 * ServerCommonPacketListener)} and {@code (Packet, ClientCommonPacketListener)}, {@code
 * NetworkRegistry.handleModdedPayload(ServerCommonPacketListener, ServerboundCustomPayloadPacket)} and {@code
 * ClientNetworkRegistry.handleModdedPayload(ClientCommonPacketListener, ClientboundCustomPayloadPacket)}, {@code
 * NetworkRegistry.filterGameBundlePackets(ChannelHandlerContext, Iterable)List} (it writes no parameter slot), the maps
 * {@code BUILTIN_PAYLOADS} (id to codec) and {@code PAYLOAD_REGISTRATIONS} (protocol to id to registration).
 *
 * <p>Thread: {@link #install} on the launching thread; the host's answers from network threads.
 */
final class NeoForgeForeignPayloads {

    static final String NETWORK_REGISTRY = "net/neoforged/neoforge/network/registration/NetworkRegistry";
    static final String CLIENT_NETWORK_REGISTRY =
            "net/neoforged/neoforge/client/network/registration/ClientNetworkRegistry";
    private static final String PACKET = "Lnet/minecraft/network/protocol/Packet;";
    private static final String SERVER_LISTENER = "Lnet/minecraft/network/protocol/common/ServerCommonPacketListener;";
    private static final String CLIENT_LISTENER = "Lnet/minecraft/network/protocol/common/ClientCommonPacketListener;";
    private static final String SERVERBOUND = "Lnet/minecraft/network/protocol/common/ServerboundCustomPayloadPacket;";
    private static final String CLIENTBOUND = "Lnet/minecraft/network/protocol/common/ClientboundCustomPayloadPacket;";

    private NeoForgeForeignPayloads() {}

    /** The rules for {@code gameVersion}, with hooks in {@code gameLayerPackage}'s {@code HostPayloadHooks}. */
    static List<TransformRuleDefinition> rules(String gameVersion, String gameLayerPackage) {
        String hooks = gameLayerPackage + "/HostPayloadHooks";
        return List.of(
                rule(
                        "poly:neoforge_send_check_clientbound",
                        gameVersion,
                        Set.of(GameSide.CLIENT, GameSide.DEDICATED_SERVER),
                        new MethodTarget(NETWORK_REGISTRY, "checkPacket", "(" + PACKET + SERVER_LISTENER + ")V"),
                        hooks,
                        "otherLoadersClientboundPayload"),
                rule(
                        "poly:neoforge_send_check_serverbound",
                        gameVersion,
                        Set.of(GameSide.CLIENT),
                        new MethodTarget(NETWORK_REGISTRY, "checkPacket", "(" + PACKET + CLIENT_LISTENER + ")V"),
                        hooks,
                        "otherLoadersServerboundPayload"),
                rule(
                        "poly:neoforge_modded_payload_server",
                        gameVersion,
                        Set.of(GameSide.CLIENT, GameSide.DEDICATED_SERVER),
                        new MethodTarget(
                                NETWORK_REGISTRY, "handleModdedPayload", "(" + SERVER_LISTENER + SERVERBOUND + ")V"),
                        hooks,
                        "dropOtherLoadersPayload"),
                rule(
                        "poly:neoforge_modded_payload_client",
                        gameVersion,
                        Set.of(GameSide.CLIENT),
                        new MethodTarget(
                                CLIENT_NETWORK_REGISTRY,
                                "handleModdedPayload",
                                "(" + CLIENT_LISTENER + CLIENTBOUND + ")V"),
                        hooks,
                        "dropOtherLoadersPayload"),
                new TransformRuleDefinition(
                        "poly:neoforge_bundle_filter",
                        "poly",
                        gameVersion,
                        Set.of(GameSide.CLIENT, GameSide.DEDICATED_SERVER),
                        new MethodTarget(
                                NETWORK_REGISTRY,
                                "filterGameBundlePackets",
                                "(Lio/netty/channel/ChannelHandlerContext;Ljava/lang/Iterable;)Ljava/util/List;"),
                        new InvokeHookBeforeReturn(hooks, "keepOtherLoadersBundledPayloads"),
                        TransformFailurePolicy.DISABLE_RULE));
    }

    private static TransformRuleDefinition rule(
            String id, String gameVersion, Set<GameSide> sides, MethodTarget target, String hooks, String hook) {
        return new TransformRuleDefinition(
                id,
                "poly",
                gameVersion,
                sides,
                target,
                new InvokeHookAtHead(hooks, hook, HeadHookResult.CANCEL_IF_TRUE),
                TransformFailurePolicy.DISABLE_RULE);
    }

    /**
     * Installs NeoForge's view of its own payloads, read from {@code NetworkRegistry} in {@code game} when first asked
     * (its registrations are complete once NeoForge's mods have loaded, before any connection).
     */
    static void install(ClassLoader game) {
        ForeignPayloads.installHost(new Host(game));
    }

    /** NeoForge's payloads: the ids NeoForge registers, and its own objects for its built-in ids. */
    private static final class Host implements ForeignPayloads.Host {

        private final ClassLoader game;
        private volatile Map<?, ?> builtIn;
        private volatile Map<?, ?> registrations;

        Host(ClassLoader game) {
            this.game = game;
        }

        @Override
        public Object encoderFor(Object payload, Object chosen) {
            Object own = builtIn().get(id(payload));
            return own != null && own != chosen && neoForgesOwn(payload) ? own : chosen;
        }

        @Override
        public boolean owns(Object payload, Object protocol) {
            Object id = id(payload);
            if (builtIn().containsKey(id)) {
                return neoForgesOwn(payload);
            }
            Object forProtocol = registrations().get(protocol);
            return forProtocol instanceof Map<?, ?> ids && ids.containsKey(id);
        }

        /** NeoForge's payload classes are in its own packages; another loader's object for a built-in id is not. */
        private static boolean neoForgesOwn(Object payload) {
            return payload.getClass().getName().startsWith("net.neoforged.neoforge.");
        }

        /** Each payload class's {@code type()}, and each type class's {@code id()}, looked up once. */
        private static final ClassValue<Method> ACCESSORS = new ClassValue<>() {
            @Override
            protected Method computeValue(Class<?> type) {
                try {
                    Method accessor =
                            type.getMethod(type.getName().endsWith("CustomPacketPayload$Type") ? "id" : "type");
                    accessor.setAccessible(true);
                    return accessor;
                } catch (NoSuchMethodException e) {
                    throw new IllegalStateException(type + " has no " + e.getMessage(), e);
                }
            }
        };

        private static Object id(Object payload) {
            try {
                Object type = ACCESSORS.get(payload.getClass()).invoke(payload);
                return ACCESSORS.get(type.getClass()).invoke(type);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(
                        "A custom payload without CustomPacketPayload.type().id(): " + payload, e);
            }
        }

        private Map<?, ?> builtIn() {
            Map<?, ?> value = builtIn;
            if (value == null) {
                value = registryMap("BUILTIN_PAYLOADS");
                builtIn = value;
            }
            return value;
        }

        private Map<?, ?> registrations() {
            Map<?, ?> value = registrations;
            if (value == null) {
                value = registryMap("PAYLOAD_REGISTRATIONS");
                registrations = value;
            }
            return value;
        }

        private Map<?, ?> registryMap(String name) {
            try {
                Class<?> registry = Class.forName(NETWORK_REGISTRY.replace('/', '.'), true, game);
                Field field = registry.getDeclaredField(name);
                field.setAccessible(true);
                return (Map<?, ?>) field.get(null);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(
                        "NeoForge's NetworkRegistry." + name + " is not readable (" + e + ")", e);
            }
        }
    }
}
