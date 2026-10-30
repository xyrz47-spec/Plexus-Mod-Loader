package dev.plexusmodloader.api.event.entity;

import dev.plexusmodloader.api.event.EventKey;
import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/** Entity events, fired on the server thread. */
@ApiStability(StabilityLevel.PREVIEW)
public final class EntityEvents {

    /**
     * An entity of any kind joins a server world: spawned, loaded with its chunk, or arriving from
     * another dimension. Fabric API: {@code ServerEntityEvents.ENTITY_LOAD}; NeoForge: {@code
     * EntityJoinLevelEvent} (not cancellable here).
     */
    public static final EventKey<EntityJoinedWorldListener> JOINED_WORLD =
            EventKey.of("poly:entity/joined_world", EntityJoinedWorldListener.class);

    /** A living entity is about to take damage; any listener returning {@code false} cancels it. */
    public static final EventKey<AllowDamageListener> ALLOW_DAMAGE =
            EventKey.of("poly:entity/allow_damage", AllowDamageListener.class);

    /**
     * A living entity is about to take damage that no {@link #ALLOW_DAMAGE} listener cancelled;
     * listeners may change the amount. Armour and resistance apply to the result.
     */
    public static final EventKey<DamageModifier> MODIFY_DAMAGE =
            EventKey.of("poly:entity/modify_damage", DamageModifier.class);

    /** A living entity died. */
    public static final EventKey<DeathListener> DIED = EventKey.of("poly:entity/died", DeathListener.class);

    private EntityEvents() {}
}
