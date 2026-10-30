package dev.plexusmodloader.api.entity;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;
import java.util.Optional;

/** A mob with AI: pathfinding, looking, and an attack target. Server side for changes. */
@ApiStability(StabilityLevel.PREVIEW)
public interface MobView extends LivingEntityView {

    /**
     * Starts walking to a position along a path.
     *
     * @param speed multiplier of the mob's speed (1.0 normal)
     * @return whether a path was found
     */
    boolean navigateTo(double x, double y, double z, double speed);

    /** Whether the mob is following a path. */
    boolean isNavigating();

    /** Stops following the path. */
    void stopNavigating();

    /** Turns the head towards a position this tick. */
    void lookAt(double x, double y, double z);

    /** The attack target, if any. */
    Optional<LivingEntityView> target();

    /** Sets the attack target, or clears it with {@code null}. */
    void setTarget(LivingEntityView target);

    /**
     * Plays one of the mob's {@link dev.plexusmodloader.api.content.entity.model.KeyframeAnimation.Trigger#ON_DEMAND
     * ON_DEMAND} model animations from the start on every client that sees the mob. Server only.
     *
     * @param name the animation's name in the mob's {@link
     *     dev.plexusmodloader.api.content.entity.model.EntityModelDefinition}
     * @throws IllegalArgumentException if the mob is not a mod mob with a model that has such an animation
     */
    void playAnimation(String name);

    /**
     * Stops an {@link dev.plexusmodloader.api.content.entity.model.KeyframeAnimation.Trigger#ON_DEMAND
     * ON_DEMAND} animation on every client; a non-looping one also stops by itself at its end. Server only.
     *
     * @param name the animation's name
     * @throws IllegalArgumentException if the mob has no such animation
     */
    void stopAnimation(String name);
}
