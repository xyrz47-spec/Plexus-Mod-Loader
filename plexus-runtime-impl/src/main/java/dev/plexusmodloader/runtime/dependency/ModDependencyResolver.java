package dev.plexusmodloader.runtime.dependency;

import dev.plexusmodloader.api.runtime.GameSide;
import dev.plexusmodloader.runtime.metadata.ModEnvironment;
import dev.plexusmodloader.runtime.metadata.ModMetadata;
import dev.plexusmodloader.runtime.metadata.version.MinecraftVersionPattern;
import dev.plexusmodloader.runtime.metadata.version.SemanticVersion;
import dev.plexusmodloader.runtime.metadata.version.VersionRange;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates the relationships between mods and computes their initialisation order: every mod comes
 * after the mods it depends on, ties broken by mod ID so the order is the same on every machine.
 * All problems are reported together.
 */
public final class ModDependencyResolver {

    /** ID under which the Plexus Mod Loader runtime itself can be depended on. */
    public static final String RUNTIME_MOD_ID = "plexus";

    /** Legacy runtime ID for backward compatibility with Poly mods. */
    public static final String LEGACY_RUNTIME_MOD_ID = "poly";

    private static final Set<String> RESERVED_IDS = Set.of(RUNTIME_MOD_ID, LEGACY_RUNTIME_MOD_ID, "minecraft", "java");

    /** {@code 1} or {@code 1.2}, optionally followed by a pre-release or build part. */
    private static final Pattern SHORT_VERSION = Pattern.compile("(\\d+)(?:\\.(\\d+))?([-+].*)?");

    /**
     * The outcome of resolution.
     *
     * @param loadOrder mods in initialisation order; empty when there are problems
     * @param problems every problem found
     */
    public record Result(List<ModMetadata> loadOrder, List<DependencyProblem> problems) {
        public Result {
            loadOrder = List.copyOf(loadOrder);
            problems = List.copyOf(problems);
        }
    }

    /**
     * Resolves {@code mods} for {@code side} of Minecraft {@code gameVersion} running on Poly Mod Loader
     * {@code runtimeVersion}.
     */
    public Result resolve(List<ModMetadata> mods, String gameVersion, GameSide side, SemanticVersion runtimeVersion) {
        return resolve(mods, gameVersion, side, runtimeVersion, Map.of(), Map.of());
    }

    /**
     * Resolves {@code mods} like {@link #resolve(List, String, GameSide, SemanticVersion)}, where a dependency
     * may also be a mod of another loader.
     *
     * @param foreignMods IDs of the loadable mods of other loaders (provided IDs included) to their version text
     * @param unavailableMods IDs of installed mods of other loaders that are not loaded, to the reason
     */
    public Result resolve(
            List<ModMetadata> mods,
            String gameVersion,
            GameSide side,
            SemanticVersion runtimeVersion,
            Map<String, String> foreignMods,
            Map<String, String> unavailableMods) {
        List<DependencyProblem> problems = new ArrayList<>();
        Map<String, ModMetadata> byId = new TreeMap<>();
        for (ModMetadata mod : mods) {
            String id = mod.id().value();
            if (RESERVED_IDS.contains(id)) {
                problems.add(new DependencyProblem(
                        DependencyProblemCode.RESERVED_MOD_ID, id, "'" + id + "' is reserved and cannot be a mod ID"));
                continue;
            }
            ModMetadata previous = byId.putIfAbsent(id, mod);
            if (previous != null) {
                problems.add(new DependencyProblem(
                        DependencyProblemCode.DUPLICATE_MOD_ID,
                        id,
                        "two mods use this ID (versions " + previous.version() + " and " + mod.version()
                                + "); remove one of them"));
            }
        }

        for (ModMetadata mod : byId.values()) {
            String id = mod.id().value();
            if (!MinecraftVersionPattern.anyMatches(mod.minecraftVersions(), gameVersion)) {
                problems.add(new DependencyProblem(
                        DependencyProblemCode.UNSUPPORTED_MINECRAFT_VERSION,
                        id,
                        "declares Minecraft " + mod.minecraftVersions() + " but the game is " + gameVersion));
            }
            if (mod.environment() == ModEnvironment.CLIENT && side == GameSide.DEDICATED_SERVER) {
                problems.add(new DependencyProblem(
                        DependencyProblemCode.WRONG_ENVIRONMENT,
                        id,
                        "is client-only and must not be loaded on a dedicated server; remove it from the server"));
            }
            if (mod.environment() == ModEnvironment.SERVER && side == GameSide.CLIENT) {
                problems.add(new DependencyProblem(
                        DependencyProblemCode.WRONG_ENVIRONMENT,
                        id,
                        "is dedicated-server-only and must not be loaded on the client"));
            }
            for (Map.Entry<String, VersionRange> dependency : mod.depends().entrySet()) {
                String dependencyId = dependency.getKey();
                VersionRange range = dependency.getValue();
                SemanticVersion available;
                if (dependencyId.equals(RUNTIME_MOD_ID) || dependencyId.equals(LEGACY_RUNTIME_MOD_ID)) {
                    available = runtimeVersion;
                } else if (byId.containsKey(dependencyId)) {
                    available = byId.get(dependencyId).version();
                } else if (foreignMods.containsKey(dependencyId)) {
                    String text = foreignMods.get(dependencyId);
                    Optional<SemanticVersion> parsed = lenientVersion(text);
                    if (parsed.isEmpty()) {
                        if (!range.toString().equals("*")) {
                            problems.add(new DependencyProblem(
                                    DependencyProblemCode.DEPENDENCY_VERSION_MISMATCH,
                                    id,
                                    "requires '" + dependencyId + "' " + range + " but its version '" + text
                                            + "' is not a semantic version; write \"*\" to accept any version"));
                        }
                        continue;
                    }
                    available = parsed.get();
                } else if (unavailableMods.containsKey(dependencyId)) {
                    problems.add(new DependencyProblem(
                            DependencyProblemCode.UNAVAILABLE_DEPENDENCY,
                            id,
                            "requires mod '" + dependencyId + "' " + range + ", which is installed but cannot be"
                                    + " loaded: " + unavailableMods.get(dependencyId)));
                    continue;
                } else {
                    problems.add(new DependencyProblem(
                            DependencyProblemCode.MISSING_DEPENDENCY,
                            id,
                            "requires mod '" + dependencyId + "' " + range + ", which is not installed"));
                    continue;
                }
                if (!range.contains(available)) {
                    problems.add(new DependencyProblem(
                            DependencyProblemCode.DEPENDENCY_VERSION_MISMATCH,
                            id,
                            "requires '" + dependencyId + "' " + range + " but version " + available
                                    + " is installed"));
                }
            }
            for (String conflict : mod.conflicts()) {
                if (byId.containsKey(conflict) || foreignMods.containsKey(conflict)) {
                    problems.add(new DependencyProblem(
                            DependencyProblemCode.CONFLICTING_MODS,
                            id,
                            "declares a conflict with '" + conflict + "', which is installed; remove one of them"));
                }
            }
        }

        List<ModMetadata> order = topologicalOrder(byId, problems);
        if (!problems.isEmpty()) {
            return new Result(List.of(), problems);
        }
        return new Result(order, problems);
    }

    /**
     * {@code text} as a semantic version, also accepting the short forms other loaders allow: {@code 1.2}
     * becomes {@code 1.2.0}. Returns empty for anything else.
     */
    static Optional<SemanticVersion> lenientVersion(String text) {
        Optional<SemanticVersion> exact = SemanticVersion.parse(text);
        if (exact.isPresent()) {
            return exact;
        }
        Matcher shortForm = SHORT_VERSION.matcher(text);
        if (shortForm.matches()) {
            String minor = shortForm.group(2) == null ? "0" : shortForm.group(2);
            String rest = shortForm.group(3) == null ? "" : shortForm.group(3);
            return SemanticVersion.parse(shortForm.group(1) + "." + minor + ".0" + rest);
        }
        return Optional.empty();
    }

    /** Kahn's algorithm over mod-to-mod dependencies; mods left over are part of a cycle. */
    private static List<ModMetadata> topologicalOrder(Map<String, ModMetadata> byId, List<DependencyProblem> problems) {
        Map<String, Integer> unresolvedDependencies = new HashMap<>();
        Map<String, List<String>> dependents = new HashMap<>();
        for (ModMetadata mod : byId.values()) {
            String id = mod.id().value();
            int count = 0;
            for (String dependencyId : mod.depends().keySet()) {
                if (byId.containsKey(dependencyId)) {
                    count++;
                    dependents
                            .computeIfAbsent(dependencyId, key -> new ArrayList<>())
                            .add(id);
                }
            }
            unresolvedDependencies.put(id, count);
        }
        TreeSet<String> ready = new TreeSet<>();
        unresolvedDependencies.forEach((id, count) -> {
            if (count == 0) {
                ready.add(id);
            }
        });
        List<ModMetadata> order = new ArrayList<>();
        while (!ready.isEmpty()) {
            String id = ready.pollFirst();
            order.add(byId.get(id));
            for (String dependent : dependents.getOrDefault(id, List.of())) {
                if (unresolvedDependencies.merge(dependent, -1, Integer::sum) == 0) {
                    ready.add(dependent);
                }
            }
        }
        if (order.size() < byId.size()) {
            Map<String, ModMetadata> inCycle = new LinkedHashMap<>(byId);
            order.forEach(mod -> inCycle.remove(mod.id().value()));
            for (String id : inCycle.keySet()) {
                problems.add(new DependencyProblem(
                        DependencyProblemCode.DEPENDENCY_CYCLE,
                        id,
                        "is part of a dependency cycle among " + inCycle.keySet()));
            }
        }
        return order;
    }
}
