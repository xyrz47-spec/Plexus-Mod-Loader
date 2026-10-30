package dev.plexusmodloader.api.event.player;

import dev.plexusmodloader.api.event.EventKey;
import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/** Player connection and respawn events, fired on the server thread of every server kind. */
@ApiStability(StabilityLevel.PREVIEW)
public final class PlayerEvents {

    /**
     * The game is building the new player object for a respawn (after death or when leaving the End)
     * and copying data from the old one. Fabric API: {@code ServerPlayerEvents.COPY_FROM}; NeoForge:
     * {@code PlayerEvent.Clone}.
     */
    public static final EventKey<PlayerCopyListener> COPY = EventKey.of("poly:player/copy", PlayerCopyListener.class);

    /**
     * A player respawned and the new player object is in its world. Fabric API: {@code
     * ServerPlayerEvents.AFTER_RESPAWN}; NeoForge: {@code PlayerEvent.PlayerRespawnEvent}.
     */
    public static final EventKey<PlayerRespawnedListener> RESPAWNED =
            EventKey.of("poly:player/respawned", PlayerRespawnedListener.class);

    /** A player finished joining the server and is in the world. */
    public static final EventKey<PlayerJoinedListener> JOINED =
            EventKey.of("poly:player/joined", PlayerJoinedListener.class);

    /** A player is about to be removed from the server (disconnect or kick). */
    public static final EventKey<PlayerLeavingListener> LEAVING =
            EventKey.of("poly:player/leaving", PlayerLeavingListener.class);

    /**
     * A player is logging in (after authentication and the game's ban, whitelist, and player-limit
     * checks); any listener may refuse them. Fired on the server thread.
     */
    public static final EventKey<LoginCheckListener> LOGIN_CHECK =
            EventKey.of("poly:player/login_check", LoginCheckListener.class);

    private PlayerEvents() {}
}
