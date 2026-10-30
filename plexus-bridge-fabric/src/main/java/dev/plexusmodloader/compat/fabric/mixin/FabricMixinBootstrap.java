package dev.plexusmodloader.compat.fabric.mixin;

import com.llamalad7.mixinextras.MixinExtrasBootstrap;
import dev.plexusmodloader.api.runtime.GameSide;
import dev.plexusmodloader.compat.bridge.BridgeClassTransformer;
import dev.plexusmodloader.compat.bridge.BridgeLaunchContext;
import dev.plexusmodloader.compat.bridge.GameClassBytes;
import dev.plexusmodloader.compat.bridge.HostedLaunch;
import dev.plexusmodloader.compat.bridge.SharedMixinConfig;
import dev.plexusmodloader.compat.fabric.loader.FabricFamilyMod;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.FabricUtil;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.extensibility.IMixinConfig;
import org.spongepowered.asm.mixin.transformer.Config;

/**
 * Starts Mixin for the loaded Fabric mods in the order Fabric Loader 0.19.5 does ({@code FabricMixinBootstrap} and
 * {@code FabricLauncherBase.finishMixinBootstrapping}, ADR-0020):
 *
 * <ol>
 *   <li>names Poly Mod Loader's service ({@code mixin.service}, {@code mixin.bootstrapService}) and runs {@link
 *       MixinBootstrap#init()}
 *   <li>starts MixinExtras, as Fabric Loader's bundled MixinExtras mod does from its configuration plugin
 *   <li>adds every loaded Fabric and Quilt mod's configurations for this side, in load order ({@link
 *       #configurations}), and marks each with the mod's ID and Mixin compatibility level ({@link
 *       FabricUtil#KEY_MOD_ID}, {@link FabricUtil#KEY_COMPATIBILITY}, {@link #compatibilityLevel}); Quilt mods share
 *       this Mixin, as they share Quilt Loader's with Fabric mods (ADR-0027)
 *   <li>moves Mixin to its {@code INIT} and then {@code DEFAULT} phase, which selects and prepares the
 *       configurations (and loads their plugins)
 * </ol>
 *
 * <p>Nothing starts when no loaded Fabric mod has a configuration for this side and no other loader's mods share this
 * Mixin, so Mixin's process-wide state is only created when it is used.
 *
 * <p>When Forge mods run in the same launch, this Mixin serves them too (ADR-0021 G8): a class loader can hold only one
 * Mixin. Their configurations ({@link BridgeLaunchContext#sharedMixinConfigs()}) are added by {@link #addShared} once
 * Forge has defined its mods, as Mixin's ModLauncher service would add them under Forge; Mixin selects them on the next
 * class it transforms. NeoForge mods share it in the same way (ADR-0026 30): NeoForge's FML does not start its own
 * Mixin then, and reports its mods' configurations once it has found them.
 */
public final class FabricMixinBootstrap {

    private static final Logger LOGGER = LoggerFactory.getLogger("Poly Mod Loader");

    /** Mixin's option for where field initialisers of mixins go ({@code MixinEnvironment.Option}). */
    private static final String INITIALISER_MODE = "mixin.initialiserInjectionMode";

    /** Whether {@link #start} started Mixin in this process. */
    private static volatile boolean started;

    private FabricMixinBootstrap() {}

    /**
     * Starts Mixin for {@code context} and returns the transformer the game class loader runs, or empty when no
     * loaded Fabric mod has a Mixin configuration for this side.
     *
     * @param fabricLoaderVersion the Fabric Loader version Poly Mod Loader stands in for
     * @param mixinExtrasVersion the MixinExtras version on Poly Mod Loader's class path, for the log
     * @throws IllegalStateException when two mods use the same configuration name, a configuration cannot be
     *     read, or Mixin fails to start; the message names the configuration and the mod. On a host's patched game also
     *     ({@code PLEXUS-COMPAT-225}) when a loaded mod's mixin cannot work as written there, which {@link
     *     #incompatibleOnHost} should have found before the launch
     */
    public static Optional<BridgeClassTransformer> start(
            BridgeLaunchContext context,
            GameClassBytes classes,
            String fabricLoaderVersion,
            String mixinExtrasVersion) {
        Map<String, FabricFamilyMod> configs = configurations(context);
        Map<String, String> configToMod = new LinkedHashMap<>();
        configs.forEach((config, mod) -> configToMod.put(config, mod.modId()));
        if (configToMod.isEmpty()
                && context.sharedMixinConfigs().isEmpty()
                && !context.host().sharesMixin()) {
            return Optional.empty();
        }
        Map<String, Path> modJars = new HashMap<>();
        for (BridgeLaunchContext.LoadedMod mod : context.mods()) {
            if (FabricFamilyMod.isFamily(mod.loader()) && !mod.runByHost()) {
                modJars.put(mod.modId(), mod.jar());
            }
        }
        HostPatchAdjustments adjustments =
                hostPatchAdjustments(context.host(), context.side(), configToMod, modJars, classes);
        if (adjustments.hasIncompatible()) {
            // The check before the launch read the host's jars; this one reads the game class loader. A mod that
            // passed there and fails here must not run with part of it left out (ADR-0030).
            List<String> lines = new ArrayList<>();
            for (HostPatchAdjustments.Finding finding : adjustments.findings()) {
                if (finding.incompatible()) {
                    lines.add(finding.modId() + " (" + simpleName(finding.mixin()) + "): " + finding.reason());
                }
            }
            throw new IllegalStateException("PLEXUS-COMPAT-225 Poly Mod Loader cannot run these Fabric mods' mixins"
                    + " as written on the game as " + context.host().hostName() + " patches it, and its check"
                    + " before the launch did not foresee it. This is a limitation of Poly Mod Loader, not a bug in"
                    + " these mods: report it to Poly Mod Loader, not to the mods' authors. Remove these mods to play: "
                    + String.join("; ", lines));
        }
        for (HostPatchAdjustments.Finding finding : adjustments.findings()) {
            LOGGER.info(
                    "[Poly Mod Loader] {} Fabric mod {} ({}, {}): {}",
                    finding.code(),
                    finding.modId(),
                    finding.config(),
                    simpleName(finding.mixin()),
                    finding.reason());
        }
        GameClassBytes mixinClasses = adjustments == HostPatchAdjustments.NONE
                ? classes
                : new GameClassBytes() {
                    @Override
                    public Optional<byte[]> classBytes(String className) {
                        return classes.classBytes(className).map(bytes -> adjustments.apply(className, bytes));
                    }

                    @Override
                    public boolean isDefined(String className) {
                        return classes.isDefined(className);
                    }
                };
        MixinLaunch launch =
                MixinLaunch.begin(mixinClasses, context.gameClassLoader(), context.side(), fabricLoaderVersion);
        System.setProperty("mixin.bootstrapService", PlexusMixinServiceBootstrap.class.getName());
        System.setProperty("mixin.service", PlexusMixinService.class.getName());
        if (context.host().isHosted() && System.getProperty(INITIALISER_MODE) == null) {
            // Mixin puts a mixin's field initialisers after the last call of a constructor of the target class or its
            // superclass in each target constructor ("default"), which is the delegate call in vanilla's constructors.
            // A host's patch may construct the target class inside its own constructor (NeoForge's Pack builds its
            // child packs there, inside a loop): "default" then puts them in that loop, where they do not run. "safe"
            // puts them right after the delegate call, the same place for every constructor with one such call.
            System.setProperty(INITIALISER_MODE, "safe");
            LOGGER.info(
                    "[Poly Mod Loader] Mixin puts mixins' field initialisers right after the delegate constructor call"
                            + " (initialiserInjectionMode safe): {} constructs some classes inside their own"
                            + " constructors",
                    context.host().hostName());
        }
        MixinBootstrap.init();
        if (context.host().isHosted()) {
            // What HostPatchAdjustments could not foresee on a host's patched game is reported as Poly Mod Loader's.
            HostedMixinErrors.handle(configToMod.keySet(), context.host().hostName());
            Mixins.registerErrorHandlerClass(HostedMixinErrors.class.getName());
        }
        MixinExtrasBootstrap.init();
        configToMod.forEach((config, modId) -> {
            try {
                Mixins.addConfiguration(config);
            } catch (RuntimeException | LinkageError e) {
                throw new IllegalStateException(
                        "PLEXUS-COMPAT-134 the Mixin configuration " + config + " of mod " + modId + " cannot be used: "
                                + e,
                        e);
            }
        });
        for (Config raw : Mixins.getConfigs()) {
            FabricFamilyMod mod = configs.get(raw.getName());
            if (mod == null) {
                continue;
            }
            IMixinConfig config = raw.getConfig();
            config.decorate(FabricUtil.KEY_MOD_ID, mod.modId());
            config.decorate(FabricUtil.KEY_COMPATIBILITY, compatibilityLevel(mod));
        }
        gotoPhase(MixinEnvironment.Phase.INIT);
        gotoPhase(MixinEnvironment.Phase.DEFAULT);
        started = true;
        if (launch.transformer == null) {
            throw new IllegalStateException(
                    "Mixin started but offered no class transformer to Poly Mod Loader's service");
        }
        if (context.host().sharesMixin() && context.sharedMixinConfigs().isEmpty()) {
            // The host reports its mods' configurations only once its loader has started (NeoForge). Mixin selects
            // these now, so that the host's configurations come in a batch of their own: a host mod's configuration
            // plugin may load game classes while Mixin selects its batch (Iris for NeoForge loads BlockGetter), and
            // Mixin
            // refuses that when a configuration of the same batch still has mixins for that class (Fabric API's).
            launch.transformer.couldTransformClass(
                    MixinEnvironment.getCurrentEnvironment(), FabricMixinBootstrap.class.getName());
        }
        String name =
                "Mixin " + MixinBootstrap.VERSION + " (" + mixinBuild() + ") with MixinExtras " + mixinExtrasVersion;
        long fabricMods = configs.values().stream()
                .filter(mod -> !mod.isQuilt())
                .distinct()
                .count();
        long quiltMods = configs.values().stream()
                .filter(FabricFamilyMod::isQuilt)
                .distinct()
                .count();
        LOGGER.info(
                "[Poly Mod Loader] Mixin: {} configuration(s) from {} Fabric mod(s){} for {}",
                configToMod.size(),
                fabricMods,
                quiltMods == 0 ? "" : " and " + quiltMods + " Quilt mod(s)",
                context.side());
        return Optional.of(new MixinClassTransformer(
                launch.transformer, name, context.host().isHosted()));
    }

    /**
     * Before the runtime decides which mods load, on a game a host loader patched (ADR-0030): the mods among {@code
     * mods} whose mixins cannot work as written on {@code patched}, the host's classes, each with why (one line per
     * mixin, {@link HostPatchAdjustments.Finding#incompatible()}). The runtime does not load them. Mods of other loaders
     * are ignored. Every copy is checked, as the dependency check may choose any of them: the copies of one mod ID (an
     * installed jar and the copies other mods nest, in different versions) are checked in turns, one copy of each ID
     * per turn, because one check holds one mod per ID and one mod per configuration name.
     *
     * @param mods the candidate mods, before dependency resolution
     * @return per mod jar, the reasons; empty without a host or when every mod's mixins work
     */
    public static Map<Path, List<String>> incompatibleOnHost(
            HostedLaunch host, GameSide side, List<BridgeLaunchContext.LoadedMod> mods, GameClassBytes patched) {
        if (!host.isHosted()) {
            return Map.of();
        }
        // Nested copies with the same content share one extracted jar, so a jar is checked once.
        List<FabricFamilyMod> remaining = new ArrayList<>();
        Set<Path> jars = new HashSet<>();
        for (BridgeLaunchContext.LoadedMod loaded : mods) {
            if (jars.add(loaded.jar())) {
                FabricFamilyMod.read(loaded).ifPresent(remaining::add);
            }
        }
        Map<Path, List<String>> reasons = new LinkedHashMap<>();
        while (!remaining.isEmpty()) {
            Map<String, String> configToMod = new LinkedHashMap<>();
            Map<String, Path> modJars = new HashMap<>();
            List<FabricFamilyMod> later = new ArrayList<>();
            for (FabricFamilyMod mod : remaining) {
                List<String> configs = mod.mixinConfigs().stream()
                        .filter(entry -> entry.appliesTo(side))
                        .map(MixinConfigEntry::config)
                        .toList();
                if (modJars.containsKey(mod.modId()) || configs.stream().anyMatch(configToMod::containsKey)) {
                    later.add(mod);
                    continue;
                }
                modJars.put(mod.modId(), mod.mod().jar());
                configs.forEach(config -> configToMod.put(config, mod.modId()));
            }
            HostPatchAdjustments adjustments = hostPatchAdjustments(host, side, configToMod, modJars, patched);
            for (HostPatchAdjustments.Finding finding : adjustments.findings()) {
                if (finding.incompatible()) {
                    reasons.computeIfAbsent(modJars.get(finding.modId()), jar -> new ArrayList<>())
                            .add(simpleName(finding.mixin()) + ": " + finding.reason());
                }
            }
            remaining = later;
        }
        return reasons;
    }

    private static String simpleName(String internalName) {
        return internalName.substring(internalName.lastIndexOf('/') + 1);
    }

    /**
     * The Mixin configurations of the loaded Fabric and Quilt mods for this side, in load order and then file order,
     * each with its mod: Fabric's {@code mixins} and Quilt's top-level {@code mixin} (ADR-0027 decision 9).
     *
     * @throws IllegalStateException ({@code PLEXUS-COMPAT-134}) when two mods use the same configuration name, which
     *     Fabric Loader refuses; Quilt Loader would prefix each name with its mod's ID instead
     */
    public static Map<String, FabricFamilyMod> configurations(BridgeLaunchContext context) {
        Map<String, FabricFamilyMod> configs = new LinkedHashMap<>();
        for (FabricFamilyMod mod : FabricFamilyMod.of(context)) {
            for (MixinConfigEntry entry : mod.mixinConfigs()) {
                if (!entry.appliesTo(context.side())) {
                    continue;
                }
                FabricFamilyMod previous = configs.putIfAbsent(entry.config(), mod);
                if (previous != null) {
                    throw new IllegalStateException("PLEXUS-COMPAT-134 the Mixin configuration " + entry.config()
                            + " is used by two mods, " + previous.modId() + " and " + mod.modId()
                            + "; Fabric Loader refuses this too. Remove one of them");
                }
            }
        }
        return configs;
    }

    /**
     * The Mixin compatibility level of {@code mod}'s configurations: for a Fabric mod from the lowest Fabric Loader
     * version it accepts, as Fabric Loader decides it; for a Quilt mod always {@link FabricUtil#COMPATIBILITY_0_10_0},
     * as Quilt Loader's {@code QuiltMixinBootstrap} sets it.
     */
    public static int compatibilityLevel(FabricFamilyMod mod) {
        return mod.isQuilt()
                ? FabricUtil.COMPATIBILITY_0_10_0
                : MixinCompatibility.of(mod.fabricModJson().orElseThrow());
    }

    /**
     * Adds the configurations of other loaders' mods ({@link BridgeLaunchContext#sharedMixinConfigs()}) to the Mixin
     * {@link #start} started, each marked with its mod's ID and the Mixin behaviour its loader gives it ({@link
     * SharedMixinConfig#compatibility()}: NeoForge's {@code behaviorVersion}), by default the newest ({@link
     * FabricUtil#COMPATIBILITY_LATEST}): Forge mods were built against upstream Mixin, not an older Fabric Mixin.
     *
     * @throws IllegalStateException when Mixin did not start, a configuration's name is already used, or it cannot be
     *     read; the message names the configuration and the mod
     */
    public static void addShared(BridgeLaunchContext context) {
        List<SharedMixinConfig> shared = context.sharedMixinConfigs();
        if (shared.isEmpty()) {
            return;
        }
        if (!started) {
            throw new IllegalStateException("PLEXUS-COMPAT-134 Mixin is not running, so the Mixin configurations of "
                    + shared.size() + " " + shared.getFirst().loaderName() + " mod configuration(s) cannot be applied");
        }
        Set<String> existing = new HashSet<>();
        for (Config raw : Mixins.getConfigs()) {
            existing.add(raw.getName());
        }
        Map<String, SharedMixinConfig> added = new LinkedHashMap<>();
        for (SharedMixinConfig config : shared) {
            if (!existing.add(config.config())) {
                throw new IllegalStateException("PLEXUS-COMPAT-134 the Mixin configuration " + config.config() + " of "
                        + config.loaderName() + " mod " + config.modId()
                        + " is already used by another mod; Remove one of them");
            }
            try {
                Mixins.addConfiguration(config.config());
            } catch (RuntimeException | LinkageError e) {
                throw new IllegalStateException(
                        "PLEXUS-COMPAT-134 the Mixin configuration " + config.config() + " of " + config.loaderName()
                                + " mod " + config.modId() + " cannot be used: " + e,
                        e);
            }
            added.put(config.config(), config);
        }
        for (Config raw : Mixins.getConfigs()) {
            SharedMixinConfig config = added.get(raw.getName());
            if (config != null) {
                raw.getConfig().decorate(FabricUtil.KEY_MOD_ID, config.modId());
                raw.getConfig()
                        .decorate(
                                FabricUtil.KEY_COMPATIBILITY,
                                config.compatibility().orElse(FabricUtil.COMPATIBILITY_LATEST));
            }
        }
        PlexusMixinService.hostSelection(true);
        java.util.Set<String> early;
        try {
            selectNow();
        } finally {
            early = PlexusMixinService.hostSelection(false);
        }
        // The host mods' mixins into classes that loaded before or while Mixin selected their configurations do not
        // apply; the rest of each configuration does (ADR-0026 43).
        java.util.Set<String> late = new java.util.TreeSet<>(early);
        late.addAll(PlexusMixinService.loadedDuringHostSelection());
        // Only classes Mixin asked about while it prepared the host's mixins: their targets.
        late.retainAll(PlexusMixinService.askedDuringHostSelection());
        for (String target : late) {
            LOGGER.warn(
                    "[Poly Mod Loader] PLEXUS-COMPAT-220 [limited] {} loaded {} Mixin selected the {} mods'"
                            + " configurations (another mod's Mixin plugin loaded it); their mixins into it do not"
                            + " apply, the rest of their configurations does",
                    target,
                    early.contains(target) ? "before" : "while",
                    shared.getFirst().loaderName());
        }
        LOGGER.info(
                "[Poly Mod Loader] Mixin: {} configuration(s) from {} {} mod(s) share Fabric's Mixin",
                added.size(),
                added.values().stream().map(SharedMixinConfig::modId).distinct().count(),
                shared.getFirst().loaderName());
    }

    /**
     * Selects the configurations added since Mixin last selected, now. Mixin 0.8.7 selects configurations added late
     * only while it has not yet applied a mixin to any class ({@code MixinProcessor.checkSelect}: unvisited
     * configurations and {@code transformedCount == 0}); once a Fabric mod's mixin changed a class (as on the client,
     * before NeoForge reports its mods' configurations), the host's would never apply. This runs Mixin's own {@code
     * select} (sponge-mixin 0.17.4, checked with {@code javap}).
     *
     * <p>It runs outside Mixin's re-entrance lock, as the host's first selection runs outside any class Mixin applies
     * mixins to. A host mod's configuration plugin may load game classes while Mixin selects or prepares its
     * configurations (Iris for NeoForge loads {@code BlockGetter} through its platform service); Mixin refuses to apply
     * mixins to a class that loads within its lock ({@code ReEntrantTransformerError}), and here Fabric mods' mixins,
     * selected before, do change such a class. Such a class gets the mixins selected so far; a host mod's mixin into it
     * that is prepared after it loaded is reported and skipped ({@code PLEXUS-COMPAT-220}), as under the host's own Mixin,
     * whose service does not notice the class and never applies that mixin either. While this selection runs, Mixin
     * counts at least one changed class, so that such a class does not start a second selection.
     *
     * @throws IllegalStateException ({@code PLEXUS-COMPAT-134}) when this Mixin build has no such members, or selecting
     *     fails
     */
    private static void selectNow() {
        try {
            Object transformer = MixinEnvironment.getCurrentEnvironment().getActiveTransformer();
            Field processorField = transformer.getClass().getDeclaredField("processor");
            processorField.setAccessible(true);
            Object processor = processorField.get(transformer);
            Field transformed = processor.getClass().getDeclaredField("transformedCount");
            transformed.setAccessible(true);
            Method select = processor.getClass().getDeclaredMethod("select", MixinEnvironment.class);
            select.setAccessible(true);
            synchronized (processor) {
                int count = transformed.getInt(processor);
                if (count == 0) {
                    transformed.setInt(processor, 1);
                }
                try {
                    select.invoke(processor, MixinEnvironment.getCurrentEnvironment());
                } finally {
                    if (count == 0) {
                        transformed.setInt(processor, transformed.getInt(processor) - 1);
                    }
                }
            }
        } catch (InvocationTargetException e) {
            throw new IllegalStateException(
                    "PLEXUS-COMPAT-134 Mixin failed while selecting other loaders' configurations: " + e.getCause(),
                    e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "PLEXUS-COMPAT-134 this Mixin build cannot select configurations added late: " + e, e);
        }
    }

    /** The adjustments of the Fabric mods' mixins for the host's patched game, or none without a host. */
    private static HostPatchAdjustments hostPatchAdjustments(
            HostedLaunch host,
            GameSide side,
            Map<String, String> configToMod,
            Map<String, Path> modJars,
            GameClassBytes classes) {
        if (!host.isHosted()) {
            return HostPatchAdjustments.NONE;
        }
        try (JarFile vanilla = new JarFile(host.vanillaGameJar().toFile())) {
            return HostPatchAdjustments.plan(
                    host,
                    side,
                    configToMod,
                    modJars,
                    name -> {
                        ZipEntry entry = vanilla.getEntry(name.replace('.', '/') + ".class");
                        if (entry == null) {
                            return Optional.empty();
                        }
                        try (InputStream input = vanilla.getInputStream(entry)) {
                            return Optional.of(input.readAllBytes());
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    },
                    classes);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read the vanilla game jar " + host.vanillaGameJar(), e);
        }
    }

    /** {@code MixinEnvironment.gotoPhase} is package-private; Fabric Loader calls it the same way. */
    private static void gotoPhase(MixinEnvironment.Phase phase) {
        try {
            Method method = MixinEnvironment.class.getDeclaredMethod("gotoPhase", MixinEnvironment.Phase.class);
            method.setAccessible(true);
            method.invoke(null, phase);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            throw new IllegalStateException(
                    "PLEXUS-COMPAT-134 Mixin failed while entering its " + phase + " phase: " + cause, cause);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("This Mixin build has no MixinEnvironment.gotoPhase(Phase)", e);
        }
    }

    private static String mixinBuild() {
        String version = MixinBootstrap.class.getPackage().getImplementationVersion();
        return version == null ? "FabricMC fork" : "FabricMC " + version;
    }
}
