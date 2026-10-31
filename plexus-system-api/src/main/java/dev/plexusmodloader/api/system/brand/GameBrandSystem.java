package dev.plexusmodloader.api.system.brand;

import dev.plexusmodloader.api.runtime.GameSide;
import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;
import dev.plexusmodloader.api.system.PlexusSystem;
import dev.plexusmodloader.api.system.SystemId;

/**
 * System {@code plexus:game_brand} (legacy alias {@code plexus:game_brand}): the mod brand the game reports about itself — shown through the
 * window title's "modded" marker, crash reports, and the server brand sent to clients.
 *
 * <ul>
 *   <li><b>Default:</b> returns {@code plexus}.
 *   <li><b>Replaceable:</b> yes, as a whole ({@code REPLACE}); there is no extension point.
 *   <li><b>Threading:</b> may be called from any thread, repeatedly; implementations must be
 *       stateless and fast.
 *   <li><b>Result rules:</b> 1–64 printable ASCII characters. Anything else stops the launch when
 *       Plexus Mod Loader validates the implementation ({@code PLEXUS-SYSTEM-003}).
 *   <li><b>Owned data:</b> none.
 * </ul>
 */
@ApiStability(StabilityLevel.PREVIEW)
public interface GameBrandSystem extends PlexusSystem {

    /** ID of this system. */
    SystemId ID = new SystemId("plexus:game_brand");

    /** Legacy ID of this system for backward compatibility with Plexus mods. */
    SystemId LEGACY_ID = new SystemId("poly:game_brand");

    /** The brand reported by {@code side}. */
    String brand(GameSide side);
}
