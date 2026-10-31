package dev.plexusmodloader.runtime.lifecycle;

import dev.plexusmodloader.api.lifecycle.LoadedMods;
import dev.plexusmodloader.compat.bridge.BridgeLaunchContext;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** {@link LoadedMods} over the mods of every loader that the runtime hands the bridges ({@link BridgeLaunchContext}). */
public final class RuntimeLoadedMods implements LoadedMods {

    private final List<Mod> all;
    private final Map<String, Mod> byId;

    private RuntimeLoadedMods(List<Mod> all, Map<String, Mod> byId) {
        this.all = all;
        this.byId = byId;
    }

    /** @param mods every loaded mod, as {@link BridgeLaunchContext#mods()} lists them */
    public static RuntimeLoadedMods of(List<BridgeLaunchContext.LoadedMod> mods) {
        List<Mod> all = mods.stream()
                .map(mod -> new Mod(mod.modId(), mod.version(), mod.name(), loader(mod), mod.providedIds()))
                .toList();
        Map<String, Mod> byId = new HashMap<>();
        // Own IDs first, so a mod keeps its ID when another mod provides it.
        all.forEach(mod -> byId.putIfAbsent(mod.modId(), mod));
        all.forEach(mod -> mod.providedIds().forEach(id -> byId.putIfAbsent(id, mod)));
        return new RuntimeLoadedMods(all, Map.copyOf(byId));
    }

    private static String loader(BridgeLaunchContext.LoadedMod mod) {
        return mod.loader().equals("Plexus Mod Loader")
                ? "plexus"
                : mod.loader().toLowerCase(Locale.ROOT);
    }

    @Override
    public boolean isLoaded(String modId) {
        return byId.containsKey(modId);
    }

    @Override
    public Optional<Mod> find(String modId) {
        return Optional.ofNullable(byId.get(modId));
    }

    @Override
    public List<Mod> all() {
        return all;
    }
}
