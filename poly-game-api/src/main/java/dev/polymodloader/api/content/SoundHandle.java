package dev.polymodloader.api.content;

import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;

/**
 * A declared sound event. Its files are listed in the mod's {@code assets/<mod id>/sounds.json} under
 * the same path, as for the game's sounds.
 */
@ApiStability(StabilityLevel.PREVIEW)
public interface SoundHandle extends ContentHandle {}
