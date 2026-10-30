package dev.plexusmodloader.compat.fabric;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.plexusmodloader.api.runtime.GameSide;
import dev.plexusmodloader.compat.bridge.BridgeAssessment;
import dev.plexusmodloader.compat.bridge.BridgeClassTransformer;
import dev.plexusmodloader.compat.bridge.BridgeLaunchContext;
import dev.plexusmodloader.compat.bridge.BridgedDependency;
import dev.plexusmodloader.compat.bridge.BridgedEntrypoint;
import dev.plexusmodloader.compat.bridge.BridgedInterfaceInjection;
import dev.plexusmodloader.compat.bridge.CompatibilityFinding;
import dev.plexusmodloader.compat.bridge.CompatibilityStatus;
import dev.plexusmodloader.compat.bridge.GameClassBytes;
import dev.plexusmodloader.compat.bridge.HostedLaunch;
import dev.plexusmodloader.compat.bridge.ModJarContents;
import dev.plexusmodloader.compat.bridge.ModLoaderBridge;
import dev.plexusmodloader.compat.fabric.loader.FabricFamilyMod;
import dev.plexusmodloader.compat.fabric.loader.FabricLoaderRuntime;
import dev.plexusmodloader.compat.fabric.loader.PlexusFabricLauncher;
import dev.plexusmodloader.compat.fabric.mixin.FabricMixinBootstrap;
import dev.plexusmodloader.compat.fabric.mixin.HostInterfaceDefaults;
import dev.plexusmodloader.compat.fabric.mixin.MixinConfigEntry;
import dev.plexusmodloader.compat.quilt.QuiltLoaderRuntime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import org.quiltmc.loader.impl.QuiltLoaderImpl;

/**
 * Fabric bridge: runs Fabric mods' {@code main}, {@code client}, and {@code server} entrypoints and gives them
 * Fabric Loader's runtime API ({@code FabricLoader.getInstance()}, see {@link FabricLoaderRuntime}). Language
 * adapters other than the default come from the loaded mod that declares them; a mod that uses one requires
 * {@code fabric-language-adapter:<name>}. A class tweaker ({@code accessWidener}) is read with Fabric Loader's
 * grammar and its access lines become access widenings the runtime checks and applies (ADR-0018). Mixin configurations
 * are applied by Mixin itself, at the versions Fabric Loader 0.19.5 ships, through Poly Mod Loader's Mixin service (ADR-0020).
 * What Poly Mod Loader cannot provide (enum extension in class tweakers) is
 * reported per feature and blocks loading. Nested jars ({@code jars}) are reported for the runtime, which extracts them
 * and assesses each one as a mod of its own, as Fabric Loader does.
 *
 * <p>This bridge owns the launch machinery the Fabric family shares (ADR-0027 decision 1): its {@link #prepareLaunch},
 * {@link #classTransformer} and {@link #beforeGame} also serve the Quilt mods {@code QuiltBridge} assessed, and attach
 * Quilt Loader's API ({@code QuiltLoader}) beside Fabric Loader's.
 *
 * <p>Relationships with other mods ({@code depends}, {@code recommends}, {@code suggests}, {@code breaks},
 * {@code conflicts}) are reported as {@link BridgedDependency} values; the runtime checks them against every
 * installed mod of every loader. Version requirements use Fabric Loader's own parser ({@link
 * VersionPredicate}, {@link Version}), so they mean exactly what they mean under Fabric.
 */
public final class FabricBridge implements ModLoaderBridge {

    private static final Map<String, String> PHASES =
            Map.of("main", "common", "client", "client", "server", "server", "preLaunch", "preLaunch");

    /** Prefix of the ID a mod provides for each language adapter it declares ({@code languageAdapters}). */
    static final String LANGUAGE_ADAPTER_PREFIX = "fabric-language-adapter:";

    /** Version of the Fabric Loader API jar on Poly Mod Loader's class path, from {@code fabric-bridge.properties}. */
    static final String FABRIC_LOADER_VERSION = BundledVersions.FABRIC_LOADER;

    /** Version of MixinExtras on Poly Mod Loader's class path, reported as the built-in mod {@code mixinextras}. */
    static final String MIXIN_EXTRAS_VERSION = BundledVersions.MIXIN_EXTRAS;

    @Override
    public String loaderName() {
        return "Fabric";
    }

    @Override
    public String metadataPath() {
        return "fabric.mod.json";
    }

    /**
     * {@code fabricloader} and MixinExtras, as Fabric Loader has them, and {@code quilt_loader}: Poly Mod Loader
     * provides Quilt Loader's API to every mod (ADR-0027), and Quilt builds that ship only {@code fabric.mod.json}
     * require it, which Quilt Loader meets. {@code FabricLoader.isModLoaded("quilt_loader")} stays {@code false}, as
     * under Fabric Loader, so Fabric mods do not take Quilt-only code paths (Quilt full support Q10).
     */
    @Override
    public Map<String, String> builtInMods() {
        return Map.of(
                "fabricloader",
                FABRIC_LOADER_VERSION,
                "mixinextras",
                MIXIN_EXTRAS_VERSION,
                "quilt_loader",
                BundledVersions.QUILT_LOADER);
    }

    /** Mixin and MixinExtras keep global state; a mod's own copy must never be defined beside Poly Mod Loader's. */
    @Override
    public List<String> sharedLibraryPackages() {
        return List.of("org.spongepowered.", "com.llamalad7.mixinextras.");
    }

    @Override
    public List<String> runtimePackages() {
        return List.of("net/fabricmc/");
    }

    @Override
    public List<String> providedPackages() {
        // Stand-ins of Fabric Loader's internal classes that mods use: the launcher (adding jars), FabricLoaderImpl and
        // its GameProvider (the game version). The link check also checks their members, so a mod that uses a member
        // a stand-in lacks is blocked.
        return List.of(
                "net/fabricmc/api/",
                "net/fabricmc/loader/api/",
                "net/fabricmc/loader/language/LanguageAdapter",
                "net/fabricmc/loader/impl/launch/FabricLauncher",
                "net/fabricmc/loader/impl/FabricLoaderImpl",
                "net/fabricmc/loader/impl/game/GameProvider");
    }

    /**
     * Builds Fabric Loader's runtime API from the loaded mods and makes {@code FabricLoader.getInstance()} return it,
     * then Quilt Loader's on top of it ({@code QuiltLoader}, ADR-0027), which is attached even without Quilt mods so
     * that the Quilt API always answers.
     */
    @Override
    public void prepareLaunch(BridgeLaunchContext context) {
        FabricLoaderRuntime fabric =
                FabricLoaderRuntime.create(context, FABRIC_LOADER_VERSION, Map.of("mixinextras", MIXIN_EXTRAS_VERSION));
        FabricLoaderImpl.INSTANCE.attachPolyModLoaderRuntime(fabric);
        QuiltLoaderImpl.INSTANCE.attachPolyModLoaderRuntime(
                QuiltLoaderRuntime.create(context, fabric, BundledVersions.QUILT_LOADER));
        PlexusFabricLauncher.install(context.gameClassLoader(), context.side());
    }

    /**
     * The Fabric and Quilt mods whose mixins cannot work as written on the host's patched game (ADR-0030): Quilt mods
     * share Fabric's Mixin (ADR-0027), so this bridge checks both.
     */
    @Override
    public Map<java.nio.file.Path, List<String>> hostIncompatibilities(
            GameSide side, HostedLaunch host, List<BridgeLaunchContext.LoadedMod> mods, GameClassBytes patched) {
        return FabricMixinBootstrap.incompatibleOnHost(host, side, mods, patched);
    }

    /**
     * The Fabric class stage (ADR-0020), installed when a Fabric or Quilt mod loads (ADR-0027): environment stripping
     * and the loaded mods' interface injections, as Fabric Loader's own transformer does, then Mixin when a loaded mod
     * has a Mixin configuration for this side.
     */
    @Override
    public Optional<BridgeClassTransformer> classTransformer(BridgeLaunchContext context, GameClassBytes classes) {
        if (context.mods().stream().noneMatch(mod -> FabricFamilyMod.isFamily(mod.loader()))
                && context.sharedMixinConfigs().isEmpty()) {
            return Optional.empty();
        }
        EnvironmentStripper stripper = new EnvironmentStripper(
                context.side(),
                EnvironmentStripper.classesIn(context.mods().stream()
                        .filter(BridgeLaunchContext.LoadedMod::runByHost)
                        .map(BridgeLaunchContext.LoadedMod::jar)
                        .distinct()
                        .toList()),
                EnvironmentStripper.QuiltScope.of(context));
        List<ClassTweakerFile> tweakers = InterfaceInjector.classTweakers(context);
        InterfaceInjector injector = InterfaceInjector.of(tweakers.stream()
                .flatMap(file -> file.injectedInterfaces().stream())
                .toList());
        EnumExtender extender = EnumExtender.of(tweakers.stream()
                .flatMap(file -> file.enumExtensions().stream())
                .toList());
        Optional<BridgeClassTransformer> mixin = FabricMixinBootstrap.start(
                context,
                FabricClassTransformer.preMixinView(classes, stripper, injector, extender),
                FABRIC_LOADER_VERSION,
                MIXIN_EXTRAS_VERSION);
        Optional<HostInterfaceDefaults> hostDefaults = context.host().isHosted()
                ? Optional.of(new HostInterfaceDefaults(classes, FabricBridge::reportHostDefault))
                : Optional.empty();
        return Optional.of(new FabricClassTransformer(classes, stripper, injector, extender, mixin, hostDefaults));
    }

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("Poly Mod Loader");

    /** Logs a default method conflict {@link HostInterfaceDefaults} resolved (ADR-0026 42). */
    private static void reportHostDefault(HostInterfaceDefaults.Resolution resolution) {
        String text = "{} {} gets the default method {} from {} and from a Fabric mod's {}; it keeps {}'s (the JVM"
                + " refuses a call of two such defaults), and overrides still win: {}";
        if (resolution.sameCode()) {
            LOGGER.info(
                    text,
                    "[Poly Mod Loader] PLEXUS-COMPAT-223",
                    resolution.type(),
                    resolution.method(),
                    resolution.kept(),
                    resolution.left(),
                    resolution.kept(),
                    "both have the same code");
        } else {
            LOGGER.warn(
                    text,
                    "[Poly Mod Loader] PLEXUS-COMPAT-223 [limited]",
                    resolution.type(),
                    resolution.method(),
                    resolution.kept(),
                    resolution.left(),
                    resolution.kept(),
                    "their code differs, so a call through the mod's interface runs the other default");
        }
    }
    /**
     * Runs the pre-launch entrypoints after Mixin is ready and before the game, in Quilt Loader's order ({@code
     * invokePreLaunch}, ADR-0027): first the Quilt mods' {@code pre_launch} ({@link QuiltLoaderRuntime#invokePreLaunch}),
     * then every Fabric and Quilt mod's {@code preLaunch}, as Fabric Loader runs it. Every failure of one stage is
     * collected and thrown together, as {@code FabricLoader.invokeEntrypoints} does. First the Mixin configurations of
     * other loaders' mods join this Mixin ({@link FabricMixinBootstrap#addShared}).
     */
    @Override
    public void beforeGame(BridgeLaunchContext context) {
        FabricMixinBootstrap.addShared(context);
        QuiltLoaderImpl.INSTANCE.polyModLoaderRuntime().invokePreLaunch();
        FabricLoaderImpl.INSTANCE
                .polyModLoaderRuntime()
                .invokeEntrypoints("preLaunch", PreLaunchEntrypoint.class, PreLaunchEntrypoint::onPreLaunch);
    }

    /** Assesses metadata on its own: a declared class tweaker cannot be read and blocks the mod. */
    @Override
    public BridgeAssessment assess(String metadata, GameSide side) {
        return assess(metadata, side, ModJarContents.NONE);
    }

    @Override
    public BridgeAssessment assess(String metadata, GameSide side, ModJarContents jar) {
        JsonObject root;
        try {
            JsonElement parsed = JsonParser.parseString(metadata);
            if (!parsed.isJsonObject()) {
                return unreadable("the file is not a JSON object");
            }
            root = parsed.getAsJsonObject();
        } catch (JsonParseException | IllegalStateException e) {
            return unreadable(e.getMessage());
        }
        String id = string(root, "id", "?");
        String version = string(root, "version", "?");
        List<CompatibilityFinding> findings = new ArrayList<>();
        if (id.equals("?")) {
            findings.add(new CompatibilityFinding("PLEXUS-COMPAT-100", true, "metadata", "has no text \"id\""));
        }
        JsonElement schema = root.get("schemaVersion");
        if (schema != null && !(isNumber(schema) && schema.getAsDouble() == 1)) {
            findings.add(new CompatibilityFinding(
                    "PLEXUS-COMPAT-100", true, "metadata", "schemaVersion " + schema + " is not 1"));
        }
        List<BridgedInterfaceInjection> mixinSupertypes = List.of();
        if (nonEmpty(root, "mixins")) {
            MixinConfigEntry.Parsed parsed = MixinConfigEntry.parse(root.get("mixins"));
            parsed.problems()
                    .forEach(problem ->
                            findings.add(new CompatibilityFinding("PLEXUS-COMPAT-100", true, "metadata", problem)));
            mixinSupertypes =
                    MixinConfigCheck.check(parsed.entries(), metadataPath(), "Fabric Loader", side, jar, findings);
        }
        ClassTweakerCheck.ClassTweaks tweaks = ClassTweakerCheck.ClassTweaks.NONE;
        if (root.has("accessWidener") && !root.get("accessWidener").isJsonNull()) {
            if (isString(root.get("accessWidener"))) {
                tweaks = ClassTweakerCheck.check(
                        root.get("accessWidener").getAsString(), metadataPath(), "Fabric Loader", jar, findings);
            } else {
                findings.add(new CompatibilityFinding(
                        "PLEXUS-COMPAT-100",
                        true,
                        "metadata",
                        "\"accessWidener\" is not a path text: " + root.get("accessWidener")));
            }
        }
        List<String> nestedJars = nestedJars(root, findings);
        List<BridgedDependency> dependencies = new ArrayList<>();
        dependencies(root, "depends", BridgedDependency.Kind.REQUIRED, dependencies, findings);
        dependencies(root, "recommends", BridgedDependency.Kind.OPTIONAL, dependencies, findings);
        dependencies(root, "suggests", BridgedDependency.Kind.OPTIONAL, dependencies, findings);
        dependencies(root, "breaks", BridgedDependency.Kind.BREAKS, dependencies, findings);
        dependencies(root, "conflicts", BridgedDependency.Kind.CONFLICTS, dependencies, findings);
        List<String> provides = new ArrayList<>();
        if (root.get("provides") instanceof JsonArray array) {
            for (JsonElement element : array) {
                if (isString(element)) {
                    provides.add(element.getAsString());
                }
            }
        }
        // A language adapter is provided by the mod that declares it, and needed by the mods that use it.
        if (root.get("languageAdapters") instanceof JsonObject declaredAdapters) {
            declaredAdapters.keySet().forEach(name -> provides.add(LANGUAGE_ADAPTER_PREFIX + name));
        }
        Set<String> adapters = new TreeSet<>();
        List<BridgedEntrypoint> entrypoints = entrypoints(root, side, findings, adapters);
        for (String adapter : adapters) {
            dependencies.add(new BridgedDependency(
                    LANGUAGE_ADAPTER_PREFIX + adapter, BridgedDependency.Kind.REQUIRED, "*", any -> true));
        }
        String environment = string(root, "environment", "*");
        if ((environment.equals("client") && side == GameSide.DEDICATED_SERVER)
                || (environment.equals("server") && side == GameSide.CLIENT)) {
            findings.add(new CompatibilityFinding(
                    "PLEXUS-COMPAT-108", true, "environment", "mod is " + environment + "-only, process is " + side));
        }
        return BridgeAssessment.of(
                        loaderName(),
                        id,
                        version,
                        findings,
                        entrypoints,
                        provides,
                        dependencies,
                        nestedJars,
                        CompatibilityStatus.EXPERIMENTAL)
                .withAccessWidenings(tweaks.widenings())
                .withInjectedInterfaces(
                        java.util.stream.Stream.concat(tweaks.injections().stream(), mixinSupertypes.stream())
                                .toList());
    }

    /**
     * The {@code jars} entries: each is {@code {"file": "META-INF/jars/x.jar"}}, a jar inside this one that
     * Fabric Loader treats as a mod of its own. The runtime extracts and assesses them.
     */
    private static List<String> nestedJars(JsonObject root, List<CompatibilityFinding> findings) {
        List<String> files = new ArrayList<>();
        JsonElement declared = root.get("jars");
        if (declared == null || declared.isJsonNull()) {
            return files;
        }
        if (!(declared instanceof JsonArray array)) {
            findings.add(new CompatibilityFinding("PLEXUS-COMPAT-100", true, "metadata", "\"jars\" is not an array"));
            return files;
        }
        for (JsonElement element : array) {
            if (element instanceof JsonObject entry && isString(entry.get("file"))) {
                files.add(entry.get("file").getAsString());
            } else {
                findings.add(new CompatibilityFinding(
                        "PLEXUS-COMPAT-100", true, "metadata", "\"jars\" entry " + element + " has no text \"file\""));
            }
        }
        return files;
    }

    /** Fabric Loader's ordering: semantic versions by their parts, anything else by its text. */
    @Override
    public int compareVersions(String first, String second) {
        try {
            return Version.parse(first).compareTo(Version.parse(second));
        } catch (VersionParsingException | RuntimeException e) {
            return first.compareTo(second);
        }
    }

    private BridgeAssessment unreadable(String reason) {
        return BridgeAssessment.of(
                loaderName(),
                "?",
                "?",
                List.of(new CompatibilityFinding("PLEXUS-COMPAT-100", true, "metadata", "unreadable: " + reason)),
                List.of(),
                CompatibilityStatus.EXPERIMENTAL);
    }

    private static List<BridgedEntrypoint> entrypoints(
            JsonObject root, GameSide side, List<CompatibilityFinding> findings, Set<String> adapters) {
        List<BridgedEntrypoint> entrypoints = new ArrayList<>();
        if (!root.has("entrypoints")) {
            return entrypoints;
        }
        if (!(root.get("entrypoints") instanceof JsonObject declared)) {
            findings.add(new CompatibilityFinding(
                    "PLEXUS-COMPAT-100", true, "metadata", "\"entrypoints\" is not an object"));
            return entrypoints;
        }
        for (String type : declared.keySet()) {
            String phase = PHASES.get(type);
            if (phase == null) {
                findings.add(new CompatibilityFinding(
                        "PLEXUS-COMPAT-107",
                        false,
                        "entrypoint '" + type + "'",
                        "not run by Poly Mod Loader; other mods can get it through FabricLoader.getEntrypoints"));
                continue;
            }
            JsonElement list = declared.get(type);
            List<JsonElement> elements = new ArrayList<>();
            if (list instanceof JsonArray array) {
                array.forEach(elements::add);
            } else {
                elements.add(list);
            }
            for (JsonElement element : elements) {
                String adapter = "default";
                if (element instanceof JsonObject object) {
                    adapter = string(object, "adapter", "default");
                    element = object.get("value");
                }
                if (!isString(element)) {
                    findings.add(new CompatibilityFinding(
                            "PLEXUS-COMPAT-100",
                            true,
                            "metadata",
                            "entrypoint '" + type + "' has an entry that is not" + " a class name: " + element));
                    continue;
                }
                String className = element.getAsString();
                if (!adapter.equals("default")) {
                    // Provided by the mod that declares the adapter in languageAdapters; checked as a dependency.
                    adapters.add(adapter);
                }
                boolean applies = phase.equals("common")
                        || phase.equals("preLaunch")
                        || (phase.equals("client") && side == GameSide.CLIENT)
                        || (phase.equals("server") && side == GameSide.DEDICATED_SERVER);
                if (applies) {
                    entrypoints.add(new BridgedEntrypoint(phase, className, adapter));
                }
            }
        }
        return entrypoints;
    }

    /** Reads one relationship block: an object of mod ID to a predicate string or an array of them (any matches). */
    private static void dependencies(
            JsonObject root,
            String field,
            BridgedDependency.Kind kind,
            List<BridgedDependency> dependencies,
            List<CompatibilityFinding> findings) {
        if (!root.has(field)) {
            return;
        }
        if (!(root.get(field) instanceof JsonObject declared)) {
            findings.add(new CompatibilityFinding(
                    "PLEXUS-COMPAT-100", true, "metadata", "\"" + field + "\" is not an object"));
            return;
        }
        for (Map.Entry<String, JsonElement> entry : declared.entrySet()) {
            if (entry.getKey().equals("quilt_loader")
                    && (kind == BridgedDependency.Kind.BREAKS || kind == BridgedDependency.Kind.CONFLICTS)) {
                // Fabric Loader, as which Poly Mod Loader runs Fabric mods, never has quilt_loader; it is built in
                // only so that Fabric jars made for Quilt find the Quilt Loader API they require (builtInMods).
                continue;
            }
            List<String> texts = new ArrayList<>();
            if (entry.getValue() instanceof JsonArray array) {
                for (JsonElement element : array) {
                    texts.add(text(element));
                }
            } else {
                texts.add(text(entry.getValue()));
            }
            List<VersionPredicate> predicates = new ArrayList<>();
            try {
                for (String text : texts) {
                    predicates.add(VersionPredicate.parse(text));
                }
            } catch (VersionParsingException | RuntimeException e) {
                // Fabric Loader refuses such a mod; a requirement that cannot be read cannot be honoured.
                findings.add(new CompatibilityFinding(
                        "PLEXUS-COMPAT-100",
                        kind == BridgedDependency.Kind.REQUIRED || kind == BridgedDependency.Kind.BREAKS,
                        "metadata",
                        field + "." + entry.getKey() + " has an unreadable version requirement " + texts + ": "
                                + e.getMessage()));
                continue;
            }
            String requirement = texts.size() == 1 ? texts.getFirst() : String.join(" || ", texts);
            dependencies.add(new BridgedDependency(entry.getKey(), kind, requirement, accepts(predicates)));
        }
    }

    /** Whether a version satisfies any of the predicates; a version Fabric cannot parse satisfies only {@code *}. */
    private static Predicate<String> accepts(List<VersionPredicate> predicates) {
        return versionText -> {
            Version version;
            try {
                version = Version.parse(versionText);
            } catch (VersionParsingException | RuntimeException e) {
                return predicates.stream()
                        .anyMatch(predicate -> predicate.toString().equals("*"));
            }
            return predicates.stream().anyMatch(predicate -> predicate.test(version));
        };
    }

    /**
     * Creates the entrypoint through {@link FabricLoaderRuntime} (its language adapter, one instance per mod and
     * declaration, the same instance {@code getEntrypoints} returns) and calls it.
     */
    @Override
    public void invokeEntrypoint(BridgedEntrypoint entrypoint, ClassLoader gameClassLoader) throws Exception {
        FabricLoaderImpl.INSTANCE.polyModLoaderRuntime().invokeModEntrypoint(entrypoint);
    }

    private static boolean nonEmpty(JsonObject root, String field) {
        return root.has(field)
                && !root.get(field).isJsonNull()
                && (!root.get(field).isJsonArray()
                        || !root.getAsJsonArray(field).isEmpty());
    }

    private static boolean isString(JsonElement element) {
        return element != null
                && element.isJsonPrimitive()
                && element.getAsJsonPrimitive().isString();
    }

    private static boolean isNumber(JsonElement element) {
        return element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber();
    }

    /** The element's text if it is a JSON string, its JSON form otherwise. */
    private static String text(JsonElement element) {
        return isString(element) ? element.getAsString() : String.valueOf(element);
    }

    private static String string(JsonObject object, String field, String fallback) {
        JsonElement value = object.get(field);
        return isString(value) ? value.getAsString() : fallback;
    }
}
