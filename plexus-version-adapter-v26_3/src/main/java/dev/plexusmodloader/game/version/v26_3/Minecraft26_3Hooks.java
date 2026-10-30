package dev.plexusmodloader.game.version.v26_3;

import dev.plexusmodloader.api.event.server.ServerEvents;
import dev.plexusmodloader.api.runtime.GameSide;
import dev.plexusmodloader.api.system.SystemLookup;
import dev.plexusmodloader.api.system.brand.GameBrandSystem;
import dev.plexusmodloader.game.version.EventPublisher;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Static entry points that transformed Minecraft 26.3 code calls. Each hook forwards to the active
 * Poly Mod Loader system, so game code never depends on which mod (if any) replaced the system. Only this
 * version adapter knows that these hooks exist.
 */
public final class Minecraft26_3Hooks {

    private static final Logger LOGGER = LoggerFactory.getLogger("Poly Mod Loader");
    private static final AtomicBoolean CLIENT_BRAND_REPORTED = new AtomicBoolean();
    private static final AtomicBoolean SERVER_BRAND_REPORTED = new AtomicBoolean();
    private static final AtomicReference<Runnable> MOD_INITIALIZATION = new AtomicReference<>();

    private static volatile SystemLookup systems;
    private static volatile EventPublisher events;
    private static volatile MinecraftServerView.Accessors serverAccessors;
    private static volatile boolean serverEventsDisabled;
    /** The server that already fired STARTED; a new server object (next single-player world) fires again. */
    private static volatile Object startedServer;

    private Minecraft26_3Hooks() {}

    static void bind(SystemLookup lookup) {
        systems = lookup;
    }

    static void bindEvents(EventPublisher publisher) {
        events = publisher;
    }

    static void bindModInitialization(Runnable initialization) {
        MOD_INITIALIZATION.set(initialization);
    }

    /**
     * Injected where Fabric Loader initialises mods: before {@code Util.startTimerHackThread()} in the server and game
     * test starts, before {@code Thread.currentThread()} in {@code Minecraft}'s constructor. Runs the bound
     * initialization once.
     */
    public static void onModInitialization() {
        Runnable initialization = MOD_INITIALIZATION.getAndSet(null);
        if (initialization != null) {
            initialization.run();
        }
    }

    /** Injected at the start of {@code MinecraftServer.tickServer(BooleanSupplier)}. */
    public static void onServerTick(Object server) {
        EventPublisher publisher = events;
        if (publisher == null || serverEventsDisabled) {
            return;
        }
        boolean firstTick = startedServer != server;
        if (!firstTick && !publisher.hasListeners(ServerEvents.TICK)) {
            return;
        }
        MinecraftServerView view = view(server);
        if (view == null) {
            return;
        }
        if (firstTick) {
            startedServer = server;
            publisher.publish(ServerEvents.STARTED, listener -> listener.onServerStarted(view));
        }
        publisher.publish(ServerEvents.TICK, listener -> listener.onServerTick(view));
    }

    /** The server whose {@code runServer} started last, until it stops. */
    private static volatile Object runningServer;

    static Object runningServer() {
        return runningServer;
    }

    /** Injected at the start of {@code MinecraftServer.runServer()}, on the server thread. */
    public static void onServerRunning(Object server) {
        runningServer = server;
    }

    /** Injected at the start of {@code MinecraftServer.stopServer()}. */
    public static void onServerStopping(Object server) {
        if (runningServer == server) {
            runningServer = null;
        }
        EventPublisher publisher = events;
        if (publisher == null || serverEventsDisabled) {
            return;
        }
        MinecraftServerView view = view(server);
        if (view != null) {
            publisher.publish(ServerEvents.STOPPING, listener -> listener.onServerStopping(view));
        }
    }

    private static MinecraftServerView view(Object server) {
        try {
            MinecraftServerView.Accessors accessors = serverAccessors;
            if (accessors == null) {
                accessors = MinecraftServerView.Accessors.resolve(server.getClass());
                serverAccessors = accessors;
            }
            return new MinecraftServerView(server, accessors);
        } catch (IllegalStateException e) {
            // Never break the game loop: report once and stop firing server events.
            serverEventsDisabled = true;
            LOGGER.error("[Poly Mod Loader] {}; server events are disabled for this run", e.getMessage(), e);
            return null;
        }
    }

    /** Replaces the body of {@code ClientBrandRetriever.getClientModName()}. */
    public static String clientModName() {
        return brand(GameSide.CLIENT, CLIENT_BRAND_REPORTED);
    }

    /** Replaces the body of {@code MinecraftServer.getServerModName()}. */
    public static String serverModName() {
        return brand(GameSide.DEDICATED_SERVER, SERVER_BRAND_REPORTED);
    }

    private static String brand(GameSide side, AtomicBoolean reported) {
        SystemLookup lookup = systems;
        if (lookup == null) {
            throw new IllegalStateException(
                    "PLEXUS-SYSTEM-006 Minecraft asked for the game brand before Poly Mod Loader bound its systems");
        }
        String brand = lookup.get(GameBrandSystem.class).brand(side);
        if (reported.compareAndSet(false, true)) {
            LOGGER.info("[Poly Mod Loader] {} reported '{}' to Minecraft ({})", GameBrandSystem.ID, brand, side);
        }
        return brand;
    }
}
