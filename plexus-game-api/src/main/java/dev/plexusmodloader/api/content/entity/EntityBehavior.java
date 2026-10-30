package dev.plexusmodloader.api.content.entity;

import dev.plexusmodloader.api.entity.LivingEntityView;
import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;
import dev.plexusmodloader.api.world.InteractionResult;

/** Code that runs for a mod mob. Defaults keep the game's behaviour. */
@ApiStability(StabilityLevel.PREVIEW)
public interface EntityBehavior {

    /** Every server tick while the mob is alive. Keep it cheap. */
    default void onServerTick(LivingEntityView entity) {}

    /** A player used the mob (both sides; check {@code world().isClientSide()}). */
    default InteractionResult onInteract(EntityInteraction interaction) {
        return InteractionResult.PASS;
    }
}
