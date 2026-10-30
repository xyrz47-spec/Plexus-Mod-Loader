package dev.plexusmodloader.api.entity;

import dev.plexusmodloader.api.resource.ResourceId;
import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;
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
