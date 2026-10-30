package dev.polymodloader.api.content.entity;

import dev.polymodloader.api.entity.LivingEntityView;
import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;
import dev.polymodloader.api.world.InteractionResult;

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
