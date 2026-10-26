package dev.polymodloader.runtime.compat;

import dev.polymodloader.compat.bridge.BridgeAssessment;
import dev.polymodloader.compat.bridge.BridgedDependency;
import dev.polymodloader.compat.bridge.CompatibilityFinding;
import dev.polymodloader.compat.bridge.ModLoaderBridge;
import dev.polymodloader.runtime.discovery.ForeignModResolver.ForeignMod;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Decides which mods of other loaders load, given every installed mod of every loader (design doc §13).
 * A foreign mod loads only when each of its required dependencies is a loadable mod (of any loader) or part
 * of the environment ({@code minecraft}, {@code java}, the bridge's built-in IDs) in an accepted version,
 * nothing it {@code breaks} is present, no other loadable mod uses its ID, and its classes link against this
 * launch ({@link ModLinkVerifier}). Blocking one mod can block the mods that need it; that is repeated until
 * nothing changes, so every blocked mod says why, including "installed but blocked" chains.
 *
 * <p>Nested mods (jar-in-jar, {@link ForeignMod#nested()}) follow Fabric Loader: one loads only while the jar it
 * came from loads, and when several copies of a mod exist the one installed directly is used, otherwise the
 * newest nested copy ({@link ModLoaderBridge#compareVersions}). The other copies are not loaded.
 *
 * <p>Relationships follow {@link BridgedDependency}: {@link BridgedDependency.Kind#IF_PRESENT} is checked only when a
 * mod with the ID is installed, a group is met by any member (a {@code BREAKS} group breaks only when every member
 * matches), a relationship whose {@code unless} condition is met does not apply, and one marked {@code
 * skipWhenOtherSideOnly} does not apply when every installed mod with the ID is left out only for the other side. A
 * provided ID has the version the mod declares for it ({@link BridgeAssessment#versionOf}). A nested mod that loads
 * only if required ({@link BridgeAssessment#loadOnlyIfRequired()}, Quilt {@code load_type} {@code if_required}) loads
 * only while a loaded mod reaches it through required or matching if-present relationships (ADR-0027).
 *
 * <p>Findings added here:
 *
 * <ul>
 *   <li>{@code POLY-COMPAT-114} (blocking): a required dependency is missing, not loaded, or the wrong version
 *       (also {@code minecraft} and {@code java}), or an if-present one is installed in a version that does not match
 *       or is not loaded
 *   <li>{@code POLY-COMPAT-115} (blocking): a mod it declares to break is installed in a matching version
 *   <li>{@code POLY-COMPAT-116} (warning): a mod it declares a conflict with is installed
 *   <li>{@code POLY-COMPAT-117} (blocking): another directly installed mod uses the same ID
 *   <li>{@code POLY-COMPAT-121} (blocking): the jar a nested mod came from is not loaded
 *   <li>{@code POLY-COMPAT-122} (blocking): another copy of the same nested mod is used instead
 *   <li>{@code POLY-COMPAT-124} (warning): a loaded mod some of whose nested mods are not loaded
 *   <li>{@code POLY-COMPAT-307} (blocking, not an error): a nested mod that loads only if required, which no loaded mod
 *       requires; it does not count for {@code 124}
 * </ul>
 *
 * <p>Poly Mods (native mods) are not decided here; {@link dev.polymodloader.runtime.dependency.ModDependencyResolver} checks
 * them with the result of this class.
 */
public final class CrossLoaderModResolver {

    /** Checks the classes of one foreign mod against the jars that will be loaded with it. */
    @FunctionalInterface
    public interface LinkCheck {
        List<CompatibilityFinding> verify(ForeignMod mod, Collection<Path> otherJars);
    }

    /**
     * @param mods every foreign mod in input order, with its final assessment
     * @param loadable the foreign mods that load
     * @param loadableVersions every ID (provided ones included) of the loadable foreign mods to its version
     * @param unavailable every ID of an installed foreign mod that does not load, to the reason
     */
    public record Result(
            List<ForeignMod> mods,
            List<ForeignMod> loadable,
            Map<String, String> loadableVersions,
            Map<String, String> unavailable) {}

    /**
     * Resolves {@code foreign} together with the Poly Mods.
     *
     * @param nativeVersions ID of each Poly Mod to its version
     * @param nativeJars the Poly Mod jars, which foreign mods may link against
     * @param gameVersion Minecraft version of this launch
     * @param javaVersion Java feature version of this launch, such as {@code 25}
     * @param linkCheck class checks for mods that pass the metadata checks; {@code null} skips them
     */
    public Result resolve(
            List<ForeignMod> foreign,
            Map<String, String> nativeVersions,
            Collection<Path> nativeJars,
            String gameVersion,
            String javaVersion,
            LinkCheck linkCheck) {
        return resolve(foreign, nativeVersions, Map.of(), nativeJars, gameVersion, javaVersion, linkCheck);
    }

    /**
     * Resolves {@code foreign} together with the Poly Mods, where the host's builds of left-out mods stand in for them.
     *
     * @param hostBuilds the IDs of mods left out because the host runs its own build of them ({@code
     *     POLY-COMPAT-139}, ADR-0026 37), each to the version of the host's build, which meets the dependencies on them
     * @see #resolve(List, Map, Collection, String, String, LinkCheck)
     */
    public Result resolve(
            List<ForeignMod> foreign,
            Map<String, String> nativeVersions,
            Map<String, String> hostBuilds,
            Collection<Path> nativeJars,
            String gameVersion,
            String javaVersion,
            LinkCheck linkCheck) {
        Resolution resolution =
                new Resolution(new ArrayList<>(foreign), nativeVersions, hostBuilds, gameVersion, javaVersion);
        resolution.blockDuplicates();
        Map<Integer, ForeignMod> beforeDependencyChecks = new HashMap<>();
        while (true) {
            boolean changed;
            do {
                changed = false;
                resolution.refresh();
                for (int i = 0; i < resolution.mods.size(); i++) {
                    if (resolution.notRequired.contains(i)) {
                        resolution.mods.set(
                                i, resolution.mods.get(i).withFindings(List.of(notRequired(i, resolution))));
                        changed = true;
                        continue;
                    }
                    if (!resolution.active(i)) {
                        continue;
                    }
                    ForeignMod mod = resolution.mods.get(i);
                    List<CompatibilityFinding> problems = resolution.problems(mod);
                    if (!problems.isEmpty()) {
                        beforeDependencyChecks.put(i, mod);
                        resolution.mods.set(i, mod.withFindings(problems));
                        changed = true;
                    }
                }
            } while (changed);
            if (linkCheck == null) {
                break;
            }
            boolean linkBlocked = false;
            Map<Integer, List<CompatibilityFinding>> notes = new HashMap<>();
            for (int i = 0; i < resolution.mods.size(); i++) {
                if (!resolution.active(i)) {
                    continue;
                }
                ForeignMod mod = resolution.mods.get(i);
                List<Path> others = new ArrayList<>(nativeJars);
                for (int j = 0; j < resolution.mods.size(); j++) {
                    if (j != i && resolution.active(j)) {
                        others.add(resolution.mods.get(j).jar());
                    }
                }
                List<CompatibilityFinding> findings = linkCheck.verify(mod, others);
                if (findings.stream().anyMatch(CompatibilityFinding::blocking)) {
                    resolution.mods.set(i, mod.withFindings(findings));
                    linkBlocked = true;
                } else {
                    notes.put(i, findings);
                }
            }
            if (!linkBlocked) {
                notes.forEach((index, findings) ->
                        resolution.mods.set(index, resolution.mods.get(index).withFindings(findings)));
                break;
            }
        }
        // A mod is blocked as soon as one dependency fails, possibly before another of its dependencies was
        // blocked too; give every such mod the full list of reasons, as of the final state.
        resolution.refresh();
        Map<Integer, List<CompatibilityFinding>> finalReasons = new HashMap<>();
        beforeDependencyChecks.forEach((index, original) -> finalReasons.put(index, resolution.problems(original)));
        finalReasons.forEach((index, problems) ->
                resolution.mods.set(index, beforeDependencyChecks.get(index).withFindings(problems)));
        resolution.markSuperseded();
        noteMissingNestedMods(resolution.mods);
        addConflictWarnings(resolution.mods, nativeVersions);
        List<ForeignMod> loadable = resolution.mods.stream()
                .filter(mod -> mod.assessment().status().loadable())
                .toList();
        Map<String, String> versions = new TreeMap<>();
        for (ForeignMod mod : loadable) {
            for (String id : mod.assessment().allIds()) {
                versions.put(id, mod.assessment().versionOf(id));
            }
        }
        return new Result(List.copyOf(resolution.mods), loadable, versions, unavailable(resolution.mods));
    }

    /** The {@code POLY-COMPAT-307} finding of the nested mod at {@code index}, which no loaded mod requires. */
    private static CompatibilityFinding notRequired(int index, Resolution resolution) {
        ForeignMod mod = resolution.mods.get(index);
        return new CompatibilityFinding(
                "POLY-COMPAT-307",
                true,
                "load type",
                "it is nested in " + mod.containerJar().getFileName() + " and loads only when a loaded mod requires it"
                        + " (Quilt load_type if_required, the default); no loaded mod does, so it is not loaded, as"
                        + " under Quilt Loader");
    }

    /** The mods being decided and what is derived from them in the current round. */
    private static final class Resolution {

        final List<ForeignMod> mods;
        final Map<String, String> nativeVersions;
        /** IDs of left-out mods whose host build stands in for them, to that build's version. */
        final Map<String, String> hostBuilds;

        final String gameVersion;
        final String javaVersion;
        /** Index of each loadable copy that another copy of the same mod replaces, to the reason. */
        Map<Integer, String> superseded = new HashMap<>();
        /** Index of each loadable nested mod that loads only if required and that no loaded mod requires. */
        Set<Integer> notRequired = Set.of();

        Map<String, ForeignMod> providers = new HashMap<>();
        Map<String, String> unavailable = new TreeMap<>();
        Set<Path> activeJars = new HashSet<>();

        Resolution(
                List<ForeignMod> mods,
                Map<String, String> nativeVersions,
                Map<String, String> hostBuilds,
                String gameVersion,
                String javaVersion) {
            this.mods = mods;
            this.nativeVersions = nativeVersions;
            this.hostBuilds = Map.copyOf(hostBuilds);
            this.gameVersion = gameVersion;
            this.javaVersion = javaVersion;
        }

        boolean active(int index) {
            return mods.get(index).assessment().status().loadable()
                    && !superseded.containsKey(index)
                    && !notRequired.contains(index);
        }

        /** Recomputes which copies are used and what the active mods provide. */
        void refresh() {
            superseded = chooseCopies();
            notRequired = notRequired();
            providers = new HashMap<>();
            activeJars = new HashSet<>();
            for (int i = 0; i < mods.size(); i++) {
                if (active(i)) {
                    activeJars.add(mods.get(i).jar());
                    for (String id : mods.get(i).assessment().allIds()) {
                        providers.putIfAbsent(id, mods.get(i));
                    }
                }
            }
            unavailable = unavailable(mods);
        }

        /** Whether the mod at {@code index} is nested and loads only while a loaded mod requires it. */
        private boolean onlyIfRequired(int index) {
            return mods.get(index).nested() && mods.get(index).assessment().loadOnlyIfRequired();
        }

        /**
         * The loadable, unreplaced nested mods that load only if required and that no other such mod reaches: starting
         * from every other loadable mod, a required relationship (or a group member) reaches the mods with its ID, an
         * if-present one only those in a matching version, as Quilt Loader's resolver pulls them in.
         */
        private Set<Integer> notRequired() {
            Set<Integer> candidates = new TreeSet<>();
            Set<Integer> reached = new HashSet<>();
            Deque<Integer> queue = new ArrayDeque<>();
            for (int i = 0; i < mods.size(); i++) {
                if (!mods.get(i).assessment().status().loadable() || superseded.containsKey(i)) {
                    continue;
                }
                if (onlyIfRequired(i)) {
                    candidates.add(i);
                } else {
                    reached.add(i);
                    queue.add(i);
                }
            }
            if (candidates.isEmpty()) {
                return Set.of();
            }
            while (!queue.isEmpty()) {
                for (BridgedDependency dependency :
                        mods.get(queue.poll()).assessment().dependencies()) {
                    reach(dependency, candidates, reached, queue);
                }
            }
            candidates.removeAll(reached);
            return Set.copyOf(candidates);
        }

        private void reach(
                BridgedDependency dependency, Set<Integer> candidates, Set<Integer> reached, Deque<Integer> queue) {
            if (dependency.kind() != BridgedDependency.Kind.REQUIRED
                    && dependency.kind() != BridgedDependency.Kind.IF_PRESENT) {
                return;
            }
            if (dependency.isGroup()) {
                dependency.members().forEach(member -> reach(member, candidates, reached, queue));
                return;
            }
            for (int candidate : candidates) {
                BridgeAssessment assessment = mods.get(candidate).assessment();
                if (!reached.contains(candidate)
                        && assessment.allIds().contains(dependency.modId())
                        && (dependency.kind() == BridgedDependency.Kind.REQUIRED
                                || dependency.accepts().test(assessment.versionOf(dependency.modId())))) {
                    reached.add(candidate);
                    queue.add(candidate);
                }
            }
        }

        /**
         * For each ID with several loadable copies: a directly installed one (or a Poly Mod) wins over nested
         * ones, and among nested ones the newest wins.
         */
        private Map<Integer, String> chooseCopies() {
            Map<String, List<Integer>> copies = new LinkedHashMap<>();
            for (int i = 0; i < mods.size(); i++) {
                if (mods.get(i).assessment().status().loadable()) {
                    for (String id : mods.get(i).assessment().allIds()) {
                        List<Integer> indexes = copies.computeIfAbsent(id, key -> new ArrayList<>());
                        if (!indexes.contains(i)) {
                            indexes.add(i);
                        }
                    }
                }
            }
            Map<Integer, String> replaced = new HashMap<>();
            for (Map.Entry<String, List<Integer>> entry : copies.entrySet()) {
                String id = entry.getKey();
                List<Integer> nested = entry.getValue().stream()
                        .filter(index -> mods.get(index).nested())
                        .toList();
                if (nested.isEmpty()) {
                    continue;
                }
                if (nativeVersions.containsKey(id)) {
                    nested.forEach(index -> replaced.putIfAbsent(
                            index, "the Poly Mod '" + id + "' " + nativeVersions.get(id) + " is used instead"));
                    continue;
                }
                if (hostBuilds.containsKey(id)) {
                    nested.forEach(index -> replaced.putIfAbsent(
                            index, "the host build '" + id + "' " + hostBuilds.get(id) + " is used instead"));
                    continue;
                }
                // A library the bridge itself provides on Poly Mod Loader's class path (Fabric's MixinExtras): the
                // game class loader never defines a mod's copy of its classes, so the copy cannot be used.
                for (int index : nested) {
                    Map<String, String> builtIn = mods.get(index).bridge().builtInMods();
                    if (builtIn.containsKey(id)) {
                        replaced.putIfAbsent(
                                index,
                                "Poly Mod Loader's built-in '" + id + "' " + builtIn.get(id) + " is used instead");
                    }
                }
                if (nested.stream().allMatch(replaced::containsKey)) {
                    continue;
                }
                Integer direct = entry.getValue().stream()
                        .filter(index -> !mods.get(index).nested())
                        .findFirst()
                        .orElse(null);
                int winner = direct != null ? direct : newest(id, nested);
                ForeignMod chosen = mods.get(winner);
                for (int index : nested) {
                    if (index != winner) {
                        replaced.putIfAbsent(
                                index,
                                "another copy of '" + id + "' is used: "
                                        + chosen.assessment().version() + " from "
                                        + chosen.jar().getFileName());
                    }
                }
            }
            return replaced;
        }

        private int newest(String id, List<Integer> indexes) {
            ModLoaderBridge order = versionOrder(indexes);
            int best = indexes.getFirst();
            for (int index : indexes) {
                if (order.compareVersions(
                                mods.get(index).assessment().versionOf(id),
                                mods.get(best).assessment().versionOf(id))
                        > 0) {
                    best = index;
                }
            }
            return best;
        }

        /**
         * The bridge that orders copies of one mod: theirs when they all come from one bridge, otherwise one whose
         * loader reads every copy's metadata in nested jars (Quilt, which also orders Fabric versions with its own
         * rules), otherwise the first copy's.
         */
        private ModLoaderBridge versionOrder(List<Integer> indexes) {
            Set<String> metadataPaths = new HashSet<>();
            indexes.forEach(index -> metadataPaths.add(mods.get(index).bridge().metadataPath()));
            ModLoaderBridge first = mods.get(indexes.getFirst()).bridge();
            if (metadataPaths.size() == 1) {
                return first;
            }
            for (int index : indexes) {
                ModLoaderBridge bridge = mods.get(index).bridge();
                if (bridge.nestedMetadataPaths().containsAll(metadataPaths)) {
                    return bridge;
                }
            }
            return first;
        }

        /** Gives every still-loadable replaced copy its {@code POLY-COMPAT-122} finding. */
        void markSuperseded() {
            Map<Integer, String> replaced = chooseCopies();
            replaced.forEach((index, reason) -> mods.set(
                    index,
                    mods.get(index)
                            .withFindings(List.of(
                                    new CompatibilityFinding("POLY-COMPAT-122", true, "nested copy", reason)))));
        }

        /** A directly installed foreign mod whose ID (or provided ID) another installed mod also uses is blocked. */
        void blockDuplicates() {
            Map<String, List<Integer>> owners = new LinkedHashMap<>();
            for (int i = 0; i < mods.size(); i++) {
                if (mods.get(i).assessment().status().loadable() && !mods.get(i).nested()) {
                    for (String id : mods.get(i).assessment().allIds()) {
                        List<Integer> indexes = owners.computeIfAbsent(id, key -> new ArrayList<>());
                        if (!indexes.contains(i)) {
                            indexes.add(i);
                        }
                    }
                }
            }
            for (Map.Entry<String, List<Integer>> owner : owners.entrySet()) {
                String id = owner.getKey();
                List<Integer> indexes = owner.getValue();
                String reason;
                if (nativeVersions.containsKey(id)) {
                    reason = "the ID '" + id + "' is also the ID of an installed Poly Mod; remove one of" + " them";
                } else if (indexes.size() > 1) {
                    reason = "the ID '" + id + "' is used by " + indexes.size() + " installed jars ("
                            + String.join(
                                    ", ",
                                    indexes.stream()
                                            .map(index -> mods.get(index)
                                                    .jar()
                                                    .getFileName()
                                                    .toString())
                                            .toList())
                            + "); keep only one";
                } else {
                    continue;
                }
                for (int index : indexes) {
                    mods.set(
                            index,
                            mods.get(index)
                                    .withFindings(List.of(new CompatibilityFinding(
                                            "POLY-COMPAT-117", true, "duplicate ID", reason))));
                }
            }
        }

        /** The container and dependency problems of {@code mod} in the current round. */
        List<CompatibilityFinding> problems(ForeignMod mod) {
            List<CompatibilityFinding> problems = new ArrayList<>();
            if (mod.nested() && !activeJars.contains(mod.containerJar())) {
                problems.add(new CompatibilityFinding(
                        "POLY-COMPAT-121",
                        true,
                        "nested jar",
                        "it is inside " + mod.containerJar().getFileName() + ", which is not loaded"
                                + containerReason(mod.containerJar())));
            }
            problems.addAll(dependencyProblems(mod));
            return problems;
        }

        private String containerReason(Path container) {
            for (ForeignMod candidate : mods) {
                if (candidate.jar().equals(container)
                        && !candidate.assessment().status().loadable()) {
                    return " (" + candidate.assessment().blockingCodes() + ")";
                }
            }
            return "";
        }

        private List<CompatibilityFinding> dependencyProblems(ForeignMod mod) {
            List<CompatibilityFinding> problems = new ArrayList<>();
            for (BridgedDependency dependency : mod.assessment().dependencies()) {
                switch (dependency.kind()) {
                    case REQUIRED, IF_PRESENT ->
                        unmet(dependency, mod)
                                .ifPresent(reason -> problems.add(
                                        new CompatibilityFinding("POLY-COMPAT-114", true, "dependencies", reason)));
                    case BREAKS ->
                        broken(dependency, mod)
                                .ifPresent(reason -> problems.add(
                                        new CompatibilityFinding("POLY-COMPAT-115", true, "breaks", reason)));
                    default -> {}
                }
            }
            return problems;
        }

        /** Why a required or if-present relationship is not met, or empty when it is met or does not apply. */
        private Optional<String> unmet(BridgedDependency dependency, ForeignMod mod) {
            if (unlessMet(dependency, mod)) {
                return Optional.empty();
            }
            if (dependency.isGroup()) {
                List<String> reasons = new ArrayList<>();
                for (BridgedDependency member : dependency.members()) {
                    Optional<String> reason = unmet(member, mod);
                    if (reason.isEmpty()) {
                        return Optional.empty();
                    }
                    reasons.add(reason.get());
                }
                return Optional.of("requires " + dependency.requirement() + ", but none of them is met: "
                        + String.join("; ", reasons));
            }
            if (dependency.skipWhenOtherSideOnly() && otherSideOnly(dependency.modId())) {
                return Optional.empty();
            }
            Provider provider = provider(dependency.modId(), mod);
            String name = displayName(dependency.modId());
            if (provider.version() == null) {
                if (dependency.kind() == BridgedDependency.Kind.IF_PRESENT) {
                    if (!matchingButNotLoaded(dependency)) {
                        return Optional.empty();
                    }
                    return Optional.of("requires " + name + " " + dependency.requirement()
                            + " when it is installed, and a matching version is installed but not loaded: "
                            + provider.unavailableReason());
                }
                return Optional.of("requires " + name + " " + dependency.requirement() + ", "
                        + (provider.unavailableReason() == null
                                ? "which is not installed"
                                : "which is installed but not loaded: " + provider.unavailableReason()));
            }
            if (!dependency.accepts().test(provider.version())) {
                return Optional.of("requires " + name + " " + dependency.requirement() + ", but " + provider.version()
                        + " is "
                        + (dependency.modId().equals("minecraft")
                                ? "running (the mod was built for another Minecraft version)"
                                : dependency.modId().equals("java") ? "running" : "installed"));
            }
            return Optional.empty();
        }

        /** Why a breaks relationship blocks the mod, or empty when nothing it breaks is present or it does not apply. */
        private Optional<String> broken(BridgedDependency dependency, ForeignMod mod) {
            if (unlessMet(dependency, mod)) {
                return Optional.empty();
            }
            if (dependency.isGroup()) {
                for (BridgedDependency member : dependency.members()) {
                    if (broken(member, mod).isEmpty()) {
                        return Optional.empty();
                    }
                }
                return Optional.of("declares that it breaks " + dependency.requirement()
                        + " together, and all of them are installed; remove one of them");
            }
            Provider provider = provider(dependency.modId(), mod);
            if (provider.version() != null && dependency.accepts().test(provider.version())) {
                return Optional.of("declares that it breaks " + displayName(dependency.modId()) + " "
                        + dependency.requirement() + ", and version " + provider.version()
                        + " is installed; remove one of them");
            }
            return Optional.empty();
        }

        /** Whether any {@code unless} condition of {@code dependency} is met as a requirement. */
        private boolean unlessMet(BridgedDependency dependency, ForeignMod mod) {
            return dependency.unless().stream().anyMatch(condition -> metAsRequirement(condition, mod));
        }

        private boolean metAsRequirement(BridgedDependency condition, ForeignMod mod) {
            if (condition.isGroup()) {
                return condition.members().stream().anyMatch(member -> metAsRequirement(member, mod));
            }
            String version = provider(condition.modId(), mod).version();
            return version != null && condition.accepts().test(version);
        }

        /**
         * Whether every installed mod with {@code id} is left out only because it is for the other side (each blocking
         * finding is about the environment), and there is at least one.
         */
        private boolean otherSideOnly(String id) {
            if (nativeVersions.containsKey(id)) {
                return false;
            }
            List<ForeignMod> installed = mods.stream()
                    .filter(candidate -> candidate.assessment().allIds().contains(id))
                    .toList();
            return !installed.isEmpty()
                    && installed.stream()
                            .allMatch(candidate ->
                                    !candidate.assessment().status().loadable()
                                            && candidate.assessment().findings().stream()
                                                    .filter(CompatibilityFinding::blocking)
                                                    .allMatch(finding ->
                                                            finding.feature().equals("environment")));
        }

        /** Whether a mod with the relationship's ID is installed in a matching version but does not load. */
        private boolean matchingButNotLoaded(BridgedDependency dependency) {
            return mods.stream()
                    .filter(candidate -> !candidate.assessment().status().loadable())
                    .filter(candidate -> candidate.assessment().allIds().contains(dependency.modId()))
                    .anyMatch(candidate ->
                            dependency.accepts().test(candidate.assessment().versionOf(dependency.modId())));
        }

        private Provider provider(String id, ForeignMod mod) {
            ModLoaderBridge bridge = mod.bridge();
            if (id.equals("minecraft")) {
                return new Provider(gameVersion, null);
            }
            if (id.equals("java")) {
                return new Provider(javaVersion, null);
            }
            if (bridge.builtInMods().containsKey(id)) {
                return new Provider(bridge.builtInMods().get(id), null);
            }
            if (bridge.unavailableBuiltInMods().containsKey(id)) {
                return new Provider(null, bridge.unavailableBuiltInMods().get(id));
            }
            if (nativeVersions.containsKey(id)) {
                return new Provider(nativeVersions.get(id), null);
            }
            ForeignMod other = providers.get(id);
            if (other != null && other != mod) {
                return new Provider(other.assessment().versionOf(id), null);
            }
            if (hostBuilds.containsKey(id)) {
                return new Provider(hostBuilds.get(id), null);
            }
            return new Provider(null, unavailable.get(id));
        }
    }

    /** Codes of nested mods that are left out on purpose: another copy is used, or no loaded mod requires it. */
    private static final Set<String> NOT_MISSING = Set.of("POLY-COMPAT-122", "POLY-COMPAT-307");

    /**
     * A loaded mod whose own nested mods are not all loaded works only in part (Fabric API with most of its
     * modules blocked, for example): it gets a {@code POLY-COMPAT-124} note naming them. Copies replaced by
     * another copy of the same mod, nested mods no loaded mod requires ({@code 307}), and nested mods left out only
     * because they are for the other side ({@code 108}, {@code 305}: every blocking finding is about the environment)
     * do not count: Fabric Loader and Quilt Loader leave those out on that side too, and the mod works there in full
     * (Quilt full support Q10, ADR-0027).
     */
    private static void noteMissingNestedMods(List<ForeignMod> mods) {
        for (int i = 0; i < mods.size(); i++) {
            ForeignMod container = mods.get(i);
            if (!container.assessment().status().loadable()) {
                continue;
            }
            int total = 0;
            List<String> missing = new ArrayList<>();
            for (ForeignMod child : mods) {
                if (!container.jar().equals(child.containerJar())) {
                    continue;
                }
                total++;
                List<String> codes = child.assessment().blockingCodes();
                if (!child.assessment().status().loadable()
                        && !NOT_MISSING.containsAll(codes)
                        && !otherSideOnly(child)) {
                    missing.add(child.assessment().modId() + " " + codes);
                }
            }
            if (!missing.isEmpty()) {
                String shown = String.join(", ", missing.subList(0, Math.min(5, missing.size())))
                        + (missing.size() > 5 ? ", and " + (missing.size() - 5) + " more" : "");
                mods.set(
                        i,
                        container.withFindings(List.of(new CompatibilityFinding(
                                "POLY-COMPAT-124",
                                false,
                                "nested jars",
                                missing.size() + " of its " + total + " nested mods are not loaded, so it works only"
                                        + " in part: " + shown))));
            }
        }
    }

    /** Whether every blocking finding of {@code mod} is about the environment: it is for the other side only. */
    private static boolean otherSideOnly(ForeignMod mod) {
        List<CompatibilityFinding> blocking = mod.assessment().findings().stream()
                .filter(CompatibilityFinding::blocking)
                .toList();
        return !blocking.isEmpty()
                && blocking.stream().allMatch(finding -> finding.feature().equals("environment"));
    }

    private static void addConflictWarnings(List<ForeignMod> mods, Map<String, String> nativeVersions) {
        Map<String, ForeignMod> providers = new HashMap<>();
        for (ForeignMod mod : mods) {
            if (mod.assessment().status().loadable()) {
                for (String id : mod.assessment().allIds()) {
                    providers.putIfAbsent(id, mod);
                }
            }
        }
        for (int i = 0; i < mods.size(); i++) {
            ForeignMod mod = mods.get(i);
            if (!mod.assessment().status().loadable()) {
                continue;
            }
            List<CompatibilityFinding> warnings = new ArrayList<>();
            for (BridgedDependency dependency : mod.assessment().dependencies()) {
                if (dependency.kind() != BridgedDependency.Kind.CONFLICTS) {
                    continue;
                }
                String version = nativeVersions.get(dependency.modId());
                ForeignMod other = providers.get(dependency.modId());
                if (version == null && other != null && other != mod) {
                    version = other.assessment().versionOf(dependency.modId());
                }
                if (version != null && dependency.accepts().test(version)) {
                    warnings.add(new CompatibilityFinding(
                            "POLY-COMPAT-116",
                            false,
                            "conflicts",
                            "declares a conflict with " + dependency.modId() + " " + dependency.requirement()
                                    + ", and version " + version + " is installed; both are loaded"));
                }
            }
            mods.set(i, mod.withFindings(warnings));
        }
    }

    private record Provider(String version, String unavailableReason) {}

    /** Every ID of an installed foreign mod that does not load, to a reason naming its blocking codes. */
    private static Map<String, String> unavailable(List<ForeignMod> mods) {
        Map<String, String> unavailable = new TreeMap<>();
        for (ForeignMod mod : mods) {
            BridgeAssessment assessment = mod.assessment();
            if (assessment.status().loadable()) {
                continue;
            }
            String reason = assessment.loader() + " mod " + assessment.modId() + " " + assessment.version() + " ("
                    + mod.jar().getFileName() + ") is " + assessment.status() + ": " + assessment.blockingCodes();
            for (String id : assessment.allIds()) {
                unavailable.putIfAbsent(id, reason);
            }
        }
        return unavailable;
    }

    private static String displayName(String id) {
        return switch (id) {
            case "minecraft" -> "Minecraft";
            case "java" -> "Java";
            default -> "'" + id + "'";
        };
    }
}
