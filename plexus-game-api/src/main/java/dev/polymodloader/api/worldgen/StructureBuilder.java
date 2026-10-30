package dev.polymodloader.api.worldgen;

import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;

/**
 * Builds a code structure's blocks into its box. Called on world generation threads, once per chunk
 * the box touches (see {@link StructurePieceContext}); keep all state in local variables.
 */
@ApiStability(StabilityLevel.PREVIEW)
@FunctionalInterface
public interface StructureBuilder {

    /** Writes the structure's blocks through {@code context}. */
    void build(StructurePieceContext context);
}
