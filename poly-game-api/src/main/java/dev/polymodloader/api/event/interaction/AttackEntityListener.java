package dev.polymodloader.api.event.interaction;

import dev.polymodloader.api.entity.EntityView;
import dev.polymodloader.api.entity.PlayerView;
import dev.polymodloader.api.event.EventResult;
import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;

/** Listener for {@link InteractionEvents#ATTACK_ENTITY}. */
@ApiStability(StabilityLevel.PREVIEW)
@FunctionalInterface
public interface AttackEntityListener {

    /**
     * Called on the server thread. The views are valid only during the call.
     *
     * @param player the attacker
     * @param target the attacked entity
     * @return {@code DENY} to cancel the attack (no damage, knockback, or durability loss);
     *     {@code ALLOW} or {@code PASS} to let it happen
     */
    EventResult onAttackEntity(PlayerView player, EntityView target);
}
