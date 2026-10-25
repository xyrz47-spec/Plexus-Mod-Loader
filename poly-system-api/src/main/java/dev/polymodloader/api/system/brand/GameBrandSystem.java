package dev.polymodloader.api.system.brand;

import dev.polymodloader.api.runtime.GameSide;
import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;
import dev.polymodloader.api.system.PolySystem;
import dev.polymodloader.api.system.SystemId;

/**
 * System {@code poly:game_brand}: the mod brand the game reports about itself — shown through the
 * window title's "modded" marker, crash reports, and the server brand sent to clients.
 *
 * <ul>
 *   <li><b>Default:</b> returns {@code poly}.
 *   <li><b>Replaceable:</b> yes, as a whole ({@code REPLACE}); there is no extension point.
 *   <li><b>Threading:</b> may be called from any thread, repeatedly; implementations must be
 *       stateless and fast.
 *   <li><b>Result rules:</b> 1–64 printable ASCII characters. Anything else stops the launch when
 *       Poly Mod Loader validates the implementation ({@code POLY-SYSTEM-003}).
 *   <li><b>Owned data:</b> none.
 * </ul>
 */
@ApiStability(StabilityLevel.PREVIEW)
public interface GameBrandSystem extends PolySystem {

    /** ID of this system. */
    SystemId ID = new SystemId("poly:game_brand");

    /** The brand reported by {@code side}. */
    String brand(GameSide side);
}
