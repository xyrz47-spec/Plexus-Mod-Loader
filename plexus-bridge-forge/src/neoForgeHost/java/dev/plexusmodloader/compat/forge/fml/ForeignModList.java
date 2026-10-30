package dev.plexusmodloader.compat.forge.fml;

import dev.plexusmodloader.compat.forge.ForeignModIndex;
import java.util.HashMap;
import java.util.Map;
import net.neoforged.neoforgespi.language.IModFileInfo;

/**
 * Poly Mod Loader's answers to NeoForge's FML mod list for the mods it runs beside NeoForge (Poly Mods and other
 * loaders' mods; ADR-0025, ADR-0026). NeoForge's {@code ModList} calls {@link #getModFileById} and {@link #isLoaded} only
 * when its own answer is {@code null} or {@code false} (Poly Mod Loader's {@code ForgeModListPatch}, shape {@code
 * NEOFORGE}), so a NeoForge mod keeps its ID.
 *
 * <p>Lives in {@code neoforge-host.jar}'s module of the library layer, an automatic module that exports this package;
 * FML's {@code loader} module reads it.
 *
 * <p>Thread: {@link #install} runs once on the launching thread before FML starts; the lookups may run on any thread
 * afterwards.
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
     * The mod file of the Poly Mod or other loader's mod with this ID (its own or a provided one), or {@code null}, as
     * {@code ModList.getModFileById} answers for a missing mod.
     */
    public static IModFileInfo getModFileById(String id) {
        return id == null ? null : files.get(id);
    }

    /** Whether a Poly Mod or other loader's mod has this ID, its own or a provided one. */
    public static boolean isLoaded(String id) {
        return id != null && files.containsKey(id);
    }
}
