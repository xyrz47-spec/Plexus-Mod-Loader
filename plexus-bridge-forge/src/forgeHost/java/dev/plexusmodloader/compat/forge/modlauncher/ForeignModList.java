package dev.plexusmodloader.compat.forge.modlauncher;

import dev.plexusmodloader.compat.forge.ForeignModIndex;
import java.util.HashMap;
import java.util.Map;
import net.minecraftforge.forgespi.language.IModFileInfo;

/**
 * Plexus Mod Loader's answers to FML's mod list for the mods it runs beside Forge (ADR-0025). Forge's {@code ModList}
 * calls {@link #getModFileById} and {@link #isLoaded} only when its own answer is {@code null} or {@code false} (Plexus Mod
 * Loader's {@code ForgeModListPatch}), so a Forge mod keeps its ID.
 *
 * <p>Lives in the BOOT layer's {@code cpw.mods.modlauncher} module, which exports this package; FML's {@code fmlcore}, an
 * automatic module of the GAME layer, reads it.
 *
 * <p>Thread: {@link #install} runs once on the launching thread before any game class is loaded; the lookups may run
 * on any thread afterwards.
 */
public final class ForeignModList {

    private static volatile Map<String, ForeignModFileInfo> files = Map.of();

    private ForeignModList() {}

    /** Shows {@code mods} to FML's mod list, replacing what an earlier call showed. */
    static void install(ForeignModIndex mods) {
        Map<String, ForeignModFileInfo> byId = new HashMap<>();
        for (ForeignModIndex.ForeignMod mod : mods.mods()) {
            ForeignModFileInfo file = new ForeignModFileInfo(mod);
            mod.ids().forEach(id -> byId.put(id, file));
        }
        files = Map.copyOf(byId);
    }

    /**
     * The mod file of the Plexus Mod or other loader's mod with this ID (its own or a provided one), or {@code null}, as
     * {@code ModList.getModFileById} answers for a missing mod.
     */
    public static IModFileInfo getModFileById(String id) {
        return id == null ? null : files.get(id);
    }

    /** Whether a Plexus Mod or other loader's mod has this ID, its own or a provided one. */
    public static boolean isLoaded(String id) {
        return id != null && files.containsKey(id);
    }
}
