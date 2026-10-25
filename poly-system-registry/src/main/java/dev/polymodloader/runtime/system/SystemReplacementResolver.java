package dev.polymodloader.runtime.system;

import dev.polymodloader.api.system.SystemId;
import dev.polymodloader.runtime.metadata.ModMetadata;
import dev.polymodloader.runtime.metadata.SystemReplacementDeclaration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Decides which provider is active for each system, before any mod code runs (design doc §4.4, §11
 * steps 8–9). Rules:
 *
 * <ol>
 *   <li>No replacement declared — Poly Mod Loader's default provider.
 *   <li>Exactly one replacement — that provider.
 *   <li>Several replacements — the one marked {@code "override": true} wins, but only if its mod
 *       depends (directly or transitively) on every other replacing mod. Anything else is a
 *       conflict; Poly Mod Loader never guesses.
 * </ol>
 */
public final class SystemReplacementResolver {

    /**
     * The chosen replacement for a system.
     *
     * @param systemId the system
     * @param modId mod that provides the replacement
     * @param providerClass the provider class name
     * @param overriddenModIds mods whose replacement for the same system was overridden
     */
    public record SelectedReplacement(
            SystemId systemId, String modId, String providerClass, List<String> overriddenModIds) {
        public SelectedReplacement {
            overriddenModIds = List.copyOf(overriddenModIds);
        }
    }

    /**
     * Outcome of resolution.
     *
     * @param replacements chosen replacement per system; systems absent here use the default
     * @param problems every problem found
     */
    public record Result(Map<SystemId, SelectedReplacement> replacements, List<String> problems) {
        public Result {
            replacements = Map.copyOf(replacements);
            problems = List.copyOf(problems);
        }
    }

    private record Candidate(ModMetadata mod, SystemReplacementDeclaration declaration) {}

    /** Resolves the replacements declared by {@code mods} against {@code definitions}. */
    public Result resolve(List<ModMetadata> mods, List<SystemDefinition<?>> definitions) {
        Map<SystemId, SystemDefinition<?>> byId = new LinkedHashMap<>();
        definitions.forEach(definition -> byId.put(definition.id(), definition));
        Map<String, ModMetadata> modsById = new LinkedHashMap<>();
        mods.forEach(mod -> modsById.put(mod.id().value(), mod));

        List<String> problems = new ArrayList<>();
        Map<SystemId, List<Candidate>> candidates = new LinkedHashMap<>();
        for (ModMetadata mod : mods) {
            for (SystemReplacementDeclaration declaration : mod.replacesSystems()) {
                SystemId systemId = new SystemId(declaration.systemId());
                SystemDefinition<?> definition = byId.get(systemId);
                if (definition == null) {
                    problems.add(problem(
                            SystemProblemCode.UNKNOWN_SYSTEM,
                            mod,
                            "replaces unknown system " + systemId + "; known systems: " + byId.keySet()));
                } else if (!definition.replaceable()) {
                    problems.add(problem(
                            SystemProblemCode.NOT_REPLACEABLE, mod, "system " + systemId + " cannot be replaced"));
                } else {
                    candidates
                            .computeIfAbsent(systemId, id -> new ArrayList<>())
                            .add(new Candidate(mod, declaration));
                }
            }
        }

        Map<SystemId, SelectedReplacement> selected = new LinkedHashMap<>();
        for (Map.Entry<SystemId, List<Candidate>> entry : candidates.entrySet()) {
            List<Candidate> contenders = entry.getValue();
            if (contenders.size() == 1) {
                Candidate only = contenders.getFirst();
                selected.put(entry.getKey(), selection(entry.getKey(), only, List.of()));
                continue;
            }
            Optional<Candidate> winner = overridingWinner(contenders, modsById);
            if (winner.isPresent()) {
                List<String> overridden = contenders.stream()
                        .filter(candidate -> candidate != winner.get())
                        .map(candidate -> candidate.mod().id().value())
                        .toList();
                selected.put(entry.getKey(), selection(entry.getKey(), winner.get(), overridden));
                continue;
            }
            List<String> ids = contenders.stream()
                    .map(candidate -> candidate.mod().id().value())
                    .toList();
            for (Candidate candidate : contenders) {
                if (candidate.declaration().override()) {
                    problems.add(problem(
                            SystemProblemCode.INVALID_OVERRIDE,
                            candidate.mod(),
                            "declares override for " + entry.getKey() + " but does not depend on every other "
                                    + "replacing mod " + ids));
                }
            }
            problems.add(SystemProblemCode.CONFLICTING_REPLACEMENTS.code() + " system=" + entry.getKey() + ": mods "
                    + ids + " all replace it. Remove all but one, or let one mod depend on the others and set "
                    + "\"override\": true in its replacesSystems entry.");
        }
        return new Result(selected, problems);
    }

    private static Optional<Candidate> overridingWinner(List<Candidate> contenders, Map<String, ModMetadata> modsById) {
        List<Candidate> winners = new ArrayList<>();
        for (Candidate candidate : contenders) {
            if (!candidate.declaration().override()) {
                continue;
            }
            Set<String> reachable = transitiveDependencies(candidate.mod(), modsById);
            boolean dependsOnAllOthers = contenders.stream()
                    .filter(other -> other != candidate)
                    .allMatch(other -> reachable.contains(other.mod().id().value()));
            if (dependsOnAllOthers) {
                winners.add(candidate);
            }
        }
        return winners.size() == 1 ? Optional.of(winners.getFirst()) : Optional.empty();
    }

    private static Set<String> transitiveDependencies(ModMetadata mod, Map<String, ModMetadata> modsById) {
        Set<String> seen = new HashSet<>();
        Deque<String> pending = new ArrayDeque<>(mod.depends().keySet());
        while (!pending.isEmpty()) {
            String id = pending.pop();
            if (seen.add(id) && modsById.containsKey(id)) {
                pending.addAll(modsById.get(id).depends().keySet());
            }
        }
        return seen;
    }

    private static SelectedReplacement selection(SystemId systemId, Candidate candidate, List<String> overridden) {
        return new SelectedReplacement(
                systemId, candidate.mod().id().value(), candidate.declaration().providerClass(), overridden);
    }

    private static String problem(SystemProblemCode code, ModMetadata mod, String message) {
        return code.code() + " mod=" + mod.id() + ": " + message;
    }
}
