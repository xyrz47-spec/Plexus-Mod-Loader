package dev.polymodloader.compat.forge.fml;

import dev.polymodloader.compat.bridge.SharedMixinConfig;
import dev.polymodloader.compat.forge.ForeignModIndex;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Consumer;
import java.util.jar.Manifest;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.classloading.JarContentsModule;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.fml.loading.mixin.MixinFacade;
import net.neoforged.fml.loading.moddiscovery.ModFile;
import net.neoforged.fml.loading.moddiscovery.ModFileInfo;
import net.neoforged.fml.loading.moddiscovery.ModFileParser;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.transformation.ClassProcessor;
import net.neoforged.neoforgespi.transformation.ClassProcessorIds;
import net.neoforged.neoforgespi.transformation.ProcessorName;
import org.apache.maven.artifact.versioning.ArtifactVersion;

/**
 * FML's Mixin setup for a launch where Fabric's Mixin serves NeoForge's mods too (ADR-0026 30): one class loader holds
 * one Mixin, so FML does not start its own ({@code NeoForgeSharedMixinPatch}), and this finds what FML would have given
 * its Mixin.
 *
 * <p>Thread: FML's startup thread.
 */
final class FmlSharedMixin {

    /** The class every MixinExtras jar has; Poly Mod Loader's class path has it when Fabric's Mixin is shared. */
    static final String MIXIN_EXTRAS = "com/llamalad7/mixinextras/MixinExtrasBootstrap.class";

    /** The class Forgified Fabric Loader has; Poly Mod Loader provides FabricLoader on its own class path. */
    static final String FABRIC_LOADER = "net/fabricmc/loader/api/FabricLoader.class";

    /** The loader name of the configurations, for logs ({@link SharedMixinConfig#loaderName()}). */
    static final String LOADER = "NeoForge";

    private FmlSharedMixin() {}

    /**
     * {@code content} without the MixinExtras game libraries (NeoForge's own {@code mixinextras-neoforge}, or a newer
     * copy FML selected from a mod's JarJar), when Poly Mod Loader's class path has MixinExtras, and without
     * {@code forgified-fabric-loader} which shadows Poly Mod Loader's {@code FabricLoader}.
     *
     * @param leftOut gets the names of the modules left out
     */
    static List<JarContentsModule> withoutMixinExtras(
            List<JarContentsModule> content, Consumer<String> leftOut, Consumer<String> log) {
        boolean hasMixinExtras = FmlSharedMixin.class.getClassLoader().getResource(MIXIN_EXTRAS) != null;
        boolean hasFabricLoader = FmlSharedMixin.class.getClassLoader().getResource(FABRIC_LOADER) != null;
        if (!hasMixinExtras && !hasFabricLoader) {
            return content;
        }
        List<JarContentsModule> kept = new ArrayList<>();
        for (JarContentsModule module : content) {
            if (hasMixinExtras && module.contents().containsFile(MIXIN_EXTRAS)) {
                leftOut.accept(module.moduleName());
                log.accept("FML's game library " + module.moduleName() + " ("
                        + module.contents().getPrimaryPath().getFileName()
                        + ") is left out of the game layer: NeoForge's mods use the MixinExtras of the Mixin that Fabric"
                        + " mods share (ADR-0026 30)");
            } else if (hasFabricLoader && module.contents().containsFile(FABRIC_LOADER)) {
                leftOut.accept(module.moduleName());
                log.accept("FML's game library " + module.moduleName() + " ("
                        + module.contents().getPrimaryPath().getFileName()
                        + ") is left out of the game layer: NeoForge's mods use Poly Mod Loader's Fabric Loader"
                        + " implementation");
            } else {
                kept.add(module);
            }
        }
        return List.copyOf(kept);
    }

    /**
     * In place of FML's Mixin class processor: one with FML's Mixin name that handles no class, so that processors
     * ordered around FML's Mixin keep their order. The shared Mixin applies in Poly Mod Loader's bridge stage.
     */
    static ClassProcessor placeholderProcessor() {
        return new ClassProcessor() {
            @Override
            public ProcessorName name() {
                return ClassProcessorIds.MIXIN;
            }

            @Override
            public boolean handlesClass(SelectionContext context) {
                return false;
            }

            @Override
            public ComputeFlags processClass(TransformationContext context) {
                return ComputeFlags.NO_REWRITE;
            }
        };
    }

    /**
     * FML 12's name of its processor for Mixin's synthetic classes ({@code ClassProcessorIds.MIXIN_FRAME_CONTEXT},
     * which FML 11, the version this host compiles against, does not have).
     */
    static final ProcessorName MIXIN_FRAME_CONTEXT = new ProcessorName("neoforge", "mixin_frame_context");

    /**
     * In place of FML 12's {@code FMLMixinGeneratingClassProcessor}: one with its name and order (after nothing, before
     * frame computation and FML's Mixin, late among equals) that handles no class and generates no package, so that
     * processors ordered around it keep their order. FML's processor would make Mixin's synthetic classes ({@code
     * org.spongepowered.asm.synthetic.args}) with FML's Mixin, which does not start; the shared Mixin's synthetic
     * classes come from Poly Mod Loader's bridge stage, as with FML 11, whose one Mixin processor this placeholder's
     * sibling replaces.
     */
    static ClassProcessor placeholderGeneratingProcessor() {
        return new ClassProcessor() {
            @Override
            public ProcessorName name() {
                return MIXIN_FRAME_CONTEXT;
            }

            /** As FML's: not {@code ClassProcessor}'s default (after frame computation), which would be a cycle. */
            @Override
            public Set<ProcessorName> runsAfter() {
                return Set.of();
            }

            @Override
            public Set<ProcessorName> runsBefore() {
                return Set.of(ClassProcessorIds.COMPUTING_FRAMES, ClassProcessorIds.MIXIN);
            }

            @Override
            public OrderingHint orderingHint() {
                return OrderingHint.LATE;
            }

            @Override
            public boolean handlesClass(SelectionContext context) {
                return false;
            }

            @Override
            public ComputeFlags processClass(TransformationContext context) {
                return ComputeFlags.NO_REWRITE;
            }
        };
    }

    /**
     * The Mixin configurations FML's {@code MixinFacade.addMixins} would add, found the same way: for each mod file in
     * FML's order, each {@code [[mixins]]} entry whose required mods are present and whose behaviour version FML accepts
     * (FML's checks, which add FML's loading issues; a required mod may be another loader's), unless another mod file declares the same configuration
     * ({@code fml.modloadingissue.mixin.duplicate_config}) or no jar has it ({@code
     * fml.modloadingissue.mixin.missing_config}); its behaviour version is FML's {@code fabric-compat} decoration. Then
     * the configurations the manifests of FML's plugin and game library jars name ({@code MixinConfigs}, which Mixin's
     * platform agent reads from the containers FML gives it), except those of the libraries left out of the game layer
     * ({@link #withoutMixinExtras}): their one configuration starts MixinExtras, which the shared Mixin already did.
     *
     * @throws IllegalStateException ({@code POLY-COMPAT-215}) when FML's own checks are not the methods Poly Mod Loader
     *     checked
     */
    static List<SharedMixinConfig> configurations(
            LoadingModList mods, ForeignModIndex otherMods, boolean mixinExtrasLeftOut, Consumer<String> log) {
        List<SharedMixinConfig> configs = new ArrayList<>();
        Map<String, IModFile> owners = new LinkedHashMap<>();
        try {
            Method behavior = facadeMethod("validateMixinBehavior", ModFile.class, ModFileParser.MixinConfig.class);
            Method version = facadeMethod("calculateBehaviorVersion", ArtifactVersion.class);
            for (ModFileInfo info : mods.getModFiles()) {
                ModFile file = info.getFile();
                for (ModFileParser.MixinConfig config : file.getMixinConfigs()) {
                    if (!requiredModsPresent(file, config, mods, otherMods, log)
                            || !(boolean) behavior.invoke(null, file, config)) {
                        continue;
                    }
                    int compatibility = (int) version.invoke(null, config.behaviorVersion());
                    IModFile owner = owners.putIfAbsent(config.config(), file);
                    if (owner != null && owner != file) {
                        ModLoader.addLoadingIssue(ModLoadingIssue.error(
                                        "fml.modloadingissue.mixin.duplicate_config", config.config(), owner)
                                .withAffectedModFile(file));
                        continue;
                    }
                    if (!hasConfig(file, config.config(), mods, log)) {
                        ModLoader.addLoadingIssue(
                                ModLoadingIssue.error("fml.modloadingissue.mixin.missing_config", config.config())
                                        .withAffectedModFile(file));
                        continue;
                    }
                    configs.add(new SharedMixinConfig(
                            LOADER, file.getId(), config.config(), OptionalInt.of(compatibility)));
                }
            }
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("FML's Mixin checks failed: " + e.getCause(), e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "POLY-COMPAT-215 FML's MixinFacade is not the code Poly Mod Loader checked: " + e, e);
        }
        List<IModFile> containers = new ArrayList<>();
        mods.getPlugins().stream().map(IModFileInfo::getFile).forEach(containers::add);
        containers.addAll(mods.getGameLibraries());
        Set<String> seen = new HashSet<>(owners.keySet());
        for (IModFile container : containers) {
            if (mixinExtrasLeftOut
                    && (container.getContents().containsFile(MIXIN_EXTRAS)
                            || container.getContents().containsFile(FABRIC_LOADER))) {
                continue;
            }
            for (String name : manifestConfigs(container.getContents().getManifest())) {
                if (seen.add(name)) {
                    configs.add(new SharedMixinConfig(LOADER, container.getId(), name));
                }
            }
        }
        return List.copyOf(configs);
    }

    /**
     * FML's {@code areRequiredModsPresent} with the mods of the other loaders in this game too (ADR-0025): each mod a
     * {@code [[mixins]]} entry's {@code requiredMods} names is one FML loads or one Poly Mod Loader runs beside it (Sodium
     * for NeoForge applies {@code sodium-frapi.mixins.json} when Fabric API's {@code fabric_renderer_api_v1} is there).
     */
    private static boolean requiredModsPresent(
            ModFile file,
            ModFileParser.MixinConfig config,
            LoadingModList mods,
            ForeignModIndex otherMods,
            Consumer<String> log) {
        for (String required : config.requiredMods()) {
            if (mods.getModFileById(required) == null
                    && otherMods.find(required).isEmpty()) {
                log.accept("Mixin config " + config.config() + " from " + file + " not applied as required mod '"
                        + required + "' is missing");
                return false;
            }
        }
        return true;
    }

    /** Whether {@code file} has {@code config}, or another mod file does (FML warns and uses that one). */
    private static boolean hasConfig(ModFile file, String config, LoadingModList mods, Consumer<String> log) {
        if (readable(file, config)) {
            return true;
        }
        for (IModFile other : mods.getAllModFiles()) {
            if (other != file && readable(other, config)) {
                log.accept("Mod file " + file + " declares mixin config " + config + ", but it actually comes from "
                        + other);
                return true;
            }
        }
        return false;
    }

    private static boolean readable(IModFile file, String name) {
        try {
            return file.getContents().readFile(name) != null;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * The names Mixin's platform agent registers from {@code manifest}'s {@code MixinConfigs}: each comma-separated name,
     * trimmed, that ends in {@code .json}.
     */
    static List<String> manifestConfigs(Manifest manifest) {
        String value = manifest == null ? null : manifest.getMainAttributes().getValue("MixinConfigs");
        if (value == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (String config : value.split(",")) {
            String name = config.trim();
            if (name.endsWith(".json")) {
                names.add(name);
            }
        }
        return List.copyOf(names);
    }

    private static Method facadeMethod(String name, Class<?>... parameters) throws NoSuchMethodException {
        Method method = MixinFacade.class.getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method;
    }
}
