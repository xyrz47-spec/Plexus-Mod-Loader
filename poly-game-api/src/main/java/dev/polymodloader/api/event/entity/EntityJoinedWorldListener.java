package dev.polymodloader.api.event.entity;

import dev.polymodloader.api.entity.EntityView;
import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;
import dev.polymodloader.api.world.WorldView;

/** Listener for {@link EntityEvents#JOINED_WORLD}. */
@ApiStability(StabilityLevel.PREVIEW)
@FunctionalInterface
public interface EntityJoinedWorldListener {

    /**
     * Called on the server thread while the world starts tracking the entity. Do not remove or move
     * the entity from the listener; schedule such changes with {@link WorldView#schedule}. Keep it
     * cheap: chunks full of entities load often.
     *
     * @param entity the entity, of any kind (players, mobs, items, projectiles); valid only during
     *     the call
     * @param world its world
     */
    void onEntityJoinedWorld(EntityView entity, WorldView world);
}
