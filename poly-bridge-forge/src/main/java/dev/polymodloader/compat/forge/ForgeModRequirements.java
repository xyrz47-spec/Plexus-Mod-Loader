package dev.polymodloader.compat.forge;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.polymodloader.api.runtime.GameSide;
import java.io.IOException;
import java.io.InputStream;
import java.lang.classfile.Annotation;
import java.lang.classfile.AnnotationValue;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
 * The requirements FML checks in each Forge mod's {@code META-INF/mods.toml} before it loads anything (ADR-0021 G6):
 * the language loader's version ({@code loaderVersion}), required dependencies that must be installed, and the version
 * ranges of the dependencies that are installed ({@code minecraft} and {@code forge} among them), each on its side.
 * FML stops the whole launch when one mod fails them; Poly Mod Loader leaves that mod out before FML starts, with the reason
 * ({@code POLY-COMPAT-209}), and then the mods that need it, so the others still run.
 *
 * <p>Version ranges are Maven's, as FML reads them ({@link VersionRanges}). A jar without {@code mods.toml} (a
 * ModLauncher service such as CustomSkinLoader's) is passed on unchecked, and so is a mod of a language loader other
 * than Forge's own ({@code javafml}, {@code lowcodefml}), whose version Poly Mod Loader does not know. The exception is a
 * jar without {@code mods.toml} that another loader's metadata in it declares client-only ({@code fabric.mod.json}
 * {@code "environment": "client"}): FML cannot tell its side, and on a server its service still changes Minecraft's
 * classes for client code (CustomSkinLoader adds a client interface to {@code ResourceManager}, and the server stops
 * when it loads that class), so it is left out on a server.
 */
final class ForgeModRequirements {

    private static final List<String> FORGE_LANGUAGES = List.of("javafml", "lowcodefml");
    private static final String MOD = "net/minecraftforge/fml/common/Mod";
    private static final String ONLY_IN = "net/minecraftforge/api/distmarker/OnlyIn";

    /** Version ranges as FML reads them: Maven's {@code VersionRange} and {@code DefaultArtifactVersion}. */
    interface VersionRanges {
        boolean contains(String range, String version);
    }

    /** A jar left out, with each reason. */
    record Excluded(Path jar, String modId, List<String> reasons) {}

    /** The jars FML gets, and those left out. */
    record Result(List<Path> kept, List<Excluded> excluded) {}

    private ForgeModRequirements() {}

    /** Maven's version ranges from {@code loader} (the {@code maven-artifact} jar of Forge's libraries). */
    static VersionRanges maven(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> rangeClass = Class.forName("org.apache.maven.artifact.versioning.VersionRange", true, loader);
        Class<?> versionClass =
                Class.forName("org.apache.maven.artifact.versioning.DefaultArtifactVersion", true, loader);
        Class<?> artifactVersion = Class.forName("org.apache.maven.artifact.versioning.ArtifactVersion", true, loader);
        Method create = rangeClass.getMethod("createFromVersionSpec", String.class);
        Method contains = rangeClass.getMethod("containsVersion", artifactVersion);
        var newVersion = versionClass.getConstructor(String.class);
        return (range, version) -> {
            // FML takes an empty or "*" range as any version.
            if (range.isBlank() || range.equals("*")) {
                return true;
            }
            try {
                return (boolean) contains.invoke(create.invoke(null, range), newVersion.newInstance(version));
            } catch (ReflectiveOperationException e) {
                throw new IllegalArgumentException("Invalid version range '" + range + "': " + e.getCause(), e);
            }
        };
    }

    /**
     * Checks {@code jars} for {@code side}, running Minecraft {@code minecraftVersion} with Forge {@code forgeVersion}
     * (such as {@code 65.1.5}), and leaves out what FML would refuse, until nothing more fails.
     */
    static Result check(
            List<Path> jars, String minecraftVersion, String forgeVersion, GameSide side, VersionRanges ranges) {
        return check(jars, minecraftVersion, forgeVersion, side, ranges, Set.of());
    }

    /**
     * As {@link #check(List, String, String, GameSide, VersionRanges)}, and a mod whose Mixin configuration needs a
     * compatibility level the running Mixin does not know ({@code mixinLevels}, such as {@code JAVA_21}; empty: not
     * checked) is left out too, as Mixin stops the launch on it.
     */
    static Result check(
            List<Path> jars,
            String minecraftVersion,
            String forgeVersion,
            GameSide side,
            VersionRanges ranges,
            Set<String> mixinLevels) {
        Map<Path, ModsToml> mods = new LinkedHashMap<>();
        Map<Path, List<String>> failures = new LinkedHashMap<>();
        Map<Path, String> otherIds = new HashMap<>();
        for (Path jar : jars) {
            try {
                ModsToml toml = ModsToml.read(jar);
                if (toml != null) {
                    mods.put(jar, toml);
                    List<String> mixinReasons = mixinReasons(mixinConfigs(jar), mixinLevels);
                    if (!mixinReasons.isEmpty()) {
                        failures.put(jar, mixinReasons);
                    }
                } else if (side != GameSide.CLIENT) {
                    Optional<String> clientOnly = clientOnlyFabricId(jar);
                    if (clientOnly.isPresent()) {
                        otherIds.put(jar, clientOnly.get());
                        failures.put(
                                jar,
                                List.of("it is a client-only mod (its fabric.mod.json says \"environment\": \"client\","
                                        + " and it has no mods.toml for FML to check); on a server its ModLauncher"
                                        + " service would still change Minecraft's classes for client code"));
                    }
                }
            } catch (IOException | RuntimeException e) {
                failures.put(jar, List.of("its META-INF/mods.toml cannot be read (" + e.getMessage() + ")"));
            }
        }
        String sideName = side == GameSide.CLIENT ? "CLIENT" : "SERVER";
        boolean changed = true;
        while (changed) {
            changed = false;
            Map<String, String> installed = new HashMap<>();
            installed.put("minecraft", minecraftVersion);
            installed.put("forge", forgeVersion);
            Map<String, Path> excludedIds = new HashMap<>();
            for (Map.Entry<Path, ModsToml> entry : mods.entrySet()) {
                Map<String, String> provided =
                        new LinkedHashMap<>(entry.getValue().nested());
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
                        reasons(entry.getValue(), installed, excludedIds, forgeVersion, sideName, ranges);
                if (!reasons.isEmpty()) {
                    failures.put(entry.getKey(), reasons);
                    changed = true;
                }
            }
        }
        List<Path> kept = new ArrayList<>();
        List<Excluded> excluded = new ArrayList<>();
        for (Path jar : jars) {
            List<String> reasons = failures.get(jar);
            if (reasons == null) {
                kept.add(jar);
            } else {
                ModsToml toml = mods.get(jar);
                String id = toml == null || toml.versions().isEmpty()
                        ? otherIds.getOrDefault(jar, "?")
                        : toml.versions().keySet().iterator().next();
                excluded.add(new Excluded(jar, id, reasons));
            }
        }
        return new Result(List.copyOf(kept), List.copyOf(excluded));
    }

    /**
     * Why Mixin 0.8.7 would stop the launch on the configurations {@code configs} (from {@link #mixinConfigs}), as it
     * does under real Forge (checked with Forge 62.0.9's server, 2026-10-23): a named configuration that cannot be read
     * ({@code MixinConfig.create} throws, {@code Config.create} turns it into a {@code MixinInitialisationError}), or one
     * whose {@code compatibilityLevel}, trimmed and upper-case, is not among {@code levels}
     * ({@code MixinConfig.initCompatibilityLevel}). Both are an {@code Error} that {@code Mixins.createConfiguration}
     * does not catch, so the launch stops before Minecraft starts. Empty {@code levels}: levels are not checked.
     */
    private static List<String> mixinReasons(Map<String, String> configs, Set<String> levels) {
        List<String> missing = new ArrayList<>();
        List<String> reasons = new ArrayList<>();
        for (Map.Entry<String, String> config : configs.entrySet()) {
            if (config.getValue() == null) {
                missing.add(config.getKey());
                continue;
            }
            if (levels.isEmpty()) {
                continue;
            }
            try {
                if (JsonParser.parseString(config.getValue()) instanceof JsonObject root
                        && root.get("compatibilityLevel") instanceof JsonPrimitive level
                        && !levels.contains(level.getAsString().trim().toUpperCase(Locale.ROOT))) {
                    reasons.add("its Mixin configuration " + config.getKey() + " needs compatibility level "
                            + level.getAsString() + ", which this Mixin does not know; Mixin would stop the launch"
                            + " on it");
                }
            } catch (JsonParseException e) {
                // Not JSON: Mixin reports it itself.
            }
        }
        if (!missing.isEmpty()) {
            reasons.addFirst("its manifest names the Mixin configuration(s) " + String.join(", ", missing)
                    + ", which the jar does not have; Mixin would stop the launch on them");
        }
        return reasons;
    }

    /**
     * The Mixin configurations that Mixin registers from the manifest of {@code jar} and of each jar nested in it
     * ({@code META-INF/jarjar/}, which FML adds as a mod of its own), each with its text, or {@code null} when that jar
     * does not have it (kuma-api-forge 26.1.0.2 in Balm names {@code kuma_api.api.mixins.json}).
     */
    static Map<String, String> mixinConfigs(Path jar) throws IOException {
        Map<String, String> configs = new LinkedHashMap<>();
        try (JarFile file = new JarFile(jar.toFile())) {
            for (String name : ForgeMixinConfigs.registered(file.getManifest())) {
                ZipEntry entry = file.getEntry(name);
                String text = null;
                if (entry != null) {
                    try (InputStream input = file.getInputStream(entry)) {
                        text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                    }
                }
                configs.put(name, text);
            }
            java.util.Enumeration<java.util.jar.JarEntry> entries = file.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.getName().startsWith("META-INF/jarjar/")
                        || !entry.getName().endsWith(".jar")) {
                    continue;
                }
                try (JarInputStream nested = new JarInputStream(file.getInputStream(entry))) {
                    Map<String, String> named = new LinkedHashMap<>();
                    for (String name : ForgeMixinConfigs.registered(nested.getManifest())) {
                        named.put(name, null);
                    }
                    for (ZipEntry inner = nested.getNextEntry(); inner != null; inner = nested.getNextEntry()) {
                        if (named.containsKey(inner.getName())) {
                            named.put(inner.getName(), new String(nested.readAllBytes(), StandardCharsets.UTF_8));
                        }
                    }
                    configs.putAll(named);
                }
            }
        }
        return configs;
    }

    /**
     * The compatibility levels a Mixin build knows: the {@code JAVA_*} constants of its {@code
     * MixinEnvironment$CompatibilityLevel} class file.
     */
    static Set<String> mixinLevels(InputStream compatibilityLevelClass) throws IOException {
        Set<String> levels = new java.util.HashSet<>();
        for (var field :
                ClassFile.of().parse(compatibilityLevelClass.readAllBytes()).fields()) {
            if (field.fieldName().stringValue().startsWith("JAVA_")) {
                levels.add(field.fieldName().stringValue());
            }
        }
        return Set.copyOf(levels);
    }

    /** The id in the jar's {@code fabric.mod.json} when it declares {@code "environment": "client"}. */
    private static Optional<String> clientOnlyFabricId(Path jar) throws IOException {
        try (JarFile file = new JarFile(jar.toFile())) {
            ZipEntry entry = file.getEntry("fabric.mod.json");
            if (entry == null) {
                return Optional.empty();
            }
            try (InputStream input = file.getInputStream(entry)) {
                JsonElement json = JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8));
                if (!(json instanceof JsonObject object)
                        || !(object.get("environment") instanceof JsonPrimitive environment)
                        || !environment.getAsString().equals("client")) {
                    return Optional.empty();
                }
                return Optional.of(
                        object.get("id") instanceof JsonPrimitive id
                                ? id.getAsString()
                                : jar.getFileName().toString());
            } catch (JsonParseException e) {
                // Unreadable: the side is unknown, so the jar is passed on unchecked like any other without mods.toml.
                return Optional.empty();
            }
        }
    }

    private static List<String> reasons(
            ModsToml toml,
            Map<String, String> installed,
            Map<String, Path> excludedIds,
            String forgeVersion,
            String side,
            VersionRanges ranges) {
        List<String> reasons = new ArrayList<>();
        if (side.equals("SERVER") && declaresMinecraftForClientOnly(toml)) {
            reasons.add("it is a client-only mod (it needs minecraft on the CLIENT side only); on a server Forge would"
                    + " construct it anyway, and it would reach client classes");
        } else if (side.equals("SERVER") && !toml.clientOnlyModClasses().isEmpty()) {
            reasons.add("it is a client-only mod (its @Mod class "
                    + toml.clientOnlyModClasses().getFirst()
                    + " is @OnlyIn(Dist.CLIENT)); on a server Forge would stop the launch on it");
        }
        if (FORGE_LANGUAGES.contains(toml.modLoader()) && !ranges.contains(toml.loaderVersion(), forgeVersion)) {
            reasons.add("its language loader " + toml.modLoader() + " must be " + toml.loaderVersion() + ", Forge has "
                    + forgeVersion + " (the mod was built for another Forge version)");
        }
        for (Dependency dependency : toml.dependencies()) {
            if (!dependency.side().equals("BOTH") && !dependency.side().equals(side)) {
                continue;
            }
            String version = installed.get(dependency.modId());
            if (dependency.incompatible()) {
                if (version != null && ranges.contains(dependency.versionRange(), version)) {
                    reasons.add(
                            "it is incompatible with " + dependency.modId() + " " + version + ", which is installed");
                }
                continue;
            }
            if (version == null) {
                if (dependency.required()) {
                    Path excluded = excludedIds.get(dependency.modId());
                    reasons.add("it requires " + dependency.modId() + " " + dependency.versionRange()
                            + (excluded != null
                                    ? ", which is installed but NOT loaded (" + excluded.getFileName() + ")"
                                    : ", which is not installed"));
                }
            } else if (!ranges.contains(dependency.versionRange(), version)) {
                String what = dependency.modId().equals("minecraft")
                        ? "Minecraft " + version + " is running"
                        : version + " is installed";
                reasons.add("it requires " + dependency.modId() + " " + dependency.versionRange() + ", but " + what);
            }
        }
        return reasons;
    }

    /** Whether every dependency on {@code minecraft} is for the client side only, as BHMenu declares. */
    private static boolean declaresMinecraftForClientOnly(ModsToml toml) {
        List<Dependency> minecraft = toml.dependencies().stream()
                .filter(dependency -> dependency.modId().equals("minecraft"))
                .toList();
        return !minecraft.isEmpty()
                && minecraft.stream().allMatch(dependency -> dependency.side().equals("CLIENT"));
    }

    /** One dependency entry of a mod. */
    record Dependency(String modId, boolean required, boolean incompatible, String versionRange, String side) {}

    /**
     * What FML reads from a jar's {@code mods.toml}: its language loader, mods and dependencies; and the mods of the
     * jars nested in it ({@code META-INF/jarjar/}), which FML installs with it.
     */
    record ModsToml(
            String modLoader,
            String loaderVersion,
            Map<String, String> versions,
            List<Dependency> dependencies,
            Map<String, String> nested,
            List<String> clientOnlyModClasses,
            Map<String, String> displayNames,
            List<NestedMod> nestedMods) {

        /** A mod of a jar nested in this one ({@code META-INF/jarjar/<entry>}), which FML installs with it. */
        record NestedMod(String modId, String version, String displayName, String entry) {}

        /** The jar's {@code mods.toml}, or {@code null} when it has none. */
        static ModsToml read(Path jar) throws IOException {
            try (JarFile file = new JarFile(jar.toFile())) {
                ZipEntry entry = file.getEntry("META-INF/mods.toml");
                if (entry == null) {
                    return null;
                }
                String text;
                try (InputStream input = file.getInputStream(entry)) {
                    text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                }
                Manifest manifest = file.getManifest();
                String jarVersion =
                        manifest == null ? null : manifest.getMainAttributes().getValue("Implementation-Version");
                ModsToml toml = parse(text, jarVersion);
                Map<String, String> nested = new LinkedHashMap<>();
                List<NestedMod> nestedMods = new ArrayList<>();
                for (var entries = file.entries(); entries.hasMoreElements(); ) {
                    ZipEntry inner = entries.nextElement();
                    if (inner.getName().startsWith("META-INF/jarjar/")
                            && inner.getName().endsWith(".jar")) {
                        try (JarInputStream nestedJar = new JarInputStream(file.getInputStream(inner))) {
                            ModsToml nestedToml = nestedMods(nestedJar);
                            nested.putAll(nestedToml.versions());
                            nestedToml
                                    .versions()
                                    .forEach((id, version) -> nestedMods.add(new NestedMod(
                                            id,
                                            version,
                                            nestedToml.displayNames().getOrDefault(id, id),
                                            inner.getName())));
                        }
                    }
                }
                List<String> clientOnly = new ArrayList<>();
                for (var entries = file.entries(); entries.hasMoreElements(); ) {
                    ZipEntry inner = entries.nextElement();
                    if (inner.getName().endsWith(".class") && !inner.getName().startsWith("META-INF/")) {
                        try (InputStream input = file.getInputStream(inner)) {
                            clientOnlyModClass(input.readAllBytes()).ifPresent(clientOnly::add);
                        }
                    }
                }
                return new ModsToml(
                        toml.modLoader(),
                        toml.loaderVersion(),
                        toml.versions(),
                        toml.dependencies(),
                        nested,
                        List.copyOf(clientOnly),
                        toml.displayNames(),
                        List.copyOf(nestedMods));
            }
        }

        /**
         * The class's name when it is a {@code @Mod} class that is also {@code @OnlyIn(Dist.CLIENT)}: Forge's
         * {@code RuntimeDistCleaner} refuses it on a server, and FML stops the launch.
         */
        private static Optional<String> clientOnlyModClass(byte[] bytes) {
            String text = new String(bytes, StandardCharsets.ISO_8859_1);
            if (!text.contains(ONLY_IN) || !text.contains(MOD)) {
                return Optional.empty();
            }
            ClassModel model = ClassFile.of().parse(bytes);
            boolean mod = false;
            boolean clientOnly = false;
            for (var attribute : model.findAttributes(Attributes.runtimeVisibleAnnotations())) {
                for (Annotation annotation : attribute.annotations()) {
                    String type = annotation.className().stringValue();
                    if (type.equals("L" + MOD + ";")) {
                        mod = true;
                    } else if (type.equals("L" + ONLY_IN + ";")) {
                        clientOnly = annotation.elements().stream()
                                .anyMatch(element -> element.value() instanceof AnnotationValue.OfEnum value
                                        && value.constantName().stringValue().equals("CLIENT"));
                    }
                }
            }
            return mod && clientOnly
                    ? Optional.of(model.thisClass().asInternalName().replace('/', '.'))
                    : Optional.empty();
        }

        /** The mods a nested jar declares, or none when it has no {@code mods.toml} (a plain library). */
        private static ModsToml nestedMods(JarInputStream jar) throws IOException {
            Manifest manifest = jar.getManifest();
            String jarVersion =
                    manifest == null ? null : manifest.getMainAttributes().getValue("Implementation-Version");
            for (ZipEntry entry = jar.getNextEntry(); entry != null; entry = jar.getNextEntry()) {
                if (entry.getName().equals("META-INF/mods.toml")) {
                    return parse(new String(jar.readAllBytes(), StandardCharsets.UTF_8), jarVersion);
                }
            }
            return new ModsToml("", "", Map.of(), List.of(), Map.of(), List.of(), Map.of(), List.of());
        }

        /** Parses {@code text}; {@code ${file.jarVersion}} becomes {@code jarVersion}, as FML does. */
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
                        String version = mod.get("version") instanceof String value ? value : "1";
                        if (version.equals("${file.jarVersion}")) {
                            version = jarVersion == null ? "0.0NONE" : jarVersion;
                        }
                        versions.put(id, version);
                        // FML's ModInfo: displayName, defaulting to the mod ID.
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
                            String type = dependency.get("type") instanceof String value ? value : "";
                            boolean required = Boolean.TRUE.equals(dependency.get("mandatory"))
                                    || type.equalsIgnoreCase("required");
                            String range = dependency.get("versionRange") instanceof String value ? value : "";
                            String side = dependency.get("side") instanceof String value ? value : "BOTH";
                            dependencies.add(new Dependency(
                                    modId, required, type.equalsIgnoreCase("incompatible"), range, side));
                        }
                    }
                }
            }
            return new ModsToml(
                    toml.get("modLoader") instanceof String loader ? loader : "",
                    toml.get("loaderVersion") instanceof String range ? range : "",
                    versions,
                    dependencies,
                    Map.of(),
                    List.of(),
                    displayNames,
                    List.of());
        }
    }
}
