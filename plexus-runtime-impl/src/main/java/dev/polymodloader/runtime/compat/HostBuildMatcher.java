package dev.polymodloader.runtime.compat;

import dev.polymodloader.compat.bridge.BridgeLaunchContext;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Finds the host's build of a mod built for another loader too (ADR-0026 37): a loader that runs the game (NeoForge or
 * MinecraftForge through FML) loads its build of a mod, and the Fabric or Quilt build of the same mod must not run
 * beside it. The two builds share their packages, so in one game the other build's classes are the host build's
 * classes, and its entrypoints run the host build's code a second time (Cloth Config registers its example config
 * twice and stops the game).
 *
 * <p>The same mod is recognised by, in this order: the same mod ID; the same ID as the other loader spells it (FML mod
 * IDs cannot have {@code -} or {@code .}, so Fabric's {@code cloth-config} is NeoForge's {@code cloth_config}); one of
 * the mod's provided IDs, either spelling; or at least half of the mod's classes in one host build's jar. In the 26.2
 * corpus every Fabric jar that shares classes with a NeoForge jar is that mod's own build, and the three whose IDs
 * differ (Cloth Config, Reese's Sodium Options, Sodium Extra) share 97 to 100 percent of their classes.
 *
 * <p>Host jars are read once, on the first class comparison. Not thread-safe; the runtime calls it during discovery.
 */
public final class HostBuildMatcher {

    /** Why a mod is taken for the host build's mod, strongest first. */
    public enum Evidence {
        /** The same mod ID. */
        SAME_ID,
        /** The same ID as the other loader spells it: {@code -} and {@code .} become {@code _}. */
        SAME_ID_SPELLING,
        /** One of the mod's provided IDs, either spelling. */
        PROVIDED_ID,
        /** At least half of the mod's classes are in the host build's jar. */
        SAME_CLASSES
    }

    /**
     * The host's build of a mod.
     *
     * @param hostMod the host's mod that is the same mod (for a jar with several mods, its first)
     * @param evidence why it is taken for the same mod
     * @param sharedClasses the mod's classes that the host build's jar has too
     * @param classes the mod's classes
     */
    public record Match(BridgeLaunchContext.LoadedMod hostMod, Evidence evidence, int sharedClasses, int classes) {

        public Match {
            Objects.requireNonNull(hostMod, "hostMod");
            Objects.requireNonNull(evidence, "evidence");
        }

        /** How the match was made, for logs: {@code "cloth_config (the same ID as NeoForge spells it)"}. */
        public String describe() {
            String why = switch (evidence) {
                case SAME_ID -> "the same mod ID";
                case SAME_ID_SPELLING -> "the same ID as " + hostMod.loader() + " spells it";
                case PROVIDED_ID -> "an ID the mod provides";
                case SAME_CLASSES -> sharedClasses + " of its " + classes + " classes are in that jar";
            };
            return hostMod.modId() + " (" + why + ")";
        }

        /**
         * Whether the two builds are mostly the same code: at least half of the mod's classes are in the host build's
         * jar. Only then does the host's build stand in for the left-out one ({@link #standIns}); builds that share
         * the mod ID but little code (Forge Config API Port: 22 of the Fabric build's 96 classes are in the NeoForge
         * build) have each loader's own API, which the left-out build's users call.
         */
        public boolean sameCode() {
            return (classes > 0 && sharedClasses * 2 >= classes) || (classes == 0 && evidence != Evidence.SAME_CLASSES);
        }

        /**
         * The left-out mod's IDs ({@code modId} and {@code providedIds}), each to the host build's version: the
         * dependencies on them that the host's build meets. Meant for {@link #sameCode()} matches.
         */
        public Map<String, String> standIns(String modId, Collection<String> providedIds) {
            Map<String, String> ids = new LinkedHashMap<>();
            ids.put(modId, hostMod.version());
            providedIds.forEach(id -> ids.putIfAbsent(id, hostMod.version()));
            return Map.copyOf(ids);
        }
    }

    private final List<BridgeLaunchContext.LoadedMod> hostMods;
    /** Each host build's classes (internal names with {@code .class}), by {@link #build}; read on first use. */
    private Map<String, Set<String>> hostClasses;

    public HostBuildMatcher(List<BridgeLaunchContext.LoadedMod> hostMods) {
        this.hostMods = List.copyOf(hostMods);
    }

    /** {@code id} as FML spells mod IDs: lower case, with {@code _} for {@code -} and {@code .}. */
    static String spelling(String id) {
        return id.toLowerCase(Locale.ROOT).replace('-', '_').replace('.', '_');
    }

    /**
     * The host's build of the mod {@code modId} (providing {@code providedIds}) whose jar is {@code jar}, or empty
     * when the host runs no build of it.
     *
     * @throws UncheckedIOException when {@code jar} or a host jar cannot be read
     */
    public Optional<Match> find(String modId, Collection<String> providedIds, Path jar) {
        Set<String> classes = classes(jar);
        for (BridgeLaunchContext.LoadedMod mod : hostMods) {
            if (mod.modId().equals(modId)) {
                return Optional.of(match(mod, Evidence.SAME_ID, classes));
            }
        }
        String spelled = spelling(modId);
        for (BridgeLaunchContext.LoadedMod mod : hostMods) {
            if (mod.modId().equals(spelled)) {
                return Optional.of(match(mod, Evidence.SAME_ID_SPELLING, classes));
            }
        }
        for (String provided : providedIds) {
            for (BridgeLaunchContext.LoadedMod mod : hostMods) {
                if (mod.modId().equals(provided) || mod.modId().equals(spelling(provided))) {
                    return Optional.of(match(mod, Evidence.PROVIDED_ID, classes));
                }
            }
        }
        Match best = null;
        for (BridgeLaunchContext.LoadedMod mod : hostMods) {
            Match candidate = match(mod, Evidence.SAME_CLASSES, classes);
            if (candidate.sameCode() && (best == null || candidate.sharedClasses() > best.sharedClasses())) {
                best = candidate;
            }
        }
        return Optional.ofNullable(best);
    }

    private Match match(BridgeLaunchContext.LoadedMod mod, Evidence evidence, Set<String> classes) {
        Set<String> host = hostClasses().getOrDefault(build(mod), Set.of());
        int shared = 0;
        for (String name : classes) {
            if (host.contains(name)) {
                shared++;
            }
        }
        return new Match(mod, evidence, shared, classes.size());
    }

    /** The jar a host mod's classes are in: its own, or the jar nested in it that FML installs with it. */
    private static String build(BridgeLaunchContext.LoadedMod mod) {
        return mod.jar() + "!/" + mod.nestedEntry();
    }

    private Map<String, Set<String>> hostClasses() {
        if (hostClasses == null) {
            hostClasses = new HashMap<>();
            for (BridgeLaunchContext.LoadedMod mod : hostMods) {
                if (mod.jar() != null && !hostClasses.containsKey(build(mod))) {
                    hostClasses.put(
                            build(mod),
                            mod.nestedEntry() == null
                                    ? classes(mod.jar())
                                    : nestedClasses(mod.jar(), mod.nestedEntry()));
                }
            }
        }
        return hostClasses;
    }

    private static Set<String> classes(Path jar) {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            List<String> names = new ArrayList<>();
            zip.stream().map(ZipEntry::getName).forEach(names::add);
            return ownClasses(names);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read the classes of " + jar, e);
        }
    }

    private static Set<String> nestedClasses(Path container, String entry) {
        try (ZipFile zip = new ZipFile(container.toFile())) {
            ZipEntry nested = zip.getEntry(entry);
            if (nested == null) {
                throw new IOException(entry + " is not in it");
            }
            List<String> names = new ArrayList<>();
            try (InputStream in = zip.getInputStream(nested);
                    ZipInputStream inner = new ZipInputStream(in)) {
                for (ZipEntry next = inner.getNextEntry(); next != null; next = inner.getNextEntry()) {
                    names.add(next.getName());
                }
            }
            return ownClasses(names);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read the classes of " + container + "!/" + entry, e);
        }
    }

    /** The class files among {@code names}, without those under {@code META-INF/} and {@code module-info}. */
    private static Set<String> ownClasses(List<String> names) {
        Set<String> classes = new HashSet<>();
        for (String name : names) {
            if (name.endsWith(".class") && !name.startsWith("META-INF/") && !name.equals("module-info.class")) {
                classes.add(name);
            }
        }
        return classes;
    }
}
