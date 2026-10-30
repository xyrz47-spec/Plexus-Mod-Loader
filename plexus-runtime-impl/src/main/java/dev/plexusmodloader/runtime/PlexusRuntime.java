package dev.plexusmodloader.runtime;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.plexusmodloader.api.event.lifecycle.LifecycleEvents;
import dev.plexusmodloader.api.event.lifecycle.ModsInitializedListener;
import dev.plexusmodloader.compat.bridge.BridgeAssessment;
import dev.plexusmodloader.compat.bridge.BridgeLaunchContext;
import dev.plexusmodloader.compat.bridge.BridgedEntrypoint;
import dev.plexusmodloader.compat.bridge.CompatibilityFinding;
import dev.plexusmodloader.compat.bridge.GameHost;
import dev.plexusmodloader.compat.bridge.HostedLaunch;
import dev.plexusmodloader.game.version.GameVersionAdapter;
import dev.plexusmodloader.game.version.layer.GameLayer;
import dev.plexusmodloader.game.version.layer.GameLayerContext;
import dev.plexusmodloader.game.version.layer.LoadedModCounts;
import dev.plexusmodloader.game.version.layer.ModResourcePack;
import dev.plexusmodloader.internal.transform.AccessWidening;
import dev.plexusmodloader.internal.transform.AccessWideningProblem;
import dev.plexusmodloader.internal.transform.TransformFailurePolicy;
import dev.plexusmodloader.internal.transform.TransformProblem;
import dev.plexusmodloader.internal.transform.TransformRuleDefinition;
import dev.plexusmodloader.internal.transform.engine.ClassTransformExecutor;
import dev.plexusmodloader.internal.transform.engine.ClassTransformPlanner;
import dev.plexusmodloader.internal.transform.engine.DirectoryTransformCache;
import dev.plexusmodloader.internal.transform.engine.TransformCache;
import dev.plexusmodloader.internal.transform.engine.TransformPlan;
import dev.plexusmodloader.runtime.classloading.GameClassLoader;
import dev.plexusmodloader.runtime.compat.CrossLoaderLoadOrder;
import dev.plexusmodloader.runtime.compat.CrossLoaderModResolver;
import dev.plexusmodloader.runtime.compat.ForeignAccessWidenings;
import dev.plexusmodloader.runtime.compat.HostBuildMatcher;
import dev.plexusmodloader.runtime.compat.HostPatchCheck;
import dev.plexusmodloader.runtime.compat.ModLinkVerifier;
import dev.plexusmodloader.runtime.dependency.DependencyProblem;
import dev.plexusmodloader.runtime.dependency.ModDependencyResolver;
import dev.plexusmodloader.runtime.diagnostics.PlexusLaunchException;
import dev.plexusmodloader.runtime.diagnostics.StartupReport;
import dev.plexusmodloader.runtime.diagnostics.TransformationReport;
import dev.plexusmodloader.runtime.discovery.DiscoveredModFile;
import dev.plexusmodloader.runtime.discovery.ForeignModResolver;
import dev.plexusmodloader.runtime.discovery.HostModules;
import dev.plexusmodloader.runtime.discovery.ModFileKind;
import dev.plexusmodloader.runtime.discovery.ModFileScanner;
import dev.plexusmodloader.runtime.discovery.NestedJarExtractor;
import dev.plexusmodloader.runtime.event.EventBus;
import dev.plexusmodloader.runtime.lifecycle.EntrypointInvoker;
import dev.plexusmodloader.runtime.lifecycle.RuntimeLoadedMods;
import dev.plexusmodloader.runtime.lifecycle.RuntimePhase;
import dev.plexusmodloader.runtime.lifecycle.ShutdownCoordinator;
import dev.plexusmodloader.runtime.metadata.MetadataProblem;
import dev.plexusmodloader.runtime.metadata.ModMetadata;
import dev.plexusmodloader.runtime.metadata.ModMetadataReader;
import dev.plexusmodloader.runtime.metadata.version.SemanticVersion;
import dev.plexusmodloader.runtime.nativeapi.NativeRegistrations;
import dev.plexusmodloader.runtime.system.BuiltInSystems;
import dev.plexusmodloader.runtime.system.SystemDefinition;
import dev.plexusmodloader.runtime.system.SystemRegistry;
import dev.plexusmodloader.runtime.system.SystemReplacementResolver;
import dev.plexusmodloader.runtime.transform.AdapterTransformRules;
import dev.plexusmodloader.runtime.transform.ModTransformRules;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs Poly Mod Loader's launch pipeline (design doc §11) and then hands control to Minecraft's {@code main}.
 * Every phase is logged with the mod IDs and versions involved (§21 item 7). Problems found in a
 * phase are all reported together, and the game never starts after an error-level problem.
 */
public final class PlexusRuntime {

    private static final Logger LOGGER = LoggerFactory.getLogger("Poly Mod Loader");

    /**
     * Starts Minecraft through Poly Mod Loader and returns when the game's {@code main} returns. The dedicated
     * server keeps running on its own threads after that, so callers must not close the returned
     * game unless they know the game has stopped (for example in tests).
     */
    public LaunchedGame launch(LaunchConfiguration configuration, String[] gameArguments) throws Throwable {
        // BOOTSTRAP
        SemanticVersion runtimeVersion = runtimeVersion();
        LOGGER.info(
                "[Poly Mod Loader] Phase {}: Poly Mod Loader {} on Java {} ({})",
                RuntimePhase.BOOTSTRAP,
                runtimeVersion,
                Runtime.version(),
                System.getProperty("java.vendor"));
        String gameVersion = readGameVersion(configuration.gameJar());
        GameVersionAdapter adapter = selectAdapter(gameVersion);
        LOGGER.info(
                "[Poly Mod Loader] Minecraft {} ({}) via adapter {}, launch target {}",
                gameVersion,
                configuration.side(),
                adapter.adapterId(),
                configuration.target().id());
        // Poly Mod Loader's early loading window, prepared hidden while the mods are found.
        StartupWindow startup = StartupWindow.start(configuration, adapter, gameVersion, gameArguments);

        // DISCOVERY + compatibility classification
        List<DiscoveredModFile> files =
                new ModFileScanner().scan(configuration.modsDirectory(), configuration.devModJars());
        LOGGER.info(
                "[Poly Mod Loader] Phase {}: {} mod file(s) in {} and {} dev mod jar(s)",
                RuntimePhase.DISCOVERY,
                files.size(),
                configuration.modsDirectory(),
                configuration.devModJars().size());
        startup.discovered(files.size());
        List<DiscoveredModFile> nativeFiles = new ArrayList<>();
        List<StartupReport.BlockedFile> blockedFiles = new ArrayList<>();
        ForeignModResolver foreignMods = new ForeignModResolver(PlexusRuntime.class.getClassLoader());
        // A loader that loads its own mods and starts the game: MinecraftForge through FML (ADR-0021), or, without
        // Forge
        // mods, NeoForge through FML 11 (ADR-0026).
        List<Path> forgeJars = forgeJars(files);
        boolean forge =
                !forgeJars.isEmpty() || System.getProperty("poly.forge", "auto").equals("always");
        List<Path> neoForgeJars = forge ? List.of() : neoForgeJars(files);
        if (forge) {
            reportNeoForgeBesideForge(files);
        }
        List<Path> hostJars = forge ? forgeJars : neoForgeJars;
        if (forge) {
            startup.hostStarting("Forge");
        } else if (!neoForgeJars.isEmpty()) {
            startup.hostStarting("NeoForge");
        }
        // With Poly Mod Loader's early window, the host's FML opens none.
        Optional<GameHost> host = forge
                ? gameHost(configuration, gameVersion, gameArguments, files, forgeJars, foreignMods, startup.shows())
                : neoForgeHost(
                        configuration, gameVersion, gameArguments, files, neoForgeJars, foreignMods, startup.shows());
        List<ForeignModResolver.ForeignMod> assessedForeignMods = new ArrayList<>();
        List<StartupReport.ForeignMod> foreignReport = new ArrayList<>();
        for (DiscoveredModFile file : files) {
            if (file.kind() == ModFileKind.POLY_NATIVE) {
                nativeFiles.add(file);
                continue;
            }
            if (file.kind() == ModFileKind.UNREADABLE) {
                blockedFiles.add(new StartupReport.BlockedFile(
                        file.path().getFileName().toString(),
                        file.kind().name(),
                        "Unreadable",
                        "PLEXUS-DISCOVERY-003"));
                LOGGER.warn(
                        "[Poly Mod Loader] PLEXUS-DISCOVERY-003 {} cannot be read as a jar ({}); it is NOT loaded. "
                                + "Download it again.",
                        file.path().getFileName(),
                        file.detail().orElse("?"));
                continue;
            }
            boolean declined = host.isPresent()
                    && host.get().declinedJars().contains(file.path())
                    && file.kind() != ModFileKind.NEOFORGE
                    && file.kind() != ModFileKind.FORGE
                    && file.kind() != ModFileKind.FML_LIBRARY;
            if (declined) {
                // A jar built for several loaders that the host does not run: its other loader runs it, as that loader
                // would without the host.
                LOGGER.info(
                        "[Poly Mod Loader] {} does not run {}, which is built for {} too; the {} bridge runs it",
                        host.get().name(),
                        file.path().getFileName(),
                        file.kind(),
                        file.kind());
            } else if (host.isPresent() && (hostJars.contains(file.path()) || file.kind() == ModFileKind.FML_LIBRARY)) {
                // FML finds these in mods/ itself and loads them.
                continue;
            }
            if (file.kind() == ModFileKind.FML_LIBRARY) {
                blockedFiles.add(new StartupReport.BlockedFile(
                        file.path().getFileName().toString(), file.kind().name(), "Blocked", "PLEXUS-COMPAT-205"));
                LOGGER.warn(
                        "[Poly Mod Loader] PLEXUS-COMPAT-205 {} is a NeoForge/Forge library (FMLModType {}); FML is not"
                                + " available, so it is NOT loaded.",
                        file.path().getFileName(),
                        file.detail().orElse("?"));
                continue;
            }
            if (file.kind() == ModFileKind.UNKNOWN) {
                blockedFiles.add(new StartupReport.BlockedFile(
                        file.path().getFileName().toString(), file.kind().name(), "NotAMod", "PLEXUS-DISCOVERY-002"));
                LOGGER.warn(
                        "[Poly Mod Loader] PLEXUS-DISCOVERY-002 {} has no known mod metadata ({}, fabric.mod.json, "
                                + "quilt.mod.json, META-INF/neoforge.mods.toml, META-INF/mods.toml); it is NOT"
                                + " loaded.",
                        file.path().getFileName(),
                        ModMetadata.FILE_NAME);
                continue;
            }
            Optional<ForeignModResolver.ForeignMod> foreign = foreignMods.assess(file, configuration.side());
            if (foreign.isEmpty()) {
                blockedFiles.add(new StartupReport.BlockedFile(
                        file.path().getFileName().toString(), file.kind().name(), "Blocked", "PLEXUS-COMPAT-001"));
                LOGGER.warn(
                        "[Poly Mod Loader] PLEXUS-COMPAT-001 {} is a {} mod and no bridge for it is installed (bridges: {}). "
                                + "The mod is NOT loaded.",
                        file.path().getFileName(),
                        file.kind(),
                        foreignMods.bridgeNames());
                continue;
            }
            assessedForeignMods.add(foreign.get());
        }
        // Jar-in-jar: every nested jar is a mod of its own (Fabric Loader's model), extracted by content hash.
        NestedJarExtractor.Result nestedJars = new NestedJarExtractor(
                        foreignMods, configuration.reportDirectory().resolve("nested-jars"), configuration.side())
                .expand(assessedForeignMods);
        assessedForeignMods = new ArrayList<>(nestedJars.mods());
        // The IDs of mods left out for the host's build of them, to that build's version, and the host mods that stand
        // in for them, to those IDs (ADR-0026 37).
        Map<String, String> hostStandIns = new LinkedHashMap<>();
        Map<String, Set<String>> hostAliases = new HashMap<>();
        if (host.isPresent()) {
            assessedForeignMods =
                    withoutHostMods(host.get(), assessedForeignMods, blockedFiles, hostStandIns, hostAliases);
            // Mods whose code does not fit the host's patch are not loaded, rather than run with parts left out
            // (ADR-0030); the dependency check below then leaves out the mods that need them.
            List<Path> patchedJars = new ArrayList<>();
            patchedJars.add(host.get().gameJar());
            patchedJars.addAll(host.get().classBytesJars());
            patchedJars.addAll(host.get().resourceJars());
            assessedForeignMods = HostPatchCheck.apply(
                    configuration.side(),
                    hostedLaunch(host.get(), configuration.gameJar()),
                    patchedJars.stream().distinct().toList(),
                    PlexusRuntime.class.getClassLoader(),
                    foreignMods.bridges(),
                    assessedForeignMods);
            // A left-out Fabric API module runs as Poly Mod Loader's build on the host's hooks, when there is one for
            // this host release (ADR-0031); that build must fit the patch too.
            HostModules.Result hostModules = HostModules.replaceLeftOut(
                    host.get().name(),
                    assessedForeignMods,
                    foreignMods,
                    configuration.side(),
                    configuration.reportDirectory().resolve("host-modules"),
                    PlexusRuntime.class.getClassLoader());
            for (Path[] replaced : hostModules.replaced()) {
                LOGGER.info(
                        "[Poly Mod Loader] {} {} {}",
                        replaced[0].getFileName(),
                        "does not fit the host's patch (PLEXUS-COMPAT-224); Poly Mod Loader runs its build on the host's hooks instead (ADR-0031):",
                        replaced[1].getFileName());
            }
            if (!hostModules.replaced().isEmpty()) {
                assessedForeignMods = HostPatchCheck.apply(
                        configuration.side(),
                        hostedLaunch(host.get(), configuration.gameJar()),
                        patchedJars.stream().distinct().toList(),
                        PlexusRuntime.class.getClassLoader(),
                        foreignMods.bridges(),
                        hostModules.mods());
            }
        }
        for (NestedJarExtractor.NestedFile file : nestedJars.otherFiles()) {
            blockedFiles.add(new StartupReport.BlockedFile(
                    file.container() + "!/" + file.entry(), file.kind().name(), "NotAMod", "PLEXUS-DISCOVERY-002"));
            LOGGER.warn(
                    "[Poly Mod Loader] PLEXUS-DISCOVERY-002 {} inside {} is not a mod of that loader ({}); it is NOT loaded.",
                    file.entry(),
                    file.container(),
                    file.kind());
        }

        // METADATA_VALIDATION
        List<String> metadataErrors = new ArrayList<>();
        List<ModMetadata> mods = new ArrayList<>();
        Map<String, Path> modJars = new HashMap<>();
        ModMetadataReader reader = new ModMetadataReader();
        for (DiscoveredModFile file : nativeFiles) {
            ModMetadataReader.Result result =
                    reader.read(file.nativeMetadataJson().orElseThrow());
            for (MetadataProblem problem : result.problems()) {
                String line = file.path().getFileName() + " " + problem;
                if (problem.isError()) {
                    metadataErrors.add(line);
                } else {
                    LOGGER.warn("[Poly Mod Loader] {}", line);
                }
            }
            result.metadata().ifPresent(metadata -> {
                mods.add(metadata);
                modJars.putIfAbsent(metadata.id().value(), file.path());
            });
        }
        failIfAny(RuntimePhase.METADATA_VALIDATION, metadataErrors);

        startup.resolving();
        // DEPENDENCY_RESOLUTION, across loaders: foreign mods first (they may be blocked, which Poly Mods
        // that need them must hear about), then Poly Mods with the foreign result.
        Map<String, String> nativeVersions = new HashMap<>();
        mods.forEach(mod ->
                nativeVersions.putIfAbsent(mod.id().value(), mod.version().toString()));
        CrossLoaderModResolver.Result foreignResolution;
        // Mods that need a left-out build link against the host's build that stands in for it (ADR-0026 37).
        List<Path> linkSources = new ArrayList<>(modJars.values());
        if (host.isPresent()) {
            Path nestedHostDir = configuration.reportDirectory().resolve("nested-host-jars");
            for (BridgeLaunchContext.LoadedMod mod : host.get().mods()) {
                if (hostAliases.containsKey(mod.modId()) || !mod.providedIds().isEmpty()) {
                    if (mod.nestedEntry() == null) {
                        if (mod.jar() != null && !linkSources.contains(mod.jar())) {
                            linkSources.add(mod.jar());
                        }
                    } else if (mod.jar() != null) {
                        Path extracted = extractNestedHostJar(nestedHostDir, mod.jar(), mod.nestedEntry());
                        if (extracted != null && !linkSources.contains(extracted)) {
                            linkSources.add(extracted);
                        }
                    }
                }
            }
        }
        // With a host the mods run on its patched game and its loader's classes, which come first (ADR-0031: a host
        // module calls what the patch adds); the vanilla jar stays for what the host jars do not hold.
        List<Path> linkGameJars = new ArrayList<>();
        host.ifPresent(found -> {
            linkGameJars.add(found.gameJar());
            linkGameJars.addAll(found.classBytesJars());
            linkGameJars.addAll(found.resourceJars());
        });
        linkGameJars.add(configuration.gameJar());
        try (ModLinkVerifier linkVerifier = new ModLinkVerifier(
                linkGameJars.stream().distinct().toList(),
                PlexusRuntime.class.getClassLoader(),
                configuration.side())) {
            // Classes in the host loader's packages come from the host, not from copies mods carry (ADR-0014).
            host.ifPresent(found -> linkVerifier.hostLoaderJars(found.loaderJars()));
            linkVerifier.absentOnThisSide(assessedForeignMods.stream()
                    .filter(mod -> mod.assessment().findings().stream()
                            .anyMatch(finding -> finding.code().equals("PLEXUS-COMPAT-108")))
                    .map(ForeignModResolver.ForeignMod::jar)
                    .toList());
            linkVerifier.injectInterfaces(assessedForeignMods.stream()
                    .flatMap(mod -> mod.assessment().injectedInterfaces().stream())
                    .toList());
            foreignResolution = new CrossLoaderModResolver()
                    .resolve(
                            assessedForeignMods,
                            nativeVersions,
                            hostStandIns,
                            linkSources,
                            gameVersion,
                            String.valueOf(Runtime.version().feature()),
                            (mod, otherJars) -> linkVerifier.verify(
                                    mod.jar(),
                                    otherJars,
                                    mod.bridge(),
                                    mod.assessment().entrypoints().stream()
                                            .map(BridgedEntrypoint::className)
                                            .toList(),
                                    mod.assessment().accessWidenings(),
                                    mod.assessment().injectedInterfaces()));
        }
        List<ForeignModResolver.ForeignMod> bridgedMods = foreignResolution.loadable();
        for (ForeignModResolver.ForeignMod mod : foreignResolution.mods()) {
            BridgeAssessment assessment = mod.assessment();
            LOGGER.atLevel(assessment.status().loadable() ? org.slf4j.event.Level.INFO : org.slf4j.event.Level.WARN)
                    .log(
                            "[Poly Mod Loader] {} mod {} {} ({}): {}{}",
                            assessment.loader(),
                            assessment.modId(),
                            assessment.version(),
                            mod.jar().getFileName(),
                            assessment.status(),
                            assessment.status().loadable()
                                    ? " - loaded through the " + assessment.loader() + " bridge"
                                    : " - NOT loaded");
            for (CompatibilityFinding finding : assessment.findings()) {
                LOGGER.atLevel(finding.blocking() ? org.slf4j.event.Level.WARN : org.slf4j.event.Level.INFO)
                        .log("[Poly Mod Loader]   {}", finding);
            }
            foreignReport.add(new StartupReport.ForeignMod(
                    assessment.loader(),
                    assessment.modId(),
                    assessment.version(),
                    mod.jar().getFileName().toString(),
                    assessment.status().name(),
                    assessment.findings().stream()
                            .map(CompatibilityFinding::toString)
                            .toList()));
        }
        LOGGER.info(
                "[Poly Mod Loader] Foreign mods: {} loaded, {} not loaded",
                bridgedMods.size(),
                foreignResolution.mods().size() - bridgedMods.size());
        ModDependencyResolver.Result resolution = new ModDependencyResolver()
                .resolve(
                        mods,
                        gameVersion,
                        configuration.side(),
                        runtimeVersion,
                        foreignResolution.loadableVersions(),
                        foreignResolution.unavailable());
        failIfAny(
                RuntimePhase.DEPENDENCY_RESOLUTION,
                resolution.problems().stream().map(DependencyProblem::toString).toList());
        for (ModMetadata mod : resolution.loadOrder()) {
            LOGGER.info(
                    "[Poly Mod Loader] Loaded mod {} {} ({}) from {}",
                    mod.id(),
                    mod.version(),
                    mod.name(),
                    modJars.get(mod.id().value()).getFileName());
        }
        // The host's mods count by their loader: NeoForge's FML runs NeoForge mods, MinecraftForge's Forge mods.
        Set<String> hostNeoForgeModIds = host.map(found -> found.mods().stream()
                        .filter(mod -> mod.loader().equals(LoadedModCounter.NEOFORGE))
                        .map(BridgeLaunchContext.LoadedMod::modId)
                        .collect(Collectors.toSet()))
                .orElse(Set.of());
        // Mods left out because Poly Mod Loader cannot run them on the host's patched game (ADR-0030): said once more,
        // together, and counted on the title screen, so that the player does not take them for broken mods.
        List<String> hostIncompatible = foreignResolution.mods().stream()
                .filter(mod -> mod.assessment().blockingCodes().contains(HostPatchCheck.CODE))
                .map(mod -> mod.assessment().modId())
                .distinct()
                .toList();
        if (!hostIncompatible.isEmpty()) {
            LOGGER.warn(
                    "[Poly Mod Loader] {} {} mod(s) are NOT loaded, because Poly Mod Loader cannot run them as written on"
                            + " the game as {} patches it: {}. Mods that need them are not loaded either"
                            + " (PLEXUS-COMPAT-114). This is a limitation of Poly Mod Loader, not a bug in these mods:"
                            + " report it to Poly Mod Loader, not to their authors. Each mod's reasons are listed above",
                    HostPatchCheck.CODE,
                    hostIncompatible.size(),
                    host.map(GameHost::name).orElse("the host"),
                    String.join(", ", hostIncompatible));
        }
        LoadedModCounts modCounts = LoadedModCounter.count(
                        resolution.loadOrder().stream()
                                .map(mod -> mod.id().value())
                                .toList(),
                        bridgedMods.stream()
                                .collect(Collectors.groupingBy(
                                        mod -> mod.bridge().loaderName(),
                                        Collectors.mapping(
                                                mod -> mod.assessment().modId(), Collectors.toList()))),
                        host.map(GameHost::modIds).orElse(Set.of()).stream()
                                .filter(id -> !hostNeoForgeModIds.contains(id))
                                .toList(),
                        hostNeoForgeModIds)
                .withHostIncompatibleMods(hostIncompatible.size());
        LOGGER.info(
                "[Poly Mod Loader] Loaded mods per loader: {} Poly, {} Fabric, {} Quilt, {} Forge, {} NeoForge",
                modCounts.polyMods(),
                modCounts.fabricMods(),
                modCounts.quiltMods(),
                modCounts.forgeMods(),
                modCounts.neoForgeMods());
        startup.modsLoaded(modCounts);

        // CONFLICT_RESOLUTION (system replacements are decided from metadata, before mod code runs)
        List<SystemDefinition<?>> systemDefinitions = BuiltInSystems.definitions();
        SystemReplacementResolver.Result replacements =
                new SystemReplacementResolver().resolve(resolution.loadOrder(), systemDefinitions);
        LOGGER.info(
                "[Poly Mod Loader] Phase {}: {} system(s), {} replacement(s)",
                RuntimePhase.CONFLICT_RESOLUTION,
                systemDefinitions.size(),
                replacements.replacements().size());
        failIfAny(RuntimePhase.CONFLICT_RESOLUTION, replacements.problems());

        // TRANSFORMATION_PLANNING
        startup.preparing();
        List<TransformRuleDefinition> rules =
                new ArrayList<>(AdapterTransformRules.forGameVersion(adapter, gameVersion));
        // Poly Mod Loader's rules for a host's patched game, where the patch replaced what an adapter rule changes.
        String gameLayerPackage = adapter.gameLayerClass()
                .map(name -> name.substring(0, name.lastIndexOf('.')).replace('.', '/'))
                .orElse("");
        host.ifPresent(found -> rules.addAll(found.transformRules(gameLayerPackage)));
        // Diagnosis only: -Dpoly.transform.disable=<rule IDs, comma-separated, or *> leaves built-in rules out, to find
        // one that conflicts with another loader's patches. Poly Mod features that rely on them stop working.
        String disabled = System.getProperty("poly.transform.disable", "").strip();
        if (!disabled.isEmpty()) {
            List<String> ids = List.of(disabled.split("\s*,\s*"));
            List<TransformRuleDefinition> left = rules.stream()
                    .filter(rule -> ids.contains("*") || ids.contains(rule.ruleId()))
                    .toList();
            rules.removeAll(left);
            LOGGER.warn(
                    "[Poly Mod Loader] {} built-in transformation rule(s) left out by poly.transform.disable: {}",
                    left.size(),
                    left.stream().map(TransformRuleDefinition::ruleId).toList());
        }
        ModTransformRules.Result modRules = ModTransformRules.from(resolution.loadOrder());
        failIfAny(RuntimePhase.TRANSFORMATION_PLANNING, modRules.problems());
        rules.addAll(modRules.rules());
        TransformPlan plan;
        List<AccessWidening> widenings = new ArrayList<>(modRules.widenings());
        int foreignWidenings;
        Path plannedGameJar = host.map(GameHost::gameJar).orElse(configuration.gameJar());
        try (JarFile gameJar = new JarFile(plannedGameJar.toFile())) {
            // Class tweakers of loadable Fabric mods, already checked against this game by the link check.
            List<AccessWidening> foreign = ForeignAccessWidenings.from(
                    bridgedMods, gameVersion, configuration.side(), internalName -> readClass(gameJar, internalName));
            foreignWidenings = foreign.size();
            widenings.addAll(foreign);
            // The host's own classes its rules target (NeoForge's universal jar) come after the game jar.
            List<JarFile> hostClassJars = new ArrayList<>();
            try {
                for (Path jar : host.map(GameHost::transformTargetJars).orElse(List.of())) {
                    hostClassJars.add(new JarFile(jar.toFile()));
                }
                plan = new ClassTransformPlanner()
                        .plan(
                                rules,
                                widenings,
                                gameVersion,
                                configuration.side(),
                                internalName -> readClass(gameJar, hostClassJars, internalName));
            } finally {
                for (JarFile jar : hostClassJars) {
                    jar.close();
                }
            }
        }
        LOGGER.info(
                "[Poly Mod Loader] Transformation plan: {} rule(s) accepted, {} rejected ({} from mods)",
                plan.acceptedRuleCount(),
                plan.rejected().size(),
                modRules.rules().size());
        if (!widenings.isEmpty()) {
            LOGGER.info(
                    "[Poly Mod Loader] Access widenings: {} accepted, {} rejected ({} from foreign mods' class tweakers)",
                    plan.acceptedWideningCount(),
                    plan.rejectedWidenings().size(),
                    foreignWidenings);
        }
        for (TransformProblem problem : plan.rejected()) {
            if (host.isPresent()
                    && problem.rule().ownerModId().equals("poly")
                    && problem.rule().failurePolicy() == TransformFailurePolicy.DISABLE_RULE) {
                // The host's patch replaced the code an optional rule of Poly Mod Loader changes (Forge sets the server
                // brand and draws the title screen's branding itself); the host's own code runs there instead.
                LOGGER.info(
                        "[Poly Mod Loader] {} patched the target of Poly Mod Loader's optional rule {}; that rule is off"
                                + " ({})",
                        host.get().name(),
                        problem.rule().ruleId(),
                        problem);
            } else {
                LOGGER.warn("[Poly Mod Loader] Rejected transformation: {}", problem);
            }
        }
        for (AccessWideningProblem problem : plan.rejectedWidenings()) {
            LOGGER.warn("[Poly Mod Loader] Rejected access widening: {}", problem);
        }
        List<String> aborting = new ArrayList<>(plan.abortingProblemMessages());
        if (host.isPresent()) {
            // A host loader that patched the game (Forge) may have reshaped a method Poly Mod Loader's own rules
            // change;
            // the host's patch provides that part of the game then (Forge pages creative tabs itself), so the rule is
            // off instead of stopping the launch.
            for (TransformProblem problem : plan.abortingProblems()) {
                if (problem.rule().ownerModId().equals("poly")) {
                    aborting.remove(problem.toString());
                    LOGGER.warn(
                            "[Poly Mod Loader] {} patched the target of Poly Mod Loader's rule {}; that rule is off: {}",
                            host.get().name(),
                            problem.rule().ruleId(),
                            problem);
                }
            }
        }
        failIfAny(RuntimePhase.TRANSFORMATION_PLANNING, aborting);
        Optional<TransformCache> cache = configuration
                .transformCacheDirectory()
                .map(directory -> new DirectoryTransformCache(
                        directory,
                        warning -> LOGGER.warn("[Poly Mod Loader] {}", warning),
                        configuration.transformCacheMaxBytes()));
        ClassTransformExecutor executor = new ClassTransformExecutor(
                plan,
                configuration.transformDumpDirectory(),
                entry -> LOGGER.info(
                        "[Poly Mod Loader] Transformed {} with {} (sha256 {} -> {}){}",
                        entry.className(),
                        entry.appliedRuleIds(),
                        entry.sha256Before(),
                        entry.sha256After(),
                        entry.fromCache() ? " from cache" : ""),
                cache,
                ClassTransformExecutor.fingerprint(runtimeVersion.toString()));

        List<Path> gameJars = new ArrayList<>();
        if (host.isEmpty()) {
            gameJars.add(configuration.gameJar());
        }
        Optional<Path> gameLayerJar = adapter.gameLayerClass().map(PlexusRuntime::locateGameLayerJar);
        gameLayerJar.ifPresent(gameJars::add);
        resolution.loadOrder().forEach(mod -> gameJars.add(modJars.get(mod.id().value())));
        bridgedMods.forEach(mod -> gameJars.add(mod.jar()));
        if (!bridgedMods.isEmpty()) {
            // Mods of other loaders may change library classes (Mixin); the game class loader owns the game's libraries
            // then, as Fabric Loader's Knot does (ADR-0020).
            List<Path> libraries = dev.plexusmodloader.runtime.classloading.GameLibraries.fromClassPath(
                    System.getProperty("java.class.path", ""), configuration.gameJar());
            gameJars.addAll(libraries);
            LOGGER.info(
                    "[Poly Mod Loader] {} game library jar(s) load in the game class loader, so foreign mods can change them",
                    libraries.size());
        }
        List<String> sharedLibraries = foreignMods.bridges().stream()
                .flatMap(bridge -> bridge.sharedLibraryPackages().stream())
                .toList();
        GameClassLoader gameClassLoader = new GameClassLoader(
                gameJars,
                host.map(GameHost::resourceJars).orElse(List.of()),
                host.map(GameHost::classBytesJars).orElse(List.of()),
                PlexusRuntime.class.getClassLoader(),
                executor,
                sharedLibraries,
                warning -> LOGGER.warn("[Poly Mod Loader] {}", warning));
        hideIdleLoaderApis(foreignMods, bridgedMods, gameClassLoader);
        // Mod code that runs from here on (Mixin config plugins, Fabric's preLaunch) finds its services through
        // ServiceLoader.load(Class), which reads the context class loader; Fabric's Knot sets it this early too.
        Thread.currentThread().setContextClassLoader(gameClassLoader);

        // Bridges that emulate their loader's runtime API (Fabric's FabricLoader) see every loaded mod first, and
        // bridges whose loader transforms classes (Fabric's Mixin) install that before any class is defined.
        List<CrossLoaderLoadOrder.LoadedMod> order = CrossLoaderLoadOrder.of(resolution.loadOrder(), bridgedMods);
        List<BridgeLaunchContext.LoadedMod> polyRunMods =
                order.stream().map(mod -> loadedMod(mod, modJars)).toList();
        // Each loader's mod list shows the other loaders' mods too (ADR-0025): the host's mods come first, so that on
        // the other loaders' side a host mod's ID is not taken by another mod's provided ID.
        List<BridgeLaunchContext.LoadedMod> everyMod = new ArrayList<>();
        host.ifPresent(found -> found.mods().forEach(mod -> everyMod.add(withAliases(mod, hostAliases))));
        everyMod.addAll(polyRunMods);
        BridgeLaunchContext bridgeContext = new BridgeLaunchContext(
                configuration.side(),
                gameVersion,
                plannedGameJar,
                Path.of("").toAbsolutePath(),
                List.of(gameArguments),
                gameClassLoader,
                everyMod,
                () -> adapter.gameInstance().orElse(null),
                host.map(found -> hostedLaunch(found, configuration.gameJar())).orElse(HostedLaunch.NONE));
        prepareBridges(foreignMods, bridgeContext, gameClassLoader);
        // The host defines its loader's layers (Forge's mods) before code that runs before the game: a Mixin shared
        // with the host's mods finds their configurations' classes there.
        if (host.isPresent()) {
            prepareHost(host.get(), polyRunMods, gameClassLoader);
            // A host whose loader found its mods' Mixin configurations while it started (NeoForge's FML) has them now.
            bridgeContext = withHostMixinConfigs(bridgeContext, host.get());
        }
        runBeforeGame(foreignMods, bridgeContext);

        // SYSTEM_REGISTRATION
        SystemRegistry systems;
        try {
            systems = SystemRegistry.create(systemDefinitions, replacements, configuration.side(), gameClassLoader);
        } catch (SystemRegistry.SystemSetupException e) {
            throw new PlexusLaunchException(RuntimePhase.SYSTEM_REGISTRATION.name(), e.problems());
        }
        for (SystemRegistry.ActiveSystem system : systems.activeSystems()) {
            LOGGER.info(
                    "[Poly Mod Loader] System {}: {} from {}{}",
                    system.systemId(),
                    system.providerClass(),
                    system.isDefault() ? "poly (default)" : system.providerModId(),
                    system.overriddenModIds().isEmpty() ? "" : ", overriding " + system.overriddenModIds());
        }
        adapter.bindSystems(systems);
        EventBus events = new EventBus();
        adapter.bindEvents(events);
        Path report = startupReport(
                        runtimeVersion,
                        gameVersion,
                        configuration,
                        adapter,
                        resolution.loadOrder(),
                        modJars,
                        blockedFiles,
                        foreignReport,
                        systems,
                        plan)
                .writeTo(configuration.reportDirectory());
        LOGGER.info("[Poly Mod Loader] Startup report: {}", report.toAbsolutePath());

        // LIFECYCLE_INITIALIZATION
        LOGGER.info(
                "[Poly Mod Loader] Phase {}: {} mod(s)",
                RuntimePhase.LIFECYCLE_INITIALIZATION,
                resolution.loadOrder().size());
        startup.initialisingMods(order.size());
        ShutdownCoordinator shutdown = new ShutdownCoordinator();
        shutdown.installJvmShutdownHook();
        // Registered first, so it runs last: the report then covers classes loaded during mod shutdown.
        shutdown.register(
                "poly",
                "write transformation report",
                () -> new TransformationReport(gameVersion, executor.report())
                        .writeTo(configuration.reportDirectory()));
        NativeRegistrations registrations = new NativeRegistrations(configuration.side());
        EntrypointInvoker invoker = new EntrypointInvoker(
                gameClassLoader,
                configuration.side(),
                shutdown,
                systems,
                events,
                registrations,
                RuntimeLoadedMods.of(bridgeContext.mods()));
        // Common entrypoints of every mod, then side-specific ones; within each, required mods first,
        // whatever their loader.
        // Poly Mods declare their content before the game starts; their entrypoints run now.
        for (CrossLoaderLoadOrder.LoadedMod mod : order) {
            if (mod.nativeMod() != null) {
                invoker.invokeCommon(mod.nativeMod());
            }
        }
        for (CrossLoaderLoadOrder.LoadedMod mod : order) {
            if (mod.nativeMod() != null) {
                invoker.invokeSide(mod.nativeMod());
            }
        }
        // Foreign mods are initialised where their own loader does it, inside the game's start (Fabric: after
        // Minecraft's bootstrap; ADR-0020), when the version adapter has that point; otherwise now. MODS_INITIALIZED
        // follows at the same point whether or not there are foreign mods (ADR-0022).
        List<ForeignModResolver.ForeignMod> foreignInOrder = order.stream()
                .filter(mod -> mod.foreignMod() != null)
                .map(CrossLoaderLoadOrder.LoadedMod::foreignMod)
                .toList();
        String sidePhase =
                configuration.side() == dev.plexusmodloader.api.runtime.GameSide.CLIENT ? "client" : "server";
        Runnable modInitialization = () -> {
            if (!foreignInOrder.isEmpty()) {
                LOGGER.info("[Poly Mod Loader] Initialising {} foreign mod(s)", foreignInOrder.size());
                Runnable entrypoints = () -> {
                    foreignInOrder.forEach(mod -> invokeBridgedEntrypoints(mod, "common", gameClassLoader));
                    foreignInOrder.forEach(mod -> invokeBridgedEntrypoints(mod, sidePhase, gameClassLoader));
                };
                // A host loader (Forge) sets up what those mods expect from their own loader, such as open registries.
                host.ifPresentOrElse(found -> found.runForeignModInitialization(entrypoints), entrypoints);
            }
            events.publish(LifecycleEvents.MODS_INITIALIZED, ModsInitializedListener::onModsInitialized);
        };
        if (adapter.bindModInitialization(configuration.target(), modInitialization)) {
            if (!foreignInOrder.isEmpty()) {
                LOGGER.info(
                        "[Poly Mod Loader] {} foreign mod(s) are initialised when Minecraft reaches its mod initialization"
                                + " point, as under their loader",
                        foreignInOrder.size());
            }
        } else {
            modInitialization.run();
        }
        LOGGER.info("[Poly Mod Loader] Native declarations: {}", registrations.summary());
        if (adapter.gameLayerClass().isPresent()) {
            List<ModResourcePack> packs = resolution.loadOrder().stream()
                    .map(mod -> new ModResourcePack(
                            mod.id().value(),
                            mod.name(),
                            mod.version().toString(),
                            modJars.get(mod.id().value())))
                    .toList();
            GameLayer layer = startGameLayer(
                    adapter.gameLayerClass().get(),
                    gameClassLoader,
                    new GameLayerContext(
                            configuration.side(),
                            configuration.target(),
                            events,
                            packs,
                            registrations,
                            modCounts,
                            startup));
            // A host that draws the title screen's branding itself shows the game layer's lines there too.
            host.ifPresent(found -> found.addBrandingLines(layer::brandingLines));
        } else {
            registrations.close();
        }

        // GAME_LAUNCH
        if (host.isPresent()) {
            LOGGER.info(
                    "[Poly Mod Loader] Phase {}: {} starts the game",
                    RuntimePhase.GAME_LAUNCH,
                    host.get().name());
            startup.startingGame(gameVersion);
            try {
                host.get().launch();
            } finally {
                startup.gameEnded();
            }
            return new LaunchedGame(gameVersion, gameClassLoader, shutdown, systems);
        }
        String mainClassName = adapter.mainClass(configuration.target());
        LOGGER.info("[Poly Mod Loader] Phase {}: {}", RuntimePhase.GAME_LAUNCH, mainClassName);
        Class<?> mainClass = Class.forName(mainClassName, false, gameClassLoader);
        MethodHandle main = MethodHandles.publicLookup()
                .findStatic(mainClass, "main", MethodType.methodType(void.class, String[].class));
        startup.startingGame(gameVersion);
        try {
            main.invokeExact(gameArguments);
        } finally {
            startup.gameEnded();
        }
        return new LaunchedGame(gameVersion, gameClassLoader, shutdown, systems);
    }

    private static StartupReport startupReport(
            SemanticVersion runtimeVersion,
            String gameVersion,
            LaunchConfiguration configuration,
            GameVersionAdapter adapter,
            List<ModMetadata> loadOrder,
            Map<String, Path> modJars,
            List<StartupReport.BlockedFile> blockedFiles,
            List<StartupReport.ForeignMod> foreignMods,
            SystemRegistry systems,
            TransformPlan plan) {
        return new StartupReport(
                runtimeVersion.toString(),
                gameVersion,
                configuration.target().id(),
                adapter.adapterId(),
                loadOrder.stream()
                        .map(mod -> new StartupReport.Mod(
                                mod.id().value(),
                                mod.version().toString(),
                                mod.name(),
                                modJars.get(mod.id().value()).getFileName().toString()))
                        .toList(),
                blockedFiles,
                foreignMods,
                systems.activeSystems().stream()
                        .map(system -> new StartupReport.ActiveSystem(
                                system.systemId().value(),
                                system.providerClass(),
                                system.providerModId(),
                                system.overriddenModIds()))
                        .toList(),
                plan.rulesByClass().values().stream()
                        .flatMap(List::stream)
                        .map(rule -> new StartupReport.Transformation(
                                rule.ruleId(),
                                rule.ownerModId(),
                                rule.target().toString(),
                                rule.action().describe()))
                        .toList(),
                plan.rejected().stream().map(TransformProblem::toString).toList());
    }

    /**
     * Lets every bridge set up its loader's runtime API ({@code prepareLaunch}) and install its own class
     * transformer, before any game or mod class is defined. A failure stops the launch ({@code PLEXUS-COMPAT-003}).
     */
    private static void prepareBridges(
            ForeignModResolver foreignMods, BridgeLaunchContext context, GameClassLoader gameClassLoader) {
        for (dev.plexusmodloader.compat.bridge.ModLoaderBridge bridge : foreignMods.bridges()) {
            try {
                bridge.prepareLaunch(context);
                bridge.classTransformer(context, gameClassLoader).ifPresent(bridgeTransformer -> {
                    gameClassLoader.installBridgeTransformer(bridgeTransformer);
                    LOGGER.info(
                            "[Poly Mod Loader] {} bridge transforms classes with {}",
                            bridge.loaderName(),
                            bridgeTransformer.name());
                });
            } catch (RuntimeException | LinkageError e) {
                throw new PlexusLaunchException(
                        RuntimePhase.LIFECYCLE_INITIALIZATION.name(),
                        "PLEXUS-COMPAT-003 the " + bridge.loaderName() + " bridge failed to prepare the launch: " + e,
                        e);
            }
        }
    }

    /**
     * Every class stage is in place and the host is prepared; mod code that must run before the game (Fabric's
     * preLaunch) runs now. A failure stops the launch ({@code PLEXUS-COMPAT-110}).
     */
    private static void runBeforeGame(ForeignModResolver foreignMods, BridgeLaunchContext context) {
        for (dev.plexusmodloader.compat.bridge.ModLoaderBridge bridge : foreignMods.bridges()) {
            try {
                bridge.beforeGame(context);
            } catch (Exception | LinkageError e) {
                throw new PlexusLaunchException(
                        RuntimePhase.LIFECYCLE_INITIALIZATION.name(),
                        "PLEXUS-COMPAT-110 " + bridge.loaderName() + " code that runs before the game threw " + e,
                        e);
            }
        }
    }

    /**
     * The runtime packages of bridges that run no mods in this launch ({@code ModLoaderBridge.runtimePackages()}, such as
     * Fabric Loader's API on Poly Mod Loader's class path) are hidden from game and mod code, unless a bridge that does
     * run mods has them too (Quilt mods use Fabric Loader's API). Under the loader that runs (NeoForge, Forge, or none),
     * those classes are not there either, and multi-loader libraries tell the loader by them.
     */
    private static void hideIdleLoaderApis(
            ForeignModResolver foreignMods,
            List<ForeignModResolver.ForeignMod> bridgedMods,
            GameClassLoader gameClassLoader) {
        Set<String> running = new java.util.LinkedHashSet<>();
        Set<String> idle = new java.util.LinkedHashSet<>();
        for (dev.plexusmodloader.compat.bridge.ModLoaderBridge bridge : foreignMods.bridges()) {
            boolean runsMods = bridgedMods.stream().anyMatch(mod -> mod.bridge() == bridge);
            (runsMods ? running : idle).addAll(bridge.runtimePackages());
        }
        idle.removeAll(running);
        if (idle.isEmpty()) {
            return;
        }
        gameClassLoader.hideFromParent(List.copyOf(idle));
        LOGGER.info(
                "[Poly Mod Loader] Loader APIs of bridges without mods are not visible to game and mod code: {}", idle);
    }

    /**
     * The host for a loader whose own code loads its mods, when such mods were found ({@code -Dpoly.forge=always}
     * also asks without them, {@code off} never): MinecraftForge through FML (ADR-0021). With Fabric mods
     * in the same launch, one Mixin (Fabric's) serves both (G8); when Forge's files cannot be prepared the Forge mods are
     * left out ({@code PLEXUS-COMPAT-206}).
     */
    private static Optional<GameHost> gameHost(
            LaunchConfiguration configuration,
            String gameVersion,
            String[] gameArguments,
            List<DiscoveredModFile> files,
            List<Path> forgeJars,
            ForeignModResolver foreignMods,
            boolean polyEarlyWindow) {
        String mode = System.getProperty("poly.forge", "auto");
        if (mode.equals("off") || (forgeJars.isEmpty() && !mode.equals("always"))) {
            return Optional.empty();
        }
        long fabricMods = files.stream().filter(PlexusRuntime::onlyFabric).count();
        if (fabricMods > 0) {
            LOGGER.info(
                    "[Poly Mod Loader] {} Forge mod(s) and {} Fabric mod(s) run together; one Mixin serves both",
                    forgeJars.size(),
                    fabricMods);
        }
        GameHost.Request request = new GameHost.Request(
                configuration.side(),
                configuration.target().id(),
                gameVersion,
                configuration.gameJar(),
                Path.of("").toAbsolutePath(),
                configuration.reportDirectory(),
                List.of(gameArguments),
                line -> LOGGER.info("[Poly Mod Loader] {}", line),
                fabricMods > 0,
                configuration.systemClassPath(),
                Optional.empty(),
                polyEarlyWindow);
        for (dev.plexusmodloader.compat.bridge.ModLoaderBridge bridge : foreignMods.bridges()) {
            if (!bridge.metadataPath().equals(ModFileKind.FORGE.metadataPath())) {
                continue;
            }
            try {
                Optional<GameHost> host = bridge.gameHost(request, forgeJars);
                host.ifPresent(found -> LOGGER.info(
                        "[Poly Mod Loader] {} loads {} Forge mod(s) itself on Poly Mod Loader's game class loader",
                        found.name(),
                        forgeJars.size()));
                return host;
            } catch (java.io.IOException | RuntimeException e) {
                LOGGER.warn(
                        "[Poly Mod Loader] PLEXUS-COMPAT-206 MinecraftForge's files could not be prepared ({}); the {} Forge"
                                + " mod(s) are NOT loaded.",
                        e.toString(),
                        forgeJars.size());
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /**
     * The host for NeoForge mods when there are no Forge mods: NeoForge through FML 11 (ADR-0026). It runs by default;
     * with {@code -Dpoly.neoforge=off} NeoForge mods are only diagnosed and left out ({@code PLEXUS-COMPAT-200..205}). When
     * NeoForge cannot run (no NeoForge release for this Minecraft version, files that cannot be prepared, no Java agent,
     * or, with Fabric or Quilt mods in the same launch, an FML whose Mixin start is not the checked code), the NeoForge
     * mods are left out too ({@code PLEXUS-COMPAT-216}). With Fabric or Quilt mods, one Mixin (Fabric's) serves them all
     * (ADR-0026 30).
     */
    private static Optional<GameHost> neoForgeHost(
            LaunchConfiguration configuration,
            String gameVersion,
            String[] gameArguments,
            List<DiscoveredModFile> files,
            List<Path> neoForgeJars,
            ForeignModResolver foreignMods,
            boolean polyEarlyWindow) {
        if (System.getProperty("poly.neoforge", "on").equals("off")
                || neoForgeJars.stream()
                        .noneMatch(jar -> files.stream()
                                .anyMatch(file -> file.path().equals(jar) && file.kind() == ModFileKind.NEOFORGE))) {
            return Optional.empty();
        }
        long fabricMods = fabricModsBesides(files, neoForgeJars);
        GameHost.Request request = new GameHost.Request(
                configuration.side(),
                configuration.target().id(),
                gameVersion,
                configuration.gameJar(),
                Path.of("").toAbsolutePath(),
                configuration.reportDirectory(),
                List.of(gameArguments),
                line -> LOGGER.info("[Poly Mod Loader] {}", line),
                fabricMods > 0,
                configuration.systemClassPath(),
                configuration.jdkModuleAccess(),
                polyEarlyWindow);
        for (dev.plexusmodloader.compat.bridge.ModLoaderBridge bridge : foreignMods.bridges()) {
            if (!bridge.metadataPath().equals(ModFileKind.NEOFORGE.metadataPath())) {
                continue;
            }
            try {
                Optional<GameHost> host = bridge.gameHost(request, neoForgeJars);
                host.ifPresent(found -> LOGGER.info(
                        "[Poly Mod Loader] {} loads {} NeoForge mod file(s) itself on Poly Mod Loader's game class"
                                + " loader",
                        found.name(),
                        neoForgeJars.size()));
                if (host.isPresent() && fabricMods > 0) {
                    LOGGER.info(
                            "[Poly Mod Loader] {} NeoForge mod file(s) and {} Fabric or Quilt mod(s) run together; one"
                                    + " Mixin serves both (ADR-0026 30)",
                            neoForgeJars.size(),
                            fabricMods);
                }
                return host;
            } catch (java.io.IOException | RuntimeException e) {
                LOGGER.warn(
                        "[Poly Mod Loader] PLEXUS-COMPAT-216 NeoForge cannot run here ({}); the {} NeoForge mod file(s)"
                                + " are NOT loaded.",
                        e.toString(),
                        neoForgeJars.size());
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /**
     * The jars NeoForge's FML runs: those whose loader is NeoForge, FML libraries ({@code FMLModType}), and, when there
     * is a NeoForge mod, every jar built for several loaders that has NeoForge's metadata too (such as Collective, Iris
     * and Zoomify with {@code fabric.mod.json} besides it): NeoForge reads only its own metadata, as it does without Poly
     * Mod Loader. Without NeoForge running, Fabric runs such a jar.
     */
    static List<Path> neoForgeJars(List<DiscoveredModFile> files) {
        boolean neoForgeMods = files.stream().anyMatch(file -> file.kind() == ModFileKind.NEOFORGE);
        return files.stream()
                .filter(file -> file.kind() == ModFileKind.NEOFORGE
                        || file.kind() == ModFileKind.FML_LIBRARY
                        || (neoForgeMods && file.isFor(ModFileKind.NEOFORGE) && file.kind() != ModFileKind.POLY_NATIVE))
                .map(DiscoveredModFile::path)
                .toList();
    }

    /** How many of {@code files} are Fabric mods no other loader could run instead, leaving out {@code hostJars}. */
    static long fabricModsBesides(List<DiscoveredModFile> files, List<Path> hostJars) {
        return files.stream()
                .filter(file -> onlyFabric(file) && !hostJars.contains(file.path()))
                .count();
    }

    /**
     * The jars MinecraftForge's FML runs: those with only Forge's {@code META-INF/mods.toml} among the loaders' metadata
     * (Fabric's and NeoForge's come first in a jar's kind), and, when there is one, every jar built for several loaders
     * that has Forge's metadata too. When Forge runs, it runs such a jar (with Fabric mods in the same game too, ADR-0021
     * G8): Forge and Fabric Loader each read only their own metadata, and a jar's Fabric side may be only a stub that
     * stops Fabric Loader (JourneyMap's for Forge). With only Fabric mods besides it, Fabric runs it.
     */
    static List<Path> forgeJars(List<DiscoveredModFile> files) {
        List<Path> onlyForge = files.stream()
                .filter(file -> file.kind() == ModFileKind.FORGE)
                .map(DiscoveredModFile::path)
                .toList();
        if (onlyForge.isEmpty()) {
            return onlyForge;
        }
        List<Path> jars = new ArrayList<>();
        for (DiscoveredModFile file : files) {
            if (file.kind() == ModFileKind.FORGE) {
                jars.add(file.path());
            } else if (file.isFor(ModFileKind.FORGE) && file.kind() != ModFileKind.POLY_NATIVE) {
                LOGGER.info(
                        "[Poly Mod Loader] {} is built for several loaders ({} and Forge); MinecraftForge runs it, as it"
                                + " runs the installed Forge mods",
                        file.path().getFileName(),
                        file.kind());
                jars.add(file.path());
            }
        }
        return List.copyOf(jars);
    }

    /**
     * {@code mods} without those the host also loads: the same mod built for both loaders (such as Forge Config API
     * Port for Fabric and for Forge, or Cloth Config as Fabric's {@code cloth-config} and NeoForge's {@code
     * cloth_config}), recognised by {@link HostBuildMatcher} (ADR-0026 37). One class loader cannot hold both builds,
     * which share packages; the host's build runs, the other is left out ({@code PLEXUS-COMPAT-139}). When the two builds
     * are mostly the same code ({@link HostBuildMatcher.Match#sameCode()}), the host's build stands in for the left-out
     * one: its IDs go to {@code standIns} with the host build's version, which meets the dependencies on them, and to
     * {@code aliases} under the host mod, which other loaders' mod lists show as provided IDs. Otherwise the mods that
     * need the left-out build are left out by the dependency check, as before.
     */
    private static List<ForeignModResolver.ForeignMod> withoutHostMods(
            GameHost host,
            List<ForeignModResolver.ForeignMod> mods,
            List<StartupReport.BlockedFile> blockedFiles,
            Map<String, String> standIns,
            Map<String, Set<String>> aliases) {
        HostBuildMatcher matcher = new HostBuildMatcher(host.mods());
        Set<String> hostIds = host.modIds();
        for (BridgeLaunchContext.LoadedMod hostMod : host.mods()) {
            standIns.putIfAbsent(hostMod.modId(), hostMod.version());
            for (String provided : hostMod.providedIds()) {
                standIns.putIfAbsent(provided, hostMod.version());
                aliases.computeIfAbsent(hostMod.modId(), key -> new LinkedHashSet<>())
                        .add(provided);
            }
            String hyphenated = hostMod.modId().replace('_', '-');
            if (!hyphenated.equals(hostMod.modId())) {
                standIns.putIfAbsent(hyphenated, hostMod.version());
                aliases.computeIfAbsent(hostMod.modId(), key -> new LinkedHashSet<>())
                        .add(hyphenated);
            }
        }
        List<ForeignModResolver.ForeignMod> kept = new ArrayList<>();
        for (ForeignModResolver.ForeignMod mod : mods) {
            String modId = mod.assessment().modId();
            Optional<HostBuildMatcher.Match> match;
            try {
                match = matcher.find(modId, mod.assessment().providedIds(), mod.jar());
            } catch (UncheckedIOException e) {
                // Without the classes, only the IDs can tell; a mod with a host mod's ID is still left out below.
                LOGGER.warn(
                        "[Poly Mod Loader] {} mod {} is compared with {}'s mods by ID only: {}",
                        mod.bridge().loaderName(),
                        modId,
                        host.name(),
                        e.getMessage());
                match = Optional.empty();
            }
            if (match.isEmpty() && !hostIds.contains(modId)) {
                kept.add(mod);
                continue;
            }
            String file = mod.containerJar() == null
                    ? mod.jar().getFileName().toString()
                    : mod.containerJar().getFileName() + "!/" + mod.nestedEntry();
            blockedFiles.add(
                    new StartupReport.BlockedFile(file, mod.bridge().loaderName(), "Blocked", "PLEXUS-COMPAT-139"));
            if (match.isEmpty() || match.get().evidence() == HostBuildMatcher.Evidence.SAME_ID) {
                LOGGER.warn(
                        "[Poly Mod Loader] PLEXUS-COMPAT-139 {} mod {} ({}) is NOT loaded: {} loads its own build of {},"
                                + " and the two builds cannot run in one game",
                        mod.bridge().loaderName(),
                        modId,
                        file,
                        host.name(),
                        modId);
            } else {
                LOGGER.warn(
                        "[Poly Mod Loader] PLEXUS-COMPAT-139 {} mod {} ({}) is NOT loaded: {} loads its own build of it"
                                + " as {}, and the two builds cannot run in one game",
                        mod.bridge().loaderName(),
                        modId,
                        file,
                        host.name(),
                        match.get().describe());
            }
            match.filter(found -> !found.sameCode())
                    .ifPresent(found -> LOGGER.info(
                            "[Poly Mod Loader]   mods that need {} are left out by the dependency check: {}'s build {} has only"
                                    + " {} of its {} classes",
                            modId,
                            host.name(),
                            found.hostMod().modId(),
                            found.sharedClasses(),
                            found.classes()));
            match.filter(HostBuildMatcher.Match::sameCode).ifPresent(found -> {
                Map<String, String> ids = found.standIns(modId, mod.assessment().providedIds());
                ids.forEach(standIns::putIfAbsent);
                aliases.computeIfAbsent(found.hostMod().modId(), key -> new LinkedHashSet<>())
                        .addAll(ids.keySet());
                LOGGER.info(
                        "[Poly Mod Loader]   mods that need {} use {}'s build {} {}",
                        String.join(" or ", new TreeSet<>(ids.keySet())),
                        host.name(),
                        found.hostMod().modId(),
                        found.hostMod().version());
            });
        }
        return kept;
    }

    /** {@code mod} with the IDs of the left-out builds it stands in for among its provided IDs (ADR-0026 37). */
    private static BridgeLaunchContext.LoadedMod withAliases(
            BridgeLaunchContext.LoadedMod mod, Map<String, Set<String>> aliases) {
        Set<String> ids = aliases.get(mod.modId());
        if (ids == null) {
            return mod;
        }
        Set<String> provided = new LinkedHashSet<>(mod.providedIds());
        ids.stream().filter(id -> !id.equals(mod.modId())).forEach(provided::add);
        return new BridgeLaunchContext.LoadedMod(
                mod.loader(),
                mod.modId(),
                mod.version(),
                mod.name(),
                List.copyOf(provided),
                mod.jar(),
                mod.containerJar(),
                mod.nestedEntry(),
                mod.runByHost());
    }

    /**
     * Extracts {@code entryName} from {@code containerJar} into {@code destinationDir} if not already extracted,
     * so it can be verified as a link source jar (ADR-0026 37).
     */
    private static Path extractNestedHostJar(Path destinationDir, Path containerJar, String entryName) {
        try {
            String cleanEntry = entryName.startsWith("/") ? entryName.substring(1) : entryName;
            Path target = destinationDir.resolve(cleanEntry);
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (ZipFile zip = new ZipFile(containerJar.toFile())) {
                ZipEntry entry = zip.getEntry(entryName);
                if (entry == null) {
                    LOGGER.warn("[Poly Mod Loader] Entry {} not found in host jar {}", entryName, containerJar);
                    return null;
                }
                if (!Files.exists(target) || (entry.getSize() >= 0 && Files.size(target) != entry.getSize())) {
                    try (InputStream in = zip.getInputStream(entry)) {
                        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
                return target;
            }
        } catch (IOException e) {
            LOGGER.warn(
                    "[Poly Mod Loader] Could not extract nested host jar {} from {}: {}",
                    entryName,
                    containerJar,
                    e.getMessage());
            return null;
        }
    }

    /**
     * A Fabric-family mod (Fabric or Quilt, which share Fabric's Mixin and entrypoint machinery, ADR-0027) no other
     * loader could run instead.
     */
    static boolean onlyFabric(DiscoveredModFile file) {
        return (file.kind() == ModFileKind.FABRIC || file.kind() == ModFileKind.QUILT)
                && !file.isFor(ModFileKind.FORGE);
    }

    /**
     * With MinecraftForge mods in the launch, NeoForge does not run (ADR-0026 34): Forge and NeoForge each patch the
     * game's classes their own way, at the same places in hundreds of methods, and one game has one copy of each class.
     * The NeoForge mods are named once with that reason ({@code PLEXUS-COMPAT-221}); each is then diagnosed as without
     * NeoForge ({@code 200..205}), so that mods that need them hear that they are installed but not loaded.
     */
    private static void reportNeoForgeBesideForge(List<DiscoveredModFile> files) {
        List<String> neoForgeFiles = files.stream()
                .filter(file -> file.kind() == ModFileKind.NEOFORGE)
                .map(file -> file.path().getFileName().toString())
                .toList();
        if (neoForgeFiles.isEmpty()) {
            return;
        }
        LOGGER.warn(
                "[Poly Mod Loader] PLEXUS-COMPAT-221 {} NeoForge mod file(s) are NOT loaded: MinecraftForge mods are in the"
                        + " same game, and MinecraftForge and NeoForge each patch the game's classes their own way, so"
                        + " only one of them can run (Forge runs). Remove the Forge mods or the NeoForge mods: {}",
                neoForgeFiles.size(),
                neoForgeFiles);
    }

    /** {@code context} with the host's shared Mixin configurations as the host reports them after it started. */
    private static BridgeLaunchContext withHostMixinConfigs(BridgeLaunchContext context, GameHost host) {
        List<dev.plexusmodloader.compat.bridge.SharedMixinConfig> configs = host.sharedMixinConfigs();
        if (configs.equals(context.sharedMixinConfigs())) {
            return context;
        }
        return new BridgeLaunchContext(
                context.side(),
                context.gameVersion(),
                context.gameJar(),
                context.gameDirectory(),
                context.launchArguments(),
                context.gameClassLoader(),
                context.mods(),
                context.gameInstance(),
                context.host().withSharedMixinConfigs(configs));
    }

    /**
     * Tells the host the mods Poly Mod Loader runs ({@code otherMods}, so that its mod list answers for them, ADR-0025),
     * then runs the host's own setup before any game class is defined; a failure stops the launch ({@code 208}).
     */
    private static void prepareHost(
            GameHost host, List<BridgeLaunchContext.LoadedMod> otherMods, GameClassLoader gameClassLoader) {
        try {
            host.otherMods(otherMods);
            host.prepare(gameClassLoader, gameClassLoader);
        } catch (Exception | LinkageError e) {
            throw new PlexusLaunchException(
                    RuntimePhase.LIFECYCLE_INITIALIZATION.name(),
                    "PLEXUS-COMPAT-208 " + host.name() + " could not start its loader: " + e,
                    e);
        }
        host.classTransformer().ifPresent(transformer -> {
            gameClassLoader.installBridgeTransformer(transformer);
            LOGGER.info("[Poly Mod Loader] {} transforms classes with {}", host.name(), transformer.name());
        });
    }

    /** What the bridges learn about {@code host}, which patched {@code vanillaGameJar}. */
    private static HostedLaunch hostedLaunch(GameHost host, Path vanillaGameJar) {
        return new HostedLaunch(
                host.name(),
                host.sharedMixinConfigs(),
                host.injectionPointReplacements(),
                vanillaGameJar,
                host.sharesMixin());
    }

    private static BridgeLaunchContext.LoadedMod loadedMod(
            CrossLoaderLoadOrder.LoadedMod mod, Map<String, Path> modJars) {
        if (mod.nativeMod() != null) {
            return new BridgeLaunchContext.LoadedMod(
                    "Poly Mod Loader",
                    mod.nativeMod().id().value(),
                    mod.nativeMod().version().toString(),
                    mod.nativeMod().name(),
                    List.of(),
                    modJars.get(mod.nativeMod().id().value()),
                    null,
                    null);
        }
        BridgeAssessment assessment = mod.foreignMod().assessment();
        return new BridgeLaunchContext.LoadedMod(
                assessment.loader(),
                assessment.modId(),
                assessment.version(),
                assessment.modId(),
                assessment.providedIds(),
                mod.foreignMod().jar(),
                mod.foreignMod().containerJar(),
                mod.foreignMod().nestedEntry());
    }

    /** The entrypoints of one bridged mod for {@code phase}, in declaration order. */
    private static void invokeBridgedEntrypoints(
            ForeignModResolver.ForeignMod mod, String phase, ClassLoader gameClassLoader) {
        for (BridgedEntrypoint entrypoint : mod.assessment().entrypoints()) {
            if (!entrypoint.phase().equals(phase)) {
                continue;
            }
            try {
                mod.bridge().invokeEntrypoint(entrypoint, gameClassLoader);
            } catch (Exception | LinkageError e) {
                throw new PlexusLaunchException(
                        RuntimePhase.LIFECYCLE_INITIALIZATION.name(),
                        "PLEXUS-COMPAT-110 " + mod.assessment().loader() + " mod="
                                + mod.assessment().modId() + " entrypoint=" + entrypoint.className() + " threw " + e,
                        e);
            }
        }
    }

    /** The jar that holds {@code className}, found on Poly Mod Loader's own class path (ADR-0010). */
    private static Path locateGameLayerJar(String className) {
        if (!className.startsWith(GameClassLoader.GAME_LAYER_PREFIX)) {
            throw new PlexusLaunchException(
                    RuntimePhase.BOOTSTRAP.name(),
                    List.of("PLEXUS-BOOTSTRAP-004 game layer " + className + " must be in package "
                            + GameClassLoader.GAME_LAYER_PREFIX + "*"));
        }
        String resource = className.replace('.', '/') + ".class";
        java.net.URL url = PlexusRuntime.class.getClassLoader().getResource(resource);
        if (url == null) {
            throw new PlexusLaunchException(
                    RuntimePhase.BOOTSTRAP.name(),
                    List.of("PLEXUS-BOOTSTRAP-004 game layer " + className
                            + " is not on Poly Mod Loader's class path"));
        }
        try {
            if (url.openConnection() instanceof java.net.JarURLConnection connection) {
                return Path.of(connection.getJarFileURL().toURI());
            }
        } catch (IOException | java.net.URISyntaxException e) {
            throw new PlexusLaunchException(
                    RuntimePhase.BOOTSTRAP.name(), "PLEXUS-BOOTSTRAP-004 cannot locate the game layer jar: " + e, e);
        }
        throw new PlexusLaunchException(
                RuntimePhase.BOOTSTRAP.name(),
                List.of("PLEXUS-BOOTSTRAP-004 game layer " + className + " must be packaged in a jar, found " + url));
    }

    private static GameLayer startGameLayer(String className, ClassLoader gameClassLoader, GameLayerContext context) {
        try {
            Class<?> type = Class.forName(className, true, gameClassLoader);
            GameLayer layer = (GameLayer) type.getConstructor().newInstance();
            layer.start(context);
            LOGGER.info("[Poly Mod Loader] Game layer {} started", className);
            return layer;
        } catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
            throw new PlexusLaunchException(
                    RuntimePhase.LIFECYCLE_INITIALIZATION.name(),
                    "PLEXUS-BOOTSTRAP-005 game layer " + className + " failed to start: " + e,
                    e);
        }
    }

    private static void failIfAny(RuntimePhase phase, List<String> problems) {
        if (!problems.isEmpty()) {
            throw new PlexusLaunchException(phase.name(), problems);
        }
    }

    static SemanticVersion runtimeVersion() {
        Properties properties = new Properties();
        try (InputStream input = PlexusRuntime.class.getResourceAsStream("poly-runtime.properties")) {
            if (input == null) {
                throw new IllegalStateException(
                        "poly-runtime.properties is missing from the Poly Mod Loader runtime jar");
            }
            properties.load(input);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String version = properties.getProperty("version");
        return SemanticVersion.parse(version)
                .orElseThrow(
                        () -> new IllegalStateException("Poly Mod Loader runtime version is not SemVer: " + version));
    }

    /** Reads the {@code id} from the {@code version.json} Mojang ships at the root of the game jar. */
    static String readGameVersion(Path gameJar) {
        try (JarFile jar = new JarFile(gameJar.toFile())) {
            ZipEntry entry = jar.getEntry("version.json");
            if (entry == null) {
                throw new PlexusLaunchException(
                        RuntimePhase.BOOTSTRAP.name(),
                        List.of("PLEXUS-BOOTSTRAP-001 " + gameJar
                                + " has no version.json; is it a Minecraft game jar?"));
            }
            try (InputStream input = jar.getInputStream(entry)) {
                JsonObject version = JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8))
                        .getAsJsonObject();
                return version.get("id").getAsString();
            }
        } catch (IOException e) {
            throw new PlexusLaunchException(
                    RuntimePhase.BOOTSTRAP.name(), "PLEXUS-BOOTSTRAP-001 cannot read game jar " + gameJar, e);
        }
    }

    private static GameVersionAdapter selectAdapter(String gameVersion) {
        List<String> available = new ArrayList<>();
        for (GameVersionAdapter adapter :
                ServiceLoader.load(GameVersionAdapter.class, PlexusRuntime.class.getClassLoader())) {
            if (adapter.gameVersions().contains(gameVersion)) {
                return adapter;
            }
            available.addAll(adapter.gameVersions());
        }
        throw new PlexusLaunchException(
                RuntimePhase.BOOTSTRAP.name(),
                List.of("PLEXUS-BOOTSTRAP-002 no version adapter for Minecraft " + gameVersion
                        + "; this Poly Mod Loader build supports " + available));
    }

    private static Optional<byte[]> readClass(JarFile gameJar, List<JarFile> hostJars, String internalName) {
        Optional<byte[]> found = readClass(gameJar, internalName);
        for (int i = 0; found.isEmpty() && i < hostJars.size(); i++) {
            found = readClass(hostJars.get(i), internalName);
        }
        return found;
    }

    private static Optional<byte[]> readClass(JarFile jar, String internalName) {
        ZipEntry entry = jar.getEntry(internalName + ".class");
        if (entry == null) {
            return Optional.empty();
        }
        try (InputStream input = jar.getInputStream(entry)) {
            return Optional.of(input.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + internalName + " from the game jar", e);
        }
    }
}
