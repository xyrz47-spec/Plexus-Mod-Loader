package dev.polymodloader.api.event.world;

import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;
import dev.polymodloader.api.world.WorldView;

/** Listener for {@link WorldEvents#CHUNK_LOADED} and {@link WorldEvents#CHUNK_UNLOADED}. */
@ApiStability(StabilityLevel.PREVIEW)
@FunctionalInterface
public interface ChunkListener {

    /** A chunk of {@code world}; coordinates are chunk coordinates. Keep it cheap: chunks load often. */
    void onChunk(WorldView world, int chunkX, int chunkZ);
}
