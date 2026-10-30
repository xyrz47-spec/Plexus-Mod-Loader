package dev.polymodloader.api.entity;

import dev.polymodloader.api.resource.ResourceId;
import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;
import java.util.Optional;

/**
 * What hurt an entity.
 *
 * @param type damage type, for example {@code minecraft:fall} or {@code minecraft:player_attack}
 * @param amount damage before armour, in half-hearts
 * @param attacker the entity responsible, if any
 */
@ApiStability(StabilityLevel.PREVIEW)
public record DamageInfo(ResourceId type, float amount, Optional<EntityView> attacker) {}
