package dev.plexusmodloader.api.client;

import dev.plexusmodloader.api.resource.ResourceId;
import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;
import java.util.Objects;

/**
 * A declared shader pipeline, used with {@link WorldCanvas#quads} and {@link BlockEntityCanvas#quads}.
 *
 * @param id the pipeline's ID, {@code <mod id>:<path>}
 */
@ApiStability(StabilityLevel.PREVIEW)
public record ShaderPipeline(ResourceId id) {

    public ShaderPipeline {
        Objects.requireNonNull(id, "id");
    }
}
