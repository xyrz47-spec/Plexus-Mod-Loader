package dev.polymodloader.api.event.world;

import dev.polymodloader.api.event.EventKey;
import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;

/**
 * Server-side dimension events. Per dimension and server start the order is {@link #LOADED}, {@link
 * #READY}, {@link #SAVING} (on every save), {@link #UNLOADED}. Fabric API: {@code ServerWorldEvents};
 * NeoForge: {@code LevelEvent.Load}, {@code Unload}, {@code Save}.
 */
@ApiStability(StabilityLevel.PREVIEW)
public final class WorldEvents {

    /**
     * The server created every dimension of the world; fired for each, before spawn chunks are
     * prepared and before the first tick. Dimensions are not saved or ticking yet.
     */
    public static final EventKey<WorldListener> LOADED = EventKey.of("poly:world/loaded", WorldListener.class);

    /**
     * A dimension is about to be saved (autosave, {@code /save-all}, or stopping), before its chunks
     * and level data are written; changes made now are saved with it.
     */
    public static final EventKey<WorldListener> SAVING = EventKey.of("poly:world/saving", WorldListener.class);

    /**
     * The stopping server is about to close a dimension, after it was saved; the last moment to read
     * it. Changes are not saved any more.
     */
    public static final EventKey<WorldListener> UNLOADED = EventKey.of("poly:world/unloaded", WorldListener.class);

    /**
     * A dimension ticks for the first time after it was loaded or created; a good moment to read
     * {@code WorldView.storage}. Fired once per dimension per server start.
     */
    public static final EventKey<WorldReadyListener> READY = EventKey.of("poly:world/ready", WorldReadyListener.class);

    /** A chunk finished loading (from disk or newly generated) on the server. */
    public static final EventKey<ChunkListener> CHUNK_LOADED =
            EventKey.of("poly:world/chunk_loaded", ChunkListener.class);

    /** A chunk is about to be unloaded on the server; it has been saved. */
    public static final EventKey<ChunkListener> CHUNK_UNLOADED =
            EventKey.of("poly:world/chunk_unloaded", ChunkListener.class);

    private WorldEvents() {}
}
