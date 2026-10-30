package dev.polymodloader.api.content.entity;

import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;

/** Spawn group of a mob, which decides despawning and difficulty rules like the game's categories. */
@ApiStability(StabilityLevel.PREVIEW)
public enum MobCategory {
    /** Peaceful animals; persistent. */
    CREATURE,
    /** Hostile mobs; removed on peaceful difficulty and despawn far from players. */
    MONSTER,
    /** Ambient creatures such as bats. */
    AMBIENT,
    /** Water animals. */
    WATER_CREATURE
}
