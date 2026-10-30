package dev.plexusmodloader.api.system;

import dev.plexusmodloader.api.runtime.GameSide;
import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/**
 * Information passed to a {@link SystemProvider} when Poly Mod Loader creates the active implementation.
 *
 * @param systemId the system being created
 * @param side the process side
 * @param providerModId mod that supplies the provider, or {@code poly} for the default
 */
@ApiStability(StabilityLevel.PREVIEW)
public record SystemContext(SystemId systemId, GameSide side, String providerModId) {}
