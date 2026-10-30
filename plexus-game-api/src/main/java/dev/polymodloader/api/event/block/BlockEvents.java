package dev.polymodloader.api.event.block;

import dev.polymodloader.api.event.EventKey;
import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;

/** Block events, fired on the server thread. */
@ApiStability(StabilityLevel.PREVIEW)
public final class BlockEvents {

    /**
     * A player is about to break a block (survival or creative); a decision event (see {@link
     * dev.polymodloader.api.event.EventResult}): {@code DENY} keeps the block. Fired before the game's
     * own checks for the held item, game master blocks, and adventure mode, so it also fires for
     * breaks the game then refuses. Fabric API: {@code PlayerBlockBreakEvents.BEFORE}; NeoForge:
     * {@code BlockEvent.BreakEvent}.
     */
    public static final EventKey<BlockBreakListener> BREAK = EventKey.of("poly:block/break", BlockBreakListener.class);

    /** A player broke a block (survival or creative). */
    public static final EventKey<BlockBrokenListener> BROKEN =
            EventKey.of("poly:block/broken", BlockBrokenListener.class);

    /**
     * A player is about to place a block from a block item (including beds and doors); a decision
     * event: {@code DENY} refuses it. NeoForge: {@code BlockEvent.EntityPlaceEvent} for players.
     */
    public static final EventKey<BlockPlaceListener> PLACE = EventKey.of("poly:block/place", BlockPlaceListener.class);

    /** A player placed a block from a block item. */
    public static final EventKey<BlockPlacedListener> PLACED =
            EventKey.of("poly:block/placed", BlockPlacedListener.class);

    private BlockEvents() {}
}
