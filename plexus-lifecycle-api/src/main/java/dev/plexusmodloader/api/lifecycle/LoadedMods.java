package dev.plexusmodloader.api.lifecycle;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;
import java.util.List;
import java.util.Optional;

/**
 * The mods loaded in this game, of every loader: Plexus Mods, the mods of other loaders that Plexus Mod Loader runs
 * (Fabric), and the mods a host loader runs (MinecraftForge's, when Forge runs the game, ADR-0021). A Plexus Mod reads it
 * to switch an optional integration on or off, as Fabric mods use {@code FabricLoader.isModLoaded} and Forge mods
 * {@code ModList.isLoaded}.
 *
 * <p>Only loaded mods are listed: mods that were blocked or left out are not, nor are the game, Java and the loaders
 * themselves ({@code minecraft}, {@code java}, {@code fabricloader}, {@code forge}).
 *
 * <p><b>When:</b> complete from the first entrypoint on; the list does not change while the game runs.
 *
 * <p><b>Thread:</b> safe to read from any thread.
 *
 * <p>Implementations are provided by Plexus Mod Loader; mods do not implement this interface.
 */
@ApiStability(StabilityLevel.PREVIEW)
public interface LoadedMods {

    /**
     * Whether a mod with {@code modId} is loaded, by its own ID or an ID it provides ({@code provides} in {@code
     * fabric.mod.json}).
     */
    boolean isLoaded(String modId);

    /**
     * The loaded mod with {@code modId}: the mod whose own ID it is, otherwise the first mod that provides it; empty when
     * none is loaded.
     */
    Optional<Mod> find(String modId);

    /** Every loaded mod: first those a host loader runs, then the others in the order their entrypoints run. */
    List<Mod> all();

    /**
     * One loaded mod.
     *
     * @param modId its ID
     * @param version its version text, as its own metadata declares it
     * @param name its display name
     * @param loader the loader that runs it, in lower case: {@code plexus}, {@code fabric}, {@code minecraftforge}; more
     *     loaders may appear in later versions
     * @param providedIds further IDs it answers to
     */
    @ApiStability(StabilityLevel.PREVIEW)
    record Mod(String modId, String version, String name, String loader, List<String> providedIds) {

        public Mod {
            providedIds = List.copyOf(providedIds);
        }
    }
}
