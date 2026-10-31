package dev.plexusmodloader.game.version.layer;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;
import java.nio.file.Path;

/**
 * A mod jar exposed as a resource pack ({@code assets/}) and a data pack ({@code data/}).
 *
 * @param modId mod ID, used as the pack ID {@code plexus/<mod id>}
 * @param name display name
 * @param version mod version
 * @param jar the mod jar
 */
@ApiStability(StabilityLevel.INTERNAL)
public record ModResourcePack(String modId, String name, String version, Path jar) {}
