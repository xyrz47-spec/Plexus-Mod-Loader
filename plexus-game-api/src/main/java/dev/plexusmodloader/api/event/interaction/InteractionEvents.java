package dev.plexusmodloader.api.event.interaction;

import dev.plexusmodloader.api.event.EventKey;
import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/**
 * A player interacting with the world: right-clicking blocks, items, and entities, and attacking
 * entities. Fired on the server thread when the server handles the player's action, for connected
 * and mock players alike. The client's own prediction of the click is not an event yet; when a
 * listener refuses an action the server corrects the client as it does for any refused action.
 *
 * <p><b>Results:</b> these are decision events (see {@link dev.plexusmodloader.api.event.EventResult}):
 * listeners run in priority order until one decides. The use events take a {@link
 * dev.plexusmodloader.api.world.InteractionResult}: {@code PASS} lets the next listener and then the game
 * handle the click; {@code SUCCESS} or {@code CONSUME} ends it as handled, so the game's own
 * behaviour (opening a chest, placing a block, eating) does not happen; {@code FAIL} ends it as
 * refused. The attack event takes an {@code EventResult}: {@code DENY} cancels the attack.
 *
 * <p>Fabric API: {@code UseBlockCallback}, {@code UseItemCallback}, {@code UseEntityCallback},
 * {@code AttackEntityCallback}. NeoForge: {@code PlayerInteractEvent.RightClickBlock}, {@code
 * RightClickItem}, {@code EntityInteract}, {@code AttackEntityEvent}.
 */
@ApiStability(StabilityLevel.PREVIEW)
public final class InteractionEvents {

    /**
     * A player right-clicks a block, before the held item or the block reacts. Fired once per hand
     * the game tries: the main hand first, then the off hand when the main hand passed.
     */
    public static final EventKey<UseBlockListener> USE_BLOCK =
            EventKey.of("plexus:interaction/use_block", UseBlockListener.class);

    /** A player right-clicks with an item in the air (not at a block or entity), before the item reacts. */
    public static final EventKey<UseItemListener> USE_ITEM =
            EventKey.of("plexus:interaction/use_item", UseItemListener.class);

    /** A player right-clicks an entity, before the entity or the held item reacts. */
    public static final EventKey<UseEntityListener> USE_ENTITY =
            EventKey.of("plexus:interaction/use_entity", UseEntityListener.class);

    /** A player attacks (left-clicks) an entity, before damage and knockback. */
    public static final EventKey<AttackEntityListener> ATTACK_ENTITY =
            EventKey.of("plexus:interaction/attack_entity", AttackEntityListener.class);

    private InteractionEvents() {}
}
