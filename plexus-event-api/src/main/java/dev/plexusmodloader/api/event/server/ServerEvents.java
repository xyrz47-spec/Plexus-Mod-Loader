package dev.plexusmodloader.api.event.server;

import dev.plexusmodloader.api.event.EventKey;
import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/**
 * Server lifecycle events. Fired for dedicated, integrated (single player), and game test servers, in
 * this order: {@link #STARTING}, {@link #STARTED}, {@link #TICK} (repeatedly), {@link #STOPPING},
 * {@link #STOPPED}. Fabric API: {@code ServerLifecycleEvents}; NeoForge: {@code ServerStartingEvent}
 * and its siblings.
 */
@ApiStability(StabilityLevel.PREVIEW)
public final class ServerEvents {

    /** The server is starting: registries and data packs are loaded, worlds are about to be created. */
    public static final EventKey<ServerStartingListener> STARTING =
            EventKey.of("plexus:server/starting", ServerStartingListener.class);

    /** The server finished starting and is about to process its first tick. */
    public static final EventKey<ServerStartedListener> STARTED =
            EventKey.of("plexus:server/started", ServerStartedListener.class);

    /** A server tick begins. */
    public static final EventKey<ServerTickListener> TICK = EventKey.of("plexus:server/tick", ServerTickListener.class);

    /** The server begins to stop. */
    public static final EventKey<ServerStoppingListener> STOPPING =
            EventKey.of("plexus:server/stopping", ServerStoppingListener.class);

    /** The server stopped: players and worlds were saved and the worlds closed. */
    public static final EventKey<ServerStoppedListener> STOPPED =
            EventKey.of("plexus:server/stopped", ServerStoppedListener.class);

    private ServerEvents() {}
}
