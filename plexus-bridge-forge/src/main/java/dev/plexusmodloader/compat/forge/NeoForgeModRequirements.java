package dev.plexusmodloader.compat.forge;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.plexusmodloader.api.runtime.GameSide;
import dev.plexusmodloader.compat.bridge.BridgeLaunchContext;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/**
 * The requirements FML 11 checks in each NeoForge mod's {@code META-INF/neoforge.mods.toml} before it loads anything
 * (checked against FancyModLoader {@code loader} 11.0.16's sources, 2026-10-24): the version of the Java language
 * loader {@code javafml} ({@code LanguageProviderLoader.findLanguage}), and each dependency on the running side
 * ({@code ModSorter.verifyDependencyVersions}): a {@code required} one must be installed in its version range, an
 * {@code optional} one that is installed must be in its range, an {@code incompatible} one that is installed must not
 * be. FML then loads only NeoForge and Minecraft and stops the launch with the errors; Poly Mod Loader leaves that mod
 * out before FML starts, with the reason ({@code PLEXUS-COMPAT-217}), and then the mods that need it, so the others still
 * run.
 *
 * <p>As FML 11 reads it: a dependency without {@code type} is {@code required} (Forge's {@code mandatory} is not read),
 * {@code discouraged} only warns, a dependency without {@code side} is for both sides. A mod's {@code
 * ${file.jarVersion}} is its jar's {@code Implementation-Version}, or {@code 0.0NONE}, which satisfies every range. A
 * mod of a jar nested in it (named by JarJar's {@code META-INF/jarjar/metadata.json}) is installed with its outer jar. Version ranges are Maven's, as FML
 * reads them ({@link ForgeModRequirements.VersionRanges}). A jar without {@code neoforge.mods.toml} (an FML library) is
 * passed on unchecked, and so is a mod of another language loader than {@code javafml} (whose version Poly Mod Loader
 * does not know) or of the deprecated {@code lowcodefml}, which FML 11 no longer checks.
 *
 * <p>Not checked: a client-only mod on a server. NeoForge does not construct {@code @Mod(dist = Dist.CLIENT)} classes
 * there itself, so leaving such mods out would change what NeoForge does.
 */
final class NeoForgeModRequirements {

    static final String METADATA = "META-INF/neoforge.mods.toml";

    /** NeoForge JarJar's list of the jars nested in a jar. */
    static final String JAR_JAR_METADATA = "META-INF/jarjar/metadata.json";

    static final String LOADER = "NeoForge";
    private static final String JAVA_LANGUAGE = "javafml";
    private static final String NO_JAR_VERSION = "0.0NONE";
    private static final Set<String> TYPES = Set.of("REQUIRED", "OPTIONAL", "INCOMPATIBLE", "DISCOURAGED");
    private static final Set<String> SIDES = Set.of("CLIENT", "SERVER", "BOTH");

    private NeoForgeModRequirements() {}

    /**
     * Checks {@code jars} for {@code side}, running Minecraft {@code minecraftVersion} with NeoForge {@code
     * neoForgeVersion} (such as {@code 26.2.0.93}) and FML's Java language loader {@code javaLanguageVersion} (the
     * {@code Implementation-Version} of FML's {@code loader} jar, such as {@code 11.0}), and leaves out what FML would
     * refuse, until nothing more fails.
     */
    static ForgeModRequirements.Result check(
            List<Path> jars,
            String minecraftVersion,
            String neoForgeVersion,
            String javaLanguageVersion,
            GameSide side,
            ForgeModRequirements.VersionRanges ranges) {
        Map<Path, ModsToml> mods = new LinkedHashMap<>();
        Map<Path, List<String>> failures = new LinkedHashMap<>();
        for (Path jar : jars) {
            try {
                ModsToml toml = ModsToml.read(jar);
                if (toml != null) {
                    mods.put(jar, toml);
                }
            } catch (IOException | RuntimeException e) {
                failures.put(jar, List.of("its " + METADATA + " cannot be read (" + e.getMessage() + ")"));
            }
        }
        String sideName = side == GameSide.CLIENT ? "CLIENT" : "SERVER";
        boolean changed = true;
        while (changed) {
            changed = false;
            Map<String, String> installed = new HashMap<>();
            installed.put("minecraft", minecraftVersion);
            installed.put("neoforge", neoForgeVersion);
            Map<String, Path> excludedIds = new HashMap<>();
            for (Map.Entry<Path, ModsToml> entry : mods.entrySet()) {
                Map<String, String> provided = new LinkedHashMap<>();
                entry.getValue().nestedMods().forEach(mod -> provided.put(mod.modId(), mod.version()));
                provided.putAll(entry.getValue().versions());
                for (Map.Entry<String, String> mod : provided.entrySet()) {
                    if (failures.containsKey(entry.getKey())) {
                        excludedIds.putIfAbsent(mod.getKey(), entry.getKey());
                    } else {
                        installed.put(mod.getKey(), mod.getValue());
                    }
                }
            }
            for (Map.Entry<Path, ModsToml> entry : mods.entrySet()) {
                if (failures.containsKey(entry.getKey())) {
                    continue;
                }
                List<String> reasons =
                        reasons(entry.getValue(), installed, excludedIds, javaLanguageVersion, sideName, ranges);
                if (!reasons.isEmpty()) {
                    failures.put(entry.getKey(), reasons);
                    changed = true;
                }
            }
        }
        List<Path> kept = new ArrayList<>();
        List<ForgeModRequirements.Excluded> excluded = new ArrayList<>();
        for (Path jar : jars) {
            List<String> reasons = failures.get(jar);
            if (reasons == null) {
                kept.add(jar);
            } else {
                ModsToml toml = mods.get(jar);
                String id = toml == null || toml.versions().isEmpty()
                        ? "?"
                        : toml.versions().keySet().iterator().next();
                excluded.add(new ForgeModRequirements.Excluded(jar, id, reasons));
            }
        }
        return new ForgeModRequirements.Result(List.copyOf(kept), List.copyOf(excluded));
    }

    private static List<String> reasons(
            ModsToml toml,
            Map<String, String> installed,
            Map<String, Path> excludedIds,
            String javaLanguageVersion,
            String side,
            ForgeModRequirements.VersionRanges ranges) {
        List<String> reasons = new ArrayList<>();
        if (JAVA_LANGUAGE.equals(toml.modLoader())
                && !toml.loaderVersion().isEmpty()
                && !ranges.contains(toml.loaderVersion(), javaLanguageVersion)) {
            reasons.add("its language loader " + JAVA_LANGUAGE + " must be " + toml.loaderVersion() + ", FML has "
                    + javaLanguageVersion + " (the mod was built for another NeoForge version)");
        }
        for (Dependency dependency : toml.dependencies()) {
            if (!dependency.side().equals("BOTH") && !dependency.side().equals(side)) {
                continue;
            }
            String version = installed.get(dependency.modId());
            boolean inRange = version != null
                    && (version.equals(NO_JAR_VERSION) || ranges.contains(dependency.versionRange(), version));
            switch (dependency.type()) {
                case "INCOMPATIBLE" -> {
                    if (inRange) {
                        reasons.add("it is incompatible with " + dependency.modId() + " " + version
                                + ", which is installed");
                    }
                }
                case "REQUIRED" -> {
                    if (version == null) {
                        Path excluded = excludedIds.get(dependency.modId());
                        reasons.add("it requires " + dependency.modId() + " " + range(dependency)
                                + (excluded != null
                                        ? ", which is installed but NOT loaded (" + excluded.getFileName() + ")"
                                        : ", which is not installed"));
                    } else if (!inRange) {
                        reasons.add(versionReason(dependency, version));
                    }
                }
                case "OPTIONAL" -> {
                    if (version != null && !inRange) {
                        reasons.add(versionReason(dependency, version));
                    }
                }
                default -> {
                    // DISCOURAGED: FML only warns.
                }
            }
        }
        return reasons;
    }

    private static String versionReason(Dependency dependency, String version) {
        String what = dependency.modId().equals("minecraft")
                ? "Minecraft " + version + " is running"
                : version + " is installed";
        return "it requires " + dependency.modId() + " " + range(dependency) + ", but " + what;
    }

    private static String range(Dependency dependency) {
        return dependency.versionRange().isEmpty() ? "(any version)" : dependency.versionRange();
    }

    /**
     * The mods FML runs from {@code modJars}, as other loaders and Poly Mods see them: each jar's {@code
     * neoforge.mods.toml} entries, then the mods of the jars nested in it (JarJar's {@code metadata.json}), which FML installs
     * with it. A nested mod's jar is the outer one, where FML reads it from. Jars without readable metadata add nothing.
     */
    static List<BridgeLaunchContext.LoadedMod> hostMods(List<Path> modJars) {
        List<BridgeLaunchContext.LoadedMod> own = new ArrayList<>();
        List<BridgeLaunchContext.LoadedMod> nested = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Path jar : modJars) {
            ModsToml toml;
            try {
                toml = ModsToml.read(jar);
            } catch (IOException | RuntimeException e) {
                continue; // the requirements check left such a jar out
            }
            if (toml == null) {
                continue; // an FML library without mods, own or nested
            }
            toml.versions().forEach((id, version) -> {
                if (seen.add(id)) {
                    own.add(new BridgeLaunchContext.LoadedMod(
                            LOADER,
                            id,
                            version,
                            toml.displayNames().getOrDefault(id, id),
                            toml.providedIds().getOrDefault(id, List.of()),
                            jar,
                            null,
                            null,
                            true));
                }
            });
            for (NestedMod mod : toml.nestedMods()) {
                if (seen.add(mod.modId())) {
                    nested.add(new BridgeLaunchContext.LoadedMod(
                            LOADER,
                            mod.modId(),
                            mod.version(),
                            mod.displayName(),
                            mod.providedIds(),
                            jar,
                            jar,
                            mod.entry(),
                            true));
                }
            }
        }
        own.addAll(nested);
        return List.copyOf(own);
    }

    /**
     * One dependency entry of a mod, as FML 11 reads it.
     *
     * @param type {@code REQUIRED}, {@code OPTIONAL}, {@code INCOMPATIBLE} or {@code DISCOURAGED}
     * @param versionRange Maven range; empty for any version
     * @param side {@code CLIENT}, {@code SERVER} or {@code BOTH}
     */
    record Dependency(String modId, String type, String versionRange, String side) {}

    /** A mod of a jar nested in another (the entry JarJar's {@code metadata.json} names), which FML installs with it. */
    record NestedMod(String modId, String version, String displayName, List<String> providedIds, String entry) {}

    /** What FML reads from a jar's {@code neoforge.mods.toml}, and the mods of the jars nested in it. */
    record ModsToml(
            String modLoader,
            String loaderVersion,
            Map<String, String> versions,
            Map<String, String> displayNames,
            Map<String, List<String>> providedIds,
            List<Dependency> dependencies,
            List<NestedMod> nestedMods) {

        /**
         * The jar's {@code neoforge.mods.toml} and its JarJar mods, or {@code null} when it has neither. A game library
         * without {@code neoforge.mods.toml} whose JarJar holds mods (MezzConfig's {@code -standalone.jar}) gives a
         * {@code ModsToml} with only {@link #nestedMods()}: FML installs those mods too.
         *
         * @throws IOException when the jar cannot be read
         * @throws IllegalArgumentException when the file is not valid TOML or names an unknown dependency type or side
         */
        static ModsToml read(Path jar) throws IOException {
            try (JarFile file = new JarFile(jar.toFile())) {
                ZipEntry entry = file.getEntry(METADATA);
                if (entry == null) {
                    List<NestedMod> nestedMods = nestedMods(file);
                    return nestedMods.isEmpty()
                            ? null
                            : new ModsToml("", "", Map.of(), Map.of(), Map.of(), List.of(), nestedMods);
                }
                String text;
                try (InputStream input = file.getInputStream(entry)) {
                    text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                }
                ModsToml toml = parse(text, jarVersion(file.getManifest()));
                return new ModsToml(
                        toml.modLoader(),
                        toml.loaderVersion(),
                        toml.versions(),
                        toml.displayNames(),
                        toml.providedIds(),
                        toml.dependencies(),
                        nestedMods(file));
            }
        }

        /** The mods of the jars JarJar installs with {@code file} ({@link #jarJarPaths}). */
        private static List<NestedMod> nestedMods(JarFile file) throws IOException {
            List<NestedMod> nestedMods = new ArrayList<>();
            for (String path : jarJarPaths(file)) {
                ZipEntry inner = file.getEntry(path);
                if (inner == null) {
                    continue; // FML's JarJar reports a missing nested jar itself
                }
                try (JarInputStream nestedJar = new JarInputStream(file.getInputStream(inner))) {
                    ModsToml nestedToml = nested(nestedJar);
                    nestedToml
                            .versions()
                            .forEach((id, version) -> nestedMods.add(new NestedMod(
                                    id,
                                    version,
                                    nestedToml.displayNames().getOrDefault(id, id),
                                    nestedToml.providedIds().getOrDefault(id, List.of()),
                                    inner.getName())));
                }
            }
            return List.copyOf(nestedMods);
        }

        /**
         * The jars NeoForge's JarJar installs with {@code file}: the {@code path} of each entry of {@code
         * META-INF/jarjar/metadata.json}, wherever in the jar it is (Lambda Dynamic Lights names {@code META-INF/jars/},
         * where its Fabric build has them too). None without that file, as JarJar finds none then.
         */
        private static List<String> jarJarPaths(JarFile file) throws IOException {
            ZipEntry metadata = file.getEntry(JAR_JAR_METADATA);
            if (metadata == null) {
                return List.of();
            }
            List<String> paths = new ArrayList<>();
            try (InputStream input = file.getInputStream(metadata)) {
                JsonElement json = JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8));
                if (json.isJsonObject() && json.getAsJsonObject().get("jars") instanceof JsonArray jars) {
                    for (JsonElement jar : jars) {
                        if (jar.isJsonObject()
                                && jar.getAsJsonObject().get("path") instanceof JsonPrimitive path
                                && path.isString()) {
                            paths.add(path.getAsString());
                        }
                    }
                }
            } catch (JsonParseException e) {
                throw new IllegalArgumentException(JAR_JAR_METADATA + " is not valid JSON: " + e.getMessage(), e);
            }
            return List.copyOf(paths);
        }

        /** The mods a nested jar declares, or none when it has no {@code neoforge.mods.toml} (a plain library). */
        private static ModsToml nested(JarInputStream jar) throws IOException {
            String jarVersion = jarVersion(jar.getManifest());
            for (ZipEntry entry = jar.getNextEntry(); entry != null; entry = jar.getNextEntry()) {
                if (entry.getName().equals(METADATA)) {
                    return parse(new String(jar.readAllBytes(), StandardCharsets.UTF_8), jarVersion);
                }
            }
            return new ModsToml("", "", Map.of(), Map.of(), Map.of(), List.of(), List.of());
        }

        private static String jarVersion(Manifest manifest) {
            return manifest == null ? null : manifest.getMainAttributes().getValue("Implementation-Version");
        }

        /** Parses {@code text}; {@code ${file.jarVersion}} becomes {@code jarVersion} or {@code 0.0NONE}. */
        static ModsToml parse(String text, String jarVersion) {
            TomlParseResult toml = Toml.parse(text);
            if (toml.hasErrors()) {
                throw new IllegalArgumentException(toml.errors().getFirst().toString());
            }
            Map<String, String> versions = new LinkedHashMap<>();
            Map<String, String> displayNames = new LinkedHashMap<>();
            if (toml.get("mods") instanceof TomlArray mods) {
                for (int i = 0; i < mods.size(); i++) {
                    if (mods.get(i) instanceof TomlTable mod && mod.get("modId") instanceof String id) {
                        // FML's ModInfo: version "1" when absent, displayName defaulting to the mod ID.
                        String version = mod.get("version") instanceof String value ? value : "1";
                        version =
                                version.replace("${file.jarVersion}", jarVersion == null ? NO_JAR_VERSION : jarVersion);
                        versions.put(id, version);
                        displayNames.put(id, mod.get("displayName") instanceof String name ? name : id);
                    }
                }
            }
            List<Dependency> dependencies = new ArrayList<>();
            if (toml.get("dependencies") instanceof TomlTable table) {
                for (String owner : versions.keySet()) {
                    if (!(table.get(List.of(owner)) instanceof TomlArray entries)) {
                        continue;
                    }
                    for (int i = 0; i < entries.size(); i++) {
                        if (entries.get(i) instanceof TomlTable dependency
                                && dependency.get("modId") instanceof String modId) {
                            String type = dependency.get("type") instanceof String value
                                    ? value.toUpperCase(Locale.ROOT)
                                    : "REQUIRED";
                            if (!TYPES.contains(type)) {
                                throw new IllegalArgumentException("dependency " + modId + " of " + owner
                                        + " has type \"" + dependency.get("type") + "\", which FML does not know");
                            }
                            String side = dependency.get("side") instanceof String value ? value : "BOTH";
                            if (!SIDES.contains(side)) {
                                throw new IllegalArgumentException("dependency " + modId + " of " + owner
                                        + " has side \"" + side + "\", which FML does not know");
                            }
                            String range = dependency.get("versionRange") instanceof String value ? value : "";
                            dependencies.add(new Dependency(modId, type, range, side));
                        }
                    }
                }
            }
            Map<String, List<String>> providedIds = new LinkedHashMap<>();
            if (toml.get("modproperties") instanceof TomlTable properties) {
                for (String owner : properties.keySet()) {
                    if (properties.get(List.of(owner)) instanceof TomlTable ownerTable) {
                        // One key, not a dotted path: get(String) parses its argument as TOML and fails on ':'.
                        Object provides = ownerTable.get(List.of("fabric:provides"));
                        List<String> ids = new ArrayList<>();
                        if (provides instanceof String single) {
                            ids.add(single);
                        } else if (provides instanceof TomlArray array) {
                            for (int i = 0; i < array.size(); i++) {
                                if (array.get(i) instanceof String str) {
                                    ids.add(str);
                                }
                            }
                        }
                        if (!ids.isEmpty()) {
                            providedIds.put(owner, new ArrayList<>(ids));
                        }
                    }
                }
            }
            for (String id : versions.keySet()) {
                if (id.contains("_")) {
                    String hyphenated = id.replace('_', '-');
                    List<String> current = providedIds.computeIfAbsent(id, k -> new ArrayList<>());
                    if (!current.contains(hyphenated)) {
                        current.add(hyphenated);
                    }
                }
            }
            Map<String, List<String>> immutableProvided = new LinkedHashMap<>();
            providedIds.forEach((k, v) -> immutableProvided.put(k, List.copyOf(v)));
            return new ModsToml(
                    toml.get("modLoader") instanceof String loader ? loader : JAVA_LANGUAGE,
                    toml.get("loaderVersion") instanceof String range ? range : "",
                    versions,
                    displayNames,
                    immutableProvided,
                    dependencies,
                    List.of());
        }
    }
}
