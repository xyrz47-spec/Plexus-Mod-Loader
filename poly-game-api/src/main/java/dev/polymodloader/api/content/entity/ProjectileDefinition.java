package dev.polymodloader.api.content.entity;

import dev.polymodloader.api.resource.ResourceId;
import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;
import java.util.Objects;

/**
 * A thrown projectile such as a snowball: it flies, falls with {@code gravity}, and on its first hit
 * deals {@code damage}, calls the behaviour, and disappears. Clients draw it as the {@code item}.
 * Launch it with {@link dev.polymodloader.api.entity.LivingEntityView#launchProjectile}.
 *
 * @param item item drawn in flight
 * @param size hitbox width and height in blocks
 * @param gravity downward acceleration per tick (snowball 0.03)
 * @param damage damage to a hit entity, 0 for none
 * @param behavior hit callbacks
 */
@ApiStability(StabilityLevel.PREVIEW)
public record ProjectileDefinition(
        ResourceId item, float size, double gravity, float damage, ProjectileBehavior behavior) {

    public ProjectileDefinition {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(behavior, "behavior");
        if (size <= 0 || gravity < 0 || damage < 0) {
            throw new IllegalArgumentException("size must be > 0, gravity and damage >= 0");
        }
    }

    /** A builder for a 0.25-block projectile drawn as {@code item}, with snowball gravity and no damage. */
    public static Builder builder(ResourceId item) {
        return new Builder(item);
    }

    /** Builds a {@link ProjectileDefinition}. */
    public static final class Builder {

        private final ResourceId item;
        private float size = 0.25f;
        private double gravity = 0.03;
        private float damage;
        private ProjectileBehavior behavior = new ProjectileBehavior() {};

        private Builder(ResourceId item) {
            this.item = item;
        }

        /** Hitbox width and height in blocks. */
        public Builder size(float size) {
            this.size = size;
            return this;
        }

        /** Downward acceleration per tick (snowball 0.03). */
        public Builder gravity(double gravity) {
            this.gravity = gravity;
            return this;
        }

        /** Damage to a hit entity, 0 for none. */
        public Builder damage(float damage) {
            this.damage = damage;
            return this;
        }

        /** Callbacks for hitting blocks and entities. */
        public Builder behavior(ProjectileBehavior behavior) {
            this.behavior = behavior;
            return this;
        }

        /** Creates the definition; invalid values throw {@link IllegalArgumentException}. */
        public ProjectileDefinition build() {
            return new ProjectileDefinition(item, size, gravity, damage, behavior);
        }
    }
}
