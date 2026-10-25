package dev.polymodloader.game.version.layer;

import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;

/**
 * How many mods each loader loaded in this launch, for the title screen's bottom-left text ({@link ModCountLines}).
 *
 * <p>Counting rule (the runtime applies it): every mod a loader actually loads counts once by mod ID, whether the
 * player installed it or an installed mod bundled it (a nested jar); mods that are not loaded (blocked, missing a
 * dependency, wrong side) do not count. Loader runtimes and stand-in mods do not count: Poly Mod Loader itself, {@code
 * minecraft}, {@code java}, {@code fabricloader}, {@code quilt_loader}, {@code forge} and {@code neoforge}.
 *
 * @param polyMods Poly mods (Native API) Poly Mod Loader loaded
 * @param fabricMods Fabric mods the Fabric bridge loaded
 * @param forgeMods MinecraftForge mods FML loads when Forge runs the game (ADR-0021)
 * @param quiltMods Quilt mods ({@code quilt.mod.json}) the Quilt bridge loaded (ADR-0027)
 * @param neoForgeMods NeoForge mods FML 11 loads when NeoForge runs the game (ADR-0026)
 */
@ApiStability(StabilityLevel.INTERNAL)
public record LoadedModCounts(int polyMods, int fabricMods, int forgeMods, int quiltMods, int neoForgeMods) {

    /** No mods of any loader. */
    public static final LoadedModCounts NONE = new LoadedModCounts(0, 0, 0, 0, 0);

    public LoadedModCounts {
        if (polyMods < 0 || fabricMods < 0 || forgeMods < 0 || quiltMods < 0 || neoForgeMods < 0) {
            throw new IllegalArgumentException("mod counts cannot be negative: " + polyMods + ", " + fabricMods + ", "
                    + forgeMods + ", " + quiltMods + ", " + neoForgeMods);
        }
    }

    /** Counts without NeoForge mods. */
    public LoadedModCounts(int polyMods, int fabricMods, int forgeMods, int quiltMods) {
        this(polyMods, fabricMods, forgeMods, quiltMods, 0);
    }

    /** Counts without Quilt and NeoForge mods. */
    public LoadedModCounts(int polyMods, int fabricMods, int forgeMods) {
        this(polyMods, fabricMods, forgeMods, 0, 0);
    }
}
