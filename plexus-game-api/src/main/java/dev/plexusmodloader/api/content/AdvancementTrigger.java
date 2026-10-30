package dev.plexusmodloader.api.content;

import dev.plexusmodloader.api.entity.PlayerView;
import dev.plexusmodloader.api.resource.ResourceId;
import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/**
 * A criterion trigger for advancements in data packs: an advancement criterion with
 * {@code "trigger": "<mod id>:<path>"} is met when the mod calls {@link #trigger}.
 */
@ApiStability(StabilityLevel.PREVIEW)
public interface AdvancementTrigger {

    /** The trigger ID used in advancement files. */
    ResourceId id();

    /**
     * Meets every criterion with this trigger for {@code player}.
     *
     * @throws IllegalStateException on the client side
     */
    void trigger(PlayerView player);
}
