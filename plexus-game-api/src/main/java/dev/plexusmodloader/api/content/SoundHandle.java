package dev.plexusmodloader.api.content;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/**
 * A declared sound event. Its files are listed in the mod's {@code assets/<mod id>/sounds.json} under
 * the same path, as for the game's sounds.
 */
@ApiStability(StabilityLevel.PREVIEW)
public interface SoundHandle extends ContentHandle {}
