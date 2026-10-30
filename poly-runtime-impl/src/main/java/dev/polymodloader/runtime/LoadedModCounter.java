package dev.polymodloader.runtime;

import dev.polymodloader.game.version.layer.LoadedModCounts;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Counts the mods each loader loaded in a launch, for the title screen ({@link LoadedModCounts}).
 *
 * <p>Rule: a mod counts once by mod ID when its loader actually loads it, whether the player installed it or an
 * installed mod bundled it (a nested jar); the lists given here hold only loaded mods. IDs of loader runtimes and stand-in
 * mods never count ({@link #NOT_MODS}), even if a jar declares one.
 */
final class LoadedModCounter {

    /** Loader name of the Fabric bridge ({@code ModLoaderBridge.loaderName()}). */
    static final String FABRIC = "Fabric";

    /** Loader name of the Quilt bridge (ADR-0027). */
    static final String QUILT = "Quilt";

    /** Loader name of the mods NeoForge's FML runs as the host ({@code GameHost.mods()}, ADR-0026). */
    static final String NEOFORGE = "NeoForge";

    /** Plexus Mod Loader itself, the game, Java, and the loaders' own entries in their mod lists. */
    static final Set<String> NOT_MODS =
            Set.of("plexus", "poly", "minecraft", "java", "fabricloader", "quilt_loader", "forge", "neoforge");

    private LoadedModCounter() {}

    /**
     * @param polyModIds IDs of the Poly mods Poly Mod Loader loads
     * @param foreignModIdsByLoader IDs of the mods each bridge loads, by the bridge's loader name; only Fabric's and
     *     Quilt's count (no other bridge loads mods itself)
     * @param hostModIds IDs of the mods MinecraftForge's FML loads itself as the host (ADR-0021), empty without that
     *     host
     */
    static LoadedModCounts count(
            Collection<String> polyModIds,
            Map<String, ? extends Collection<String>> foreignModIdsByLoader,
            Collection<String> hostModIds) {
        return count(polyModIds, foreignModIdsByLoader, hostModIds, Set.of());
    }

    /**
     * @param polyModIds IDs of the Poly mods Poly Mod Loader loads
     * @param foreignModIdsByLoader IDs of the mods each bridge loads, by the bridge's loader name; only Fabric's and
     *     Quilt's count (no other bridge loads mods itself)
     * @param forgeModIds IDs of the mods MinecraftForge's FML loads itself as the host (ADR-0021)
     * @param neoForgeModIds IDs of the mods NeoForge's FML loads itself as the host (ADR-0026); one launch has at most
     *     one host, so one of the two is empty
     */
    static LoadedModCounts count(
            Collection<String> polyModIds,
            Map<String, ? extends Collection<String>> foreignModIdsByLoader,
            Collection<String> forgeModIds,
            Collection<String> neoForgeModIds) {
        Collection<String> fabricModIds = foreignModIdsByLoader.get(FABRIC);
        Collection<String> quiltModIds = foreignModIdsByLoader.get(QUILT);
        return new LoadedModCounts(
                distinctMods(polyModIds),
                distinctMods(fabricModIds == null ? Set.of() : fabricModIds),
                distinctMods(forgeModIds),
                distinctMods(quiltModIds == null ? Set.of() : quiltModIds),
                distinctMods(neoForgeModIds));
    }

    private static int distinctMods(Collection<String> ids) {
        Set<String> mods = new HashSet<>(ids);
        mods.removeAll(NOT_MODS);
        return mods.size();
    }
}
