package dev.polymodloader.game.layer.v26_1;

import dev.polymodloader.api.entity.PlayerView;
import dev.polymodloader.api.event.EventKey;
import dev.polymodloader.api.event.entity.EntityEvents;
import dev.polymodloader.api.event.player.PlayerEvents;
import dev.polymodloader.api.event.server.ServerEvents;
import dev.polymodloader.api.event.server.ServerView;
import dev.polymodloader.api.event.world.WorldEvents;
import dev.polymodloader.api.event.world.WorldListener;
import dev.polymodloader.api.world.WorldView;
import dev.polymodloader.game.version.layer.GameLayerContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.util.ProgressListener;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hooks for the server, world, player-respawn, and entity lifecycle events: {@link
 * ServerEvents#STARTING}/{@link ServerEvents#STOPPED}, {@link WorldEvents#LOADED}/{@link
 * WorldEvents#SAVING}/{@link WorldEvents#UNLOADED}, {@link PlayerEvents#COPY}/{@link
 * PlayerEvents#RESPAWNED}, and {@link EntityEvents#JOINED_WORLD}. The rules are in {@code
 * Minecraft26_1Adapter}; parameter lists mirror the targets (verified with {@code javap}).
 */
public final class LifecycleHooks {

    private static final Logger LOGGER = LoggerFactory.getLogger("Poly Mod Loader");

    private LifecycleHooks() {}

    /** From the head of {@code MinecraftServer.createLevels()}: registries and data are loaded, no world yet. */
    static void onServerStarting(MinecraftServer server) {
        GameLayerContext context = listening(ServerEvents.STARTING);
        if (context != null) {
            ServerView view = new GameServerView(server);
            context.events().publish(ServerEvents.STARTING, listener -> listener.onServerStarting(view));
        }
    }

    /** Before the return of {@code MinecraftServer.createLevels()}: every dimension exists. */
    public static void onLevelsCreated(MinecraftServer server) {
        GameLayerContext context = listening(WorldEvents.LOADED);
        if (context != null) {
            for (ServerLevel level : server.getAllLevels()) {
                publishWorld(context, WorldEvents.LOADED, level);
            }
        }
    }

    /** Head of {@code ServerLevel.save(ProgressListener, boolean, boolean)}; the last flag skips saving. */
    public static void onLevelSave(ServerLevel level, ProgressListener progress, boolean flush, boolean skipSave) {
        GameLayerContext context = skipSave ? null : listening(WorldEvents.SAVING);
        if (context != null) {
            publishWorld(context, WorldEvents.SAVING, level);
        }
    }

    /**
     * Runs right before each {@code ServerLevel.close()} call in {@code MinecraftServer.stopServer()}, which stays
     * in place: fires {@code UNLOADED}, then the game closes the world.
     */
    public static void onCloseLevel(ServerLevel level) {
        GameLayerContext context = listening(WorldEvents.UNLOADED);
        if (context != null) {
            publishWorld(context, WorldEvents.UNLOADED, level);
        }
    }

    /** Before the return of {@code MinecraftServer.stopServer()}. */
    public static void onServerStopped(MinecraftServer server) {
        // stopServer has saved and closed every world by now. Checks read this line: since 26.3 the game no
        // longer logs that all dimensions are saved.
        LOGGER.info("[Poly Mod Loader] Server stopped after saving and closing its worlds");
        GameLayerContext context = listening(ServerEvents.STOPPED);
        if (context != null) {
            ServerView view = new GameServerView(server);
            context.events().publish(ServerEvents.STOPPED, listener -> listener.onServerStopped(view));
        }
    }

    /** From the return hook of {@code ServerPlayer.restoreFrom}, after Poly Mod Loader copied entity data. */
    static void onPlayerCopied(ServerPlayer newPlayer, ServerPlayer oldPlayer, boolean returnedFromEnd) {
        GameLayerContext context = listening(PlayerEvents.COPY);
        if (context != null) {
            PlayerView oldView = Views.player(oldPlayer);
            PlayerView newView = Views.player(newPlayer);
            context.events()
                    .publish(PlayerEvents.COPY, listener -> listener.onPlayerCopy(oldView, newView, returnedFromEnd));
        }
    }

    /** Before the return of {@code PlayerList.respawn}: the new player object is in its world. */
    public static ServerPlayer onPlayerRespawned(
            PlayerList players,
            ServerPlayer oldPlayer,
            boolean returnedFromEnd,
            Entity.RemovalReason reason,
            ServerPlayer newPlayer) {
        GameLayerContext context = listening(PlayerEvents.RESPAWNED);
        if (context != null) {
            PlayerView view = Views.player(newPlayer);
            context.events()
                    .publish(PlayerEvents.RESPAWNED, listener -> listener.onPlayerRespawned(view, returnedFromEnd));
        }
        return newPlayer;
    }

    /**
     * Runs right after the {@code ServerChunkCache.addEntity} call at the start of {@code
     * ServerLevel.EntityCallbacks.onTrackingStart}, which stays in place: the world has started tracking the entity,
     * then {@code JOINED_WORLD} fires.
     */
    public static void onEntityTracked(ServerChunkCache chunks, Entity entity) {
        GameLayerContext context = listening(EntityEvents.JOINED_WORLD);
        if (context != null) {
            dev.polymodloader.api.entity.EntityView view = Views.entity(entity);
            WorldView world = Views.world(entity.level());
            context.events().publish(EntityEvents.JOINED_WORLD, listener -> listener.onEntityJoinedWorld(view, world));
        }
    }

    private static void publishWorld(GameLayerContext context, EventKey<WorldListener> event, ServerLevel level) {
        WorldView world = Views.world(level);
        context.events().publish(event, listener -> listener.onWorld(world));
    }

    /** The context when Poly Mod Loader runs and someone listens to {@code event}, else null. */
    private static GameLayerContext listening(EventKey<?> event) {
        GameLayerContext context = LayerState.context().orElse(null);
        return context != null && context.events().hasListeners(event) ? context : null;
    }

    /** {@link ServerView} over the game's server object. */
    private record GameServerView(MinecraftServer server) implements ServerView {

        @Override
        public int tickCount() {
            return server.getTickCount();
        }

        @Override
        public int playerCount() {
            return server.getPlayerCount();
        }

        @Override
        public String motd() {
            return server.getMotd();
        }

        @Override
        public boolean dedicated() {
            return server.isDedicatedServer();
        }
    }
}
