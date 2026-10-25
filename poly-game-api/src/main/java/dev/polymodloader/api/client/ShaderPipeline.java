package dev.polymodloader.api.client;

import dev.polymodloader.api.resource.ResourceId;
import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;
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
