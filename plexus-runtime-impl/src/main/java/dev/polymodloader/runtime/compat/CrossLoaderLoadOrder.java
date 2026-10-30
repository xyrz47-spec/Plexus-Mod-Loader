package dev.polymodloader.runtime.compat;

import dev.polymodloader.compat.bridge.BridgedDependency;
import dev.polymodloader.runtime.discovery.ForeignModResolver.ForeignMod;
import dev.polymodloader.runtime.metadata.ModMetadata;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The order in which the entrypoints of every loaded mod run, native and foreign together: a mod after the
 * mods it requires, whatever their loader, ties broken by mod ID so every machine uses the same order.
 *
 * <p>Native dependency cycles are rejected earlier by {@link dev.polymodloader.runtime.dependency.ModDependencyResolver}.
 * Fabric allows cycles among its mods (it does not order entrypoints by dependency at all), so mods left in a
 * cycle that includes a foreign mod run after the others, in ID order.
 */
public final class CrossLoaderLoadOrder {

    /** One loaded mod: exactly one of {@code nativeMod} and {@code foreignMod} is set. */
    public record LoadedMod(ModMetadata nativeMod, ForeignMod foreignMod) {

        /** The mod's ID. */
        public String id() {
            return nativeMod != null
                    ? nativeMod.id().value()
                    : foreignMod.assessment().modId();
        }
    }

    private CrossLoaderLoadOrder() {}

    /**
     * Adds the IDs a relationship makes run first: a required or if-present one (only loaded mods are ordered, so an
     * if-present target that is not loaded adds nothing), and every member of such a group (ADR-0027).
     */
    private static void addRequiredIds(BridgedDependency dependency, List<String> ids) {
        if (dependency.kind() != BridgedDependency.Kind.REQUIRED
                && dependency.kind() != BridgedDependency.Kind.IF_PRESENT) {
            return;
        }
        if (dependency.isGroup()) {
            dependency.members().forEach(member -> addRequiredIds(member, ids));
        } else {
            ids.add(dependency.modId());
        }
    }

    /** Orders {@code natives} (already in valid native order) and {@code foreign} together. */
    public static List<LoadedMod> of(List<ModMetadata> natives, List<ForeignMod> foreign) {
        Map<String, LoadedMod> byKey = new TreeMap<>();
        Map<String, String> keyById = new HashMap<>();
        for (ModMetadata mod : natives) {
            String key = mod.id().value();
            byKey.put(key, new LoadedMod(mod, null));
            keyById.put(key, key);
        }
        for (ForeignMod mod : foreign) {
            String key = mod.assessment().modId() + "#" + mod.assessment().loader();
            byKey.put(key, new LoadedMod(null, mod));
            for (String id : mod.assessment().allIds()) {
                keyById.putIfAbsent(id, key);
            }
        }
        Map<String, List<String>> requires = new LinkedHashMap<>();
        for (Map.Entry<String, LoadedMod> entry : byKey.entrySet()) {
            List<String> ids = new ArrayList<>();
            LoadedMod mod = entry.getValue();
            if (mod.nativeMod() != null) {
                ids.addAll(mod.nativeMod().depends().keySet());
            } else {
                for (BridgedDependency dependency :
                        mod.foreignMod().assessment().dependencies()) {
                    addRequiredIds(dependency, ids);
                }
            }
            List<String> keys = new ArrayList<>();
            for (String id : ids) {
                String key = keyById.get(id);
                if (key != null && !key.equals(entry.getKey()) && !keys.contains(key)) {
                    keys.add(key);
                }
            }
            requires.put(entry.getKey(), keys);
        }
        Map<String, Integer> waiting = new HashMap<>();
        Map<String, List<String>> dependents = new HashMap<>();
        requires.forEach((key, needed) -> {
            waiting.put(key, needed.size());
            for (String dependency : needed) {
                dependents
                        .computeIfAbsent(dependency, ignored -> new ArrayList<>())
                        .add(key);
            }
        });
        TreeSet<String> ready = new TreeSet<>();
        waiting.forEach((key, count) -> {
            if (count == 0) {
                ready.add(key);
            }
        });
        List<LoadedMod> order = new ArrayList<>();
        while (!ready.isEmpty()) {
            String key = ready.pollFirst();
            order.add(byKey.get(key));
            for (String dependent : dependents.getOrDefault(key, List.of())) {
                if (waiting.merge(dependent, -1, Integer::sum) == 0) {
                    ready.add(dependent);
                }
            }
        }
        for (Map.Entry<String, LoadedMod> entry : byKey.entrySet()) {
            if (!order.contains(entry.getValue())) {
                order.add(entry.getValue());
            }
        }
        return order;
    }
}
