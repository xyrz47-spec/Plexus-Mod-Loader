package dev.plexusmodloader.api.system;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/**
 * Marker for the contract interface of a replaceable system. Every system has exactly one active
 * implementation: Poly Mod Loader's default or one explicit replacement declared by a mod (design doc §4.3,
 * §4.4).
 */
@ApiStability(StabilityLevel.PREVIEW)
public interface PolySystem {}
