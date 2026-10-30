package dev.polymodloader.api.entity;

import dev.polymodloader.api.resource.ResourceId;
import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;

/** An entity with health and status effects: players and mobs. Same rules as {@link EntityView}. */
@ApiStability(StabilityLevel.PREVIEW)
public interface LivingEntityView extends EntityView {

    /** Current health in half-hearts. */
    float health();

    /** Maximum health. */
    float maxHealth();

    /** Sets health, clamped to {@code [0, maxHealth]}; server side only. */
    void setHealth(float health);

    /**
     * Deals generic damage on the server, as if from {@code /damage}. Armour and
     * {@link dev.polymodloader.api.event.entity.EntityEvents#ALLOW_DAMAGE} apply.
     *
     * @return whether the entity was hurt
     * @throws IllegalStateException on the client side
     */
    boolean hurt(float amount);

    /**
     * Deals damage of a data-defined damage type, such as {@code minecraft:magic} or one from the
     * mod's {@code data/<mod id>/damage_type/}.
     *
     * @throws IllegalArgumentException for an unknown damage type
     * @throws IllegalStateException on the client side
     */
    boolean hurt(ResourceId damageType, float amount);

    /**
     * Adds a status effect, such as {@code minecraft:speed} or a mod effect.
     *
     * @param ticks duration in ticks (20 per second)
     * @param amplifier 0 for level I
     * @throws IllegalArgumentException for an unknown effect
     * @throws IllegalStateException on the client side
     */
    void addEffect(ResourceId effect, int ticks, int amplifier);

    /** Whether the entity has the effect. */
    boolean hasEffect(ResourceId effect);

    /**
     * Launches a projectile, such as a mod projectile or {@code minecraft:snowball}, from the eyes in
     * the look direction.
     *
     * @param speed blocks per tick (snowball 1.5)
     * @param inaccuracy random spread (players 1.0, 0 for none)
     * @return the launched projectile
     * @throws IllegalArgumentException when {@code type} is not a projectile
     * @throws IllegalStateException on the client side
     */
    EntityView launchProjectile(ResourceId type, float speed, float inaccuracy);
}
