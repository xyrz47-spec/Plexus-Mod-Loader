package dev.polymodloader.api.system;

import dev.polymodloader.api.runtime.GameSide;
import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;

/**
 * Information passed to a {@link SystemProvider} when Poly Mod Loader creates the active implementation.
 *
 * @param systemId the system being created
 * @param side the process side
 * @param providerModId mod that supplies the provider, or {@code poly} for the default
 */
@ApiStability(StabilityLevel.PREVIEW)
public record SystemContext(SystemId systemId, GameSide side, String providerModId) {}
