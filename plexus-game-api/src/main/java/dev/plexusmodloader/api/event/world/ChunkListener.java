package dev.plexusmodloader.api.event.world;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;
import dev.plexusmodloader.api.world.WorldView;

/** Listener for {@link WorldEvents#CHUNK_LOADED} and {@link WorldEvents#CHUNK_UNLOADED}. */
@ApiStability(StabilityLevel.PREVIEW)
@FunctionalInterface
public interface ChunkListener {

    /** A chunk of {@code world}; coordinates are chunk coordinates. Keep it cheap: chunks load often. */
    void onChunk(WorldView world, int chunkX, int chunkZ);
}
