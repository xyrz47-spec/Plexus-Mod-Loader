package dev.polymodloader.compat.forge.fml;

import dev.polymodloader.compat.bridge.BridgeClassTransformer;
import dev.polymodloader.compat.bridge.SharedMixinConfig;
import dev.polymodloader.compat.forge.NeoForgeLaunch;
import java.io.File;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.classloading.JarContentsModule;
import net.neoforged.fml.classloading.JarContentsModuleFinder;
import net.neoforged.fml.classloading.transformation.ClassProcessorAuditLog;
import net.neoforged.fml.classloading.transformation.ClassProcessorSet;
import net.neoforged.fml.classloading.transformation.TransformingClassLoader;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.fml.loading.mixin.MixinFacade;
import net.neoforged.fml.startup.StartupArgs;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import net.neoforged.neoforgespi.transformation.ClassProcessor;

/**
 * Poly Mod Loader's host for FML 11 (ADR-0026), a module of the layer of NeoForge's loader libraries. {@link #start}
 * runs FML's own {@code FMLLoader.create}; Poly Mod Loader's patch of {@code FMLLoader} ({@code NeoForgeLoaderPatch})
 * calls {@link #buildTransformingLoader} and {@link #modsLocator} from there.
 *
 * <p>Thread: FML starts on the launching thread; one launch per JVM.
 */
public final class PolyFml implements NeoForgeLaunch {

    private static volatile Request request;
    private static volatile ModuleLayer gameLayer;
    private static volatile PolyTransformingClassLoader classLoader;
    /** With a shared Mixin: what {@link #finishSharedMixin} found, for the bridge whose Mixin serves every loader. */
    private static volatile List<SharedMixinConfig> sharedMixinConfigs = List.of();
    /** With a shared Mixin: whether a MixinExtras game library was left out of the game layer. */
    private static volatile boolean mixinExtrasLeftOut;

    private final FMLLoader loader;
    private final Set<String> gamePackages;

    private PolyFml(FMLLoader loader, ModuleLayer gameLayer) {
        this.loader = loader;
        Set<String> packages = new HashSet<>();
        gameLayer.modules().forEach(module -> packages.addAll(module.getPackages()));
        this.gamePackages = Set.copyOf(packages);
    }

    /**
     * Runs FML's startup: discovery of the game, NeoForge and {@code request}'s mods, their sorting, language
     * providers, class processors, the game layer (through {@link #buildTransformingLoader}) and Mixin.
     *
     * @throws IllegalStateException when FML built its game layer without Poly Mod Loader's host (the patch did not
     *     apply)
     * @throws RuntimeException whatever FML throws, such as its {@code ModLoadingException} with the discovery issues
     */
    public static NeoForgeLaunch start(Request request) {
        PolyFml.request = request;
        System.setProperty("libraryDirectory", request.libraryDirectory().toString());
        Set<File> claimed = new HashSet<>();
        request.claimedFiles().forEach(path -> claimed.add(path.toFile()));
        StartupArgs arguments = new StartupArgs(
                request.gameDirectory(),
                request.headless(),
                Dist.valueOf(request.dist()),
                false,
                request.programArguments().toArray(String[]::new),
                claimed,
                earlyServiceCandidates(request.modJars(), request.gameDirectory()),
                request.gameClassLoader());
        Thread.currentThread().setContextClassLoader(request.gameClassLoader());
        ForeignModList.install(request.otherMods());
        FMLLoader loader = FMLLoader.create(null, arguments);
        ModuleLayer layer = gameLayer;
        if (layer == null) {
            throw new IllegalStateException(
                    "POLY-COMPAT-215 FML built its game layer without Poly Mod Loader's host; its own class loader"
                            + " would own the game");
        }
        request.log()
                .accept("FML " + FMLLoader.class.getPackage().getImplementationVersion() + " loaded "
                        + loader.getLoadingModList().getMods().size() + " mod(s); game layer of "
                        + layer.modules().size() + " module(s) in Poly Mod Loader's game class loader");
        return new PolyFml(loader, layer);
    }

    /**
     * The NeoForge mods outside the mods folder, for FML's early service discovery, which looks at the mods folder and
     * at the unclaimed class path entries only. NeoForge's own launcher has every mod in the mods folder; Poly Mod Loader
     * may run mods from elsewhere too (development runs), and FML must find their early services in the same way, so
     * that it loads such a jar as an early service and the mod nested in it from its JarJar (Sodium's NeoForge build).
     * Jars inside the mods folder FML finds itself.
     */
    static List<File> earlyServiceCandidates(List<Path> modJars, Path gameDirectory) {
        Path modsFolder = gameDirectory.resolve("mods").toAbsolutePath().normalize();
        return modJars.stream()
                .map(jar -> jar.toAbsolutePath().normalize())
                .filter(jar -> !modsFolder.equals(jar.getParent()))
                .map(Path::toFile)
                .toList();
    }

    /**
     * In place of FML's private {@code buildTransformingLoader}: the game class loader defines {@code content} (the game
     * layer FML computed) as a layer over NeoForge's loader libraries, and FML gets {@link PolyTransformingClassLoader},
     * which hands every class and resource to the game class loader and runs FML's class processors for it.
     *
     * <p>Sets what FML's own method sets: {@code FMLLoader}'s game layer and current class loader, the class loader
     * among the resources FML closes, and the thread's context class loader.
     */
    public static TransformingClassLoader buildTransformingLoader(
            FMLLoader loader,
            ClassProcessorSet processors,
            ClassProcessorAuditLog auditLog,
            List<JarContentsModule> content) {
        Request current = request;
        if (current == null) {
            throw new IllegalStateException("Poly Mod Loader's FML host was not started");
        }
        if (current.sharedMixin()) {
            content = FmlSharedMixin.withoutMixinExtras(content, module -> mixinExtrasLeftOut = true, current.log());
        }
        List<String> names = content.stream().map(JarContentsModule::moduleName).toList();
        Configuration configuration = Configuration.resolveAndBind(
                new JarContentsModuleFinder(content),
                List.of(current.libraryLayer().configuration()),
                ModuleFinder.of(),
                names);
        ModuleLayer.Controller controller =
                current.layers().define(configuration, List.of(current.libraryLayer()), true);
        current.openJdk().accept(controller.layer().modules());
        PolyTransformingClassLoader facade =
                new PolyTransformingClassLoader(processors, auditLog, current.gameClassLoader(), current.classes());
        try {
            // FML's own loader falls back to FML's class loader chain so far (the loaders of its early services and
            // plugin libraries, such as Kotlin for Forge's Kotlin standard library); the game class loader does too.
            Field currentLoader = FMLLoader.class.getDeclaredField("currentClassLoader");
            currentLoader.setAccessible(true);
            if (currentLoader.get(loader) instanceof ClassLoader chain && chain != current.gameClassLoader()) {
                current.layers().addFallback(chain);
            }
            set(loader, "gameLayer", controller.layer());
            set(loader, "currentClassLoader", facade);
            Field owned = FMLLoader.class.getDeclaredField("ownedResources");
            owned.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<AutoCloseable> resources = (List<AutoCloseable>) owned.get(loader);
            resources.add(facade);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "POLY-COMPAT-215 FMLLoader's fields are not the ones Poly Mod Loader checked: " + e, e);
        }
        gameLayer = controller.layer();
        classLoader = facade;
        Thread.currentThread().setContextClassLoader(facade);
        current.log()
                .accept("FML's game layer: " + names.size() + " module(s) defined by Poly Mod Loader's game class"
                        + " loader");
        return facade;
    }

    /**
     * In place of {@code MixinFacade.getClassProcessor()} when Fabric's Mixin is shared ({@code
     * NeoForgeSharedMixinPatch}, ADR-0026 30): a processor with FML's Mixin name that handles no class.
     */
    public static ClassProcessor mixinClassProcessor(MixinFacade facade) {
        return FmlSharedMixin.placeholderProcessor();
    }

    /**
     * In place of {@code MixinFacade.finishInitialization} when Fabric's Mixin is shared ({@code
     * NeoForgeSharedMixinPatch}, ADR-0026 30): the configurations FML would give its Mixin ({@link
     * FmlSharedMixin#configurations}), for the bridge whose Mixin serves every loader ({@link #sharedMixinConfigs()}),
     * which adds them before the game starts.
     */
    public static void finishSharedMixin(LoadingModList mods, TransformingClassLoader loader) {
        Request current = request;
        if (current == null) {
            throw new IllegalStateException("Poly Mod Loader's FML host was not started");
        }
        List<SharedMixinConfig> configs =
                FmlSharedMixin.configurations(mods, current.otherMods(), mixinExtrasLeftOut, current.log());
        sharedMixinConfigs = configs;
        current.log()
                .accept("FML found " + configs.size() + " Mixin configuration(s) of NeoForge's mods and libraries for"
                        + " the Mixin Fabric mods share; FML's own Mixin does not start (ADR-0026 30)");
    }

    /** In place of FML's {@code new ModsFolderLocator()}: the NeoForge mods Poly Mod Loader found and left in. */
    public static IModFileCandidateLocator modsLocator() {
        Request current = request;
        if (current == null) {
            throw new IllegalStateException("Poly Mod Loader's FML host was not started");
        }
        return new PolyModsLocator(current.modJars());
    }

    private static void set(FMLLoader loader, String name, Object value) throws ReflectiveOperationException {
        Field field = FMLLoader.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(loader, value);
    }

    @Override
    public List<SharedMixinConfig> sharedMixinConfigs() {
        return sharedMixinConfigs;
    }

    @Override
    public ModuleLayer gameLayer() {
        return loader.getGameLayer();
    }

    @Override
    public BridgeClassTransformer classTransformer() {
        PolyTransformingClassLoader processors = classLoader;
        String name = "FML " + FMLLoader.class.getPackage().getImplementationVersion() + " class processors";
        return new BridgeClassTransformer() {
            @Override
            public String name() {
                return name;
            }

            /** FML's processors apply to the classes of FML's game layer, as its own class loader defines only those. */
            @Override
            public byte[] transform(String className, byte[] bytes) {
                if (!inGameLayer(className)) {
                    return bytes;
                }
                byte[] transformed =
                        WidenedFieldGetters.process(bytes, input -> processors.processAll(className, input));
                if (NeoForgeLaunch.mixinAudit()) {
                    MixinTypeAudit.record(className, transformed);
                }
                return transformed;
            }

            /**
             * A class of a game layer package that no jar holds, as FML's own loader defines it: its processors run on
             * empty bytes, and a processor may create the class (Mixin's synthetic classes, such as Lithium's {@code
             * Brain$Anonymous$...}).
             */
            @Override
            public byte[] generate(String className) {
                if (!inGameLayer(className)) {
                    return null;
                }
                byte[] generated = processors.processAll(className, new byte[0]);
                return generated.length == 0 ? null : generated;
            }

            private boolean inGameLayer(String className) {
                int lastDot = className.lastIndexOf('.');
                return lastDot > 0 && gamePackages.contains(className.substring(0, lastDot));
            }
        };
    }

    @Override
    public void launch(String mainClass) throws Throwable {
        if (NeoForgeLaunch.mixinAudit()) {
            auditMixins(mainClass);
            return;
        }
        Class<?> main = Class.forName(mainClass, true, loader.getCurrentClassLoader());
        MethodHandle handle = MethodHandles.publicLookup()
                .findStatic(main, "main", MethodType.methodType(void.class, String[].class));
        handle.invokeExact(loader.getProgramArgs().getArguments());
    }

    /**
     * {@link NeoForgeLaunch#MIXIN_AUDIT}: loads {@code mainClass} (which makes Mixin select its configurations), then
     * every class a Mixin configuration still targets, through FML's class loader as the game would, and logs each that
     * fails with its causes. Mixin stops applying mixins after its first critical failure, so later failures can hide
     * behind the first.
     */
    private void auditMixins(String mainClass) throws ReflectiveOperationException {
        ClassLoader classes = loader.getCurrentClassLoader();
        Request current = request;
        List<String> failures = new ArrayList<>();
        load(mainClass, classes, failures);
        Class<?> environmentClass = Class.forName("org.spongepowered.asm.mixin.MixinEnvironment", true, classes);
        Object environment = environmentClass.getMethod("getCurrentEnvironment").invoke(null);
        Object transformer = environmentClass.getMethod("getActiveTransformer").invoke(environment);
        Object processor = field(transformer, "processor");
        Set<String> targets = new TreeSet<>();
        int configurations = 0;
        List<?> configs = (List<?>) field(processor, "configs");
        for (Object config : configs) {
            configurations++;
            Method unhandled = config.getClass().getDeclaredMethod("getUnhandledTargets");
            unhandled.setAccessible(true);
            for (Object target : (Set<?>) unhandled.invoke(config)) {
                targets.add(target.toString().replace('/', '.'));
            }
        }
        for (String target : targets) {
            load(target, classes, failures);
        }
        // Code that casts to a type a left-out or failed mixin should have given its target (ADR-0026 39).
        List<String> notes = new ArrayList<>();
        failures.addAll(MixinTypeAudit.check(configs, classes, current.dist().equals("CLIENT"), notes));
        // Classes the host's patch and a mod give two defaults of the same method (ADR-0026 42).
        failures.addAll(MixinTypeAudit.conflictingDefaults(classes, notes));
        // Mostly mixins their mod's own configuration turns off (Lithium's options); listed for diagnosis.
        current.log()
                .accept("Mixin audit: " + notes.size() + " type(s) mixins would add are missing on their targets but"
                        + " no transformed game class uses them unchecked");
        notes.forEach(note -> current.log().accept("Mixin audit note: " + note));
        current.log()
                .accept("Mixin audit: " + targets.size() + " target class(es) of " + configurations
                        + " Mixin configuration(s) loaded through FML's class loader, " + failures.size()
                        + " failed");
        failures.forEach(failure -> current.log().accept("Mixin audit: " + failure));
        // What FML would show on its loading screen when the client starts (errors stop the game, warnings ask first).
        var issues = loader.getLoadingModList().getModLoadingIssues();
        current.log().accept("FML loading issues for the client: " + issues.size());
        issues.forEach(issue -> current.log()
                .accept("FML loading issue: " + issue.severity() + " " + issue.translationKey() + " "
                        + issue.translationArgs()));
    }

    private static void load(String name, ClassLoader classes, List<String> failures) {
        try {
            Class.forName(name, false, classes);
        } catch (Throwable e) {
            StringBuilder causes = new StringBuilder(name).append(" failed to load");
            for (Throwable cause = e; cause != null; cause = cause.getCause()) {
                causes.append(" <- ").append(cause);
            }
            failures.add(causes.toString());
        }
    }

    private static Object field(Object owner, String name) throws ReflectiveOperationException {
        for (Class<?> type = owner.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(owner);
            } catch (NoSuchFieldException e) {
                // a superclass may declare it
            }
        }
        throw new NoSuchFieldException(owner.getClass().getName() + "." + name);
    }
}
