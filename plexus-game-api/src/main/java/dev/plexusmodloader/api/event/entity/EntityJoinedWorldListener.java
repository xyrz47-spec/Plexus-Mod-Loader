package dev.plexusmodloader.api.event.entity;

import dev.plexusmodloader.api.entity.EntityView;
import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;
import dev.plexusmodloader.api.world.WorldView;

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
