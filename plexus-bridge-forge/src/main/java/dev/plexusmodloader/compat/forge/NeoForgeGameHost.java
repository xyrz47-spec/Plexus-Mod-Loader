package dev.plexusmodloader.compat.forge;

import dev.plexusmodloader.api.runtime.GameSide;
import dev.plexusmodloader.compat.bridge.BridgeClassTransformer;
import dev.plexusmodloader.compat.bridge.BridgeLaunchContext;
import dev.plexusmodloader.compat.bridge.GameClassBytes;
import dev.plexusmodloader.compat.bridge.GameHost;
import dev.plexusmodloader.compat.bridge.GameModuleLayers;
import dev.plexusmodloader.compat.bridge.InjectionPointReplacement;
import dev.plexusmodloader.compat.bridge.SharedMixinConfig;
import dev.plexusmodloader.internal.transform.TransformRuleDefinition;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.classfile.ClassHierarchyResolver;
import java.lang.module.Configuration;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.jar.JarFile;

/**
 * NeoForge on Poly Mod Loader (ADR-0026): prepares NeoForge's files ({@link NeoForgeInstallation}), defines the layer
 * of NeoForge's loader libraries in the game class loader with Poly Mod Loader's host ({@code neoforge-host.jar}) and
 * the checked patch of {@code FMLLoader} ({@link NeoForgeLoaderPatch}), and runs FML's own startup there ({@code
 * PlexusFml.start}). The game class loader defines the game layer FML computes; FML sees exactly the NeoForge mods Poly
 * Mod Loader found.
 *
 * <p>FML's {@code ModList} answers for the Poly Mods and other loaders' mods in the same game too ({@link
 * ForgeModListPatch}, ADR-0025). With Fabric or Quilt mods in the same game, Fabric's Mixin serves NeoForge's mods
 * too, FML does not start its own ({@link NeoForgeSharedMixinPatch}), and the other loaders' mods initialize with
 * NeoForge's registries open (ADR-0026 30, 31).
 *
 * <p>Thread: created and used by the launching thread only.
 */
final class NeoForgeGameHost implements GameHost {

    private static final String HOST_JAR = "/META-INF/polymodloader/neoforge-host.jar";
    private static final String HOST_CLASS = "dev.plexusmodloader.compat.forge.fml.PlexusFml";
    private static final String MIXIN_BOOTSTRAP = "org/spongepowered/asm/launch/MixinBootstrap.class";

    private final GameHost.Request request;
    private final NeoForgeInstallation.Prepared files;
    private final List<Path> modules;
    private final List<Path> onClassPath;
    private final Path hostJar;
    private final List<Path> modJars;
    private final List<BridgeLaunchContext.LoadedMod> hostMods;
    private final List<Path> declined;
    private ForeignModIndex otherMods = ForeignModIndex.EMPTY;
    private NeoForgeLaunch launch;

    private NeoForgeGameHost(
            GameHost.Request request,
            NeoForgeInstallation.Prepared files,
            List<Path> modules,
            List<Path> onClassPath,
            Path hostJar,
            List<Path> modJars,
            List<BridgeLaunchContext.LoadedMod> hostMods,
            List<Path> declined) {
        this.request = request;
        this.files = files;
        this.modules = List.copyOf(modules);
        this.onClassPath = List.copyOf(onClassPath);
        this.hostJar = hostJar;
        this.modJars = List.copyOf(modJars);
        this.hostMods = List.copyOf(hostMods);
        this.declined = List.copyOf(declined);
    }

    /**
     * Prepares NeoForge's files for {@code request}'s Minecraft version, to run {@code modJars} (the NeoForge mods Poly
     * Mod Loader found).
     *
     * @throws IOException when Poly Mod Loader has no NeoForge release for that version, Fabric mods run in the same
     *     launch, or the files cannot be downloaded, checked or patched
     */
    static NeoForgeGameHost create(GameHost.Request request, List<Path> modJars) throws IOException {
        if (request.jdkModuleAccess().isEmpty()) {
            throw new IOException("NeoForge's FML needs java.lang.invoke opened to it, as NeoForge's launcher profile"
                    + " does with --add-opens; that takes Poly Mod Loader's Java agent, and this launch was started"
                    + " without it");
        }
        NeoForgeInstallation.Release release = NeoForgeInstallation.release(request.gameVersion())
                .orElseThrow(() -> new IOException(
                        "Poly Mod Loader has no NeoForge release for Minecraft " + request.gameVersion()));
        NeoForgeInstallation.Prepared files = NeoForgeInstallation.prepare(
                release,
                request.cacheDirectory().resolve("neoforge"),
                request.side(),
                request.vanillaGameJar(),
                ForgeInstallation.httpDownloader(),
                request.log());
        if (request.sharedMixin()) {
            checkSharedMixin(files);
        }
        List<Path> modules = new ArrayList<>();
        List<Path> onClassPath = new ArrayList<>();
        ClassLoader classPath = NeoForgeGameHost.class.getClassLoader();
        for (Path library : files.libraries()) {
            try (JarFile jar = new JarFile(library.toFile())) {
                // FML's Mixin service is found by class name from Mixin's own class loader, so Mixin is a module
                // next to FML although Poly Mod Loader's class path has the same release. With Fabric mods, Fabric's
                // Mixin on the class path serves NeoForge's mods too (ADR-0026 30), and FML uses that one.
                if ((jar.getEntry(MIXIN_BOOTSTRAP) == null || request.sharedMixin()) && onClassPath(jar, classPath)) {
                    onClassPath.add(library);
                } else {
                    modules.add(library);
                }
            }
        }
        List<Path> runnable = runnableMods(request, files, modJars);
        // With Fabric's Mixin shared, a jar built for several loaders that FML would refuse may run by its other
        // metadata instead (declinedJars).
        List<Path> declined = request.sharedMixin()
                ? modJars.stream().filter(jar -> !runnable.contains(jar)).toList()
                : List.of();
        return new NeoForgeGameHost(
                request,
                files,
                modules,
                onClassPath,
                extractHostJar(files.directory()),
                runnable,
                NeoForgeModRequirements.hostMods(runnable),
                declined);
    }

    /**
     * With Fabric or Quilt mods in the launch, whether FML's own Mixin start is the code {@link
     * NeoForgeSharedMixinPatch} changes, before NeoForge starts: otherwise FML would start a second Mixin, so NeoForge's
     * mods are left out ({@code PLEXUS-COMPAT-216}, by the runtime) and the other mods run.
     */
    private static void checkSharedMixin(NeoForgeInstallation.Prepared files) throws IOException {
        Path loader = fmlLoaderJar(files);
        List<String> missing;
        try (JarFile jar = new JarFile(loader.toFile())) {
            missing = NeoForgeSharedMixinPatch.missing(
                    entry(jar, NeoForgeLoaderPatch.CLASS_FILE), entry(jar, NeoForgeSharedMixinPatch.FACADE_CLASS_FILE));
        }
        if (!missing.isEmpty()) {
            throw new IOException("NeoForge mods do not run together with Fabric or Quilt mods on "
                    + loader.getFileName() + ": FML's Mixin start is not the code Poly Mod Loader checked (missing: "
                    + String.join("; ", missing) + "), and two Mixins cannot run in one game");
        }
    }

    private static byte[] entry(JarFile jar, String name) throws IOException {
        var entry = jar.getEntry(name);
        if (entry == null) {
            throw new IOException(jar.getName() + " has no " + name);
        }
        try (InputStream input = jar.getInputStream(entry)) {
            return input.readAllBytes();
        }
    }

    /** FML's {@code loader} jar among NeoForge's libraries ({@code net/neoforged/fancymodloader/loader/}). */
    private static Path fmlLoaderJar(NeoForgeInstallation.Prepared files) throws IOException {
        return files.libraries().stream()
                .filter(jar -> jar.getFileName().toString().startsWith("loader-")
                        && jar.getParent().getParent().getFileName().toString().equals("loader")
                        && jar.getParent()
                                .getParent()
                                .getParent()
                                .getFileName()
                                .toString()
                                .equals("fancymodloader"))
                .findFirst()
                .orElseThrow(() -> new IOException("NeoForge's libraries have no FancyModLoader loader jar"));
    }

    /**
     * The mods FML will accept: those failing their {@code neoforge.mods.toml} requirements are left out first ({@code
     * PLEXUS-COMPAT-217}), checked with the Maven version ranges of NeoForge's own {@code maven-artifact} and the version
     * FML's Java language loader has ({@link #javaLanguageVersion}).
     */
    private static List<Path> runnableMods(
            GameHost.Request request, NeoForgeInstallation.Prepared files, List<Path> modJars) throws IOException {
        Path mavenArtifact = library(files, "maven-artifact-");
        String javaLanguage = javaLanguageVersion(fmlLoaderJar(files));
        String neoForgeVersion = files.release().neoForgeVersion();
        try (URLClassLoader maven = new URLClassLoader(
                new URL[] {mavenArtifact.toUri().toURL()}, NeoForgeGameHost.class.getClassLoader())) {
            ForgeModRequirements.Result result = NeoForgeModRequirements.check(
                    modJars,
                    request.gameVersion(),
                    neoForgeVersion,
                    javaLanguage,
                    request.side(),
                    ForgeModRequirements.maven(maven));
            for (ForgeModRequirements.Excluded excluded : result.excluded()) {
                request.log()
                        .accept("PLEXUS-COMPAT-217 NeoForge mod " + excluded.modId() + " ("
                                + excluded.jar().getFileName() + ") is NOT loaded: "
                                + String.join("; ", excluded.reasons()));
            }
            if (!result.excluded().isEmpty()) {
                request.log()
                        .accept("NeoForge " + neoForgeVersion + ": "
                                + result.kept().size() + " of " + modJars.size()
                                + " NeoForge mod file(s) meet their requirements; "
                                + result.excluded().size()
                                + " left out (PLEXUS-COMPAT-217)");
            }
            return result.kept();
        } catch (ReflectiveOperationException e) {
            throw new IOException("NeoForge's maven-artifact cannot check version ranges: " + e, e);
        }
    }

    private static Path library(NeoForgeInstallation.Prepared files, String fileNamePrefix) throws IOException {
        return files.libraries().stream()
                .filter(jar -> jar.getFileName().toString().startsWith(fileNamePrefix))
                .findFirst()
                .orElseThrow(() -> new IOException("NeoForge's libraries have no " + fileNamePrefix + "* jar"));
    }

    /**
     * The version of FML's Java language loader {@code javafml} as NeoForge runs it from the class path: the {@code
     * Implementation-Version} of FML's {@code loader} jar (FML's {@code JarVersionLookupHandler}; {@code 11.0} for
     * 11.0.16).
     */
    static String javaLanguageVersion(Path fmlLoaderJar) throws IOException {
        try (JarFile jar = new JarFile(fmlLoaderJar.toFile())) {
            String version = jar.getManifest() == null
                    ? null
                    : jar.getManifest().getMainAttributes().getValue("Implementation-Version");
            if (version == null || version.isBlank()) {
                throw new IOException(fmlLoaderJar.getFileName() + " names no Implementation-Version");
            }
            return version;
        }
    }

    @Override
    public String name() {
        return "NeoForge " + files.release().neoForgeVersion();
    }

    @Override
    public Path gameJar() {
        return files.patchedGame();
    }

    /**
     * None: FML finds the patched game and NeoForge's {@code universal.jar} in its library folder, and would take a copy
     * the game class loader shows as a development setup.
     */
    @Override
    public List<Path> resourceJars() {
        return List.of();
    }

    /**
     * The patched game and NeoForge's universal jar, which FML defines as modules of its game layer when it starts:
     * Fabric's Mixin reads the game from them before that (ADR-0026 30).
     */
    @Override
    public List<Path> classBytesJars() {
        return List.of(files.patchedGame(), files.universal());
    }

    /**
     * NeoForge's loader libraries that become modules ({@link #prepare}; those Poly Mod Loader's class path has already
     * stay there) and the universal jar, which FML defines in its game layer.
     */
    @Override
    public List<Path> loaderJars() {
        List<Path> jars = new ArrayList<>(modules);
        jars.add(files.universal());
        return jars;
    }

    @Override
    public void prepare(GameModuleLayers layers, GameClassBytes classes) throws Exception {
        AtomicReference<NeoForgeLoaderPatch.Result> patch = new AtomicReference<>();
        ModuleLayer libraryLayer;
        try (URLClassLoader hierarchyJars =
                new URLClassLoader(urls(modules), NeoForgeGameHost.class.getClassLoader())) {
            ClassHierarchyResolver hierarchy = ClassHierarchyResolver.ofResourceParsing(hierarchyJars)
                    .orElse(ClassHierarchyResolver.defaultResolver());
            libraryLayer = defineLibraryLayer(layers, hierarchy, patch);
            openJdk(libraryLayer.modules());
            Module host = libraryLayer.modules().stream()
                    .filter(module -> module.getPackages().contains("dev.plexusmodloader.compat.forge.fml"))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("neoforge-host.jar is not in the library layer"));
            request.log()
                    .accept(name() + ": " + libraryLayer.modules().size() + " library module(s), " + onClassPath.size()
                            + " librar(y/ies) shared with the game");
            Class<?> fml = Class.forName(host, HOST_CLASS);
            if (fml == null) {
                throw new IllegalStateException("neoforge-host.jar has no " + HOST_CLASS);
            }
            ClassLoader gameClassLoader = host.getClassLoader();
            List<String> arguments = new ArrayList<>(request.gameArguments());
            arguments.addAll(files.gameArguments());
            try {
                launch = (NeoForgeLaunch) fml.getMethod("start", NeoForgeLaunch.Request.class)
                        .invoke(
                                null,
                                new NeoForgeLaunch.Request(
                                        request.side() == GameSide.CLIENT ? "CLIENT" : "DEDICATED_SERVER",
                                        // With Poly Mod Loader's early window, FML opens none of its own.
                                        request.side() != GameSide.CLIENT
                                                || NeoForgeLaunch.headlessClient()
                                                || request.polyEarlyWindow(),
                                        request.gameDirectory(),
                                        arguments,
                                        files.libraryDirectory(),
                                        Set.of(request.vanillaGameJar().toAbsolutePath()),
                                        modJars,
                                        libraryLayer,
                                        layers,
                                        classes,
                                        gameClassLoader,
                                        this::openJdk,
                                        otherMods,
                                        request.sharedMixin(),
                                        request.log()));
            } catch (InvocationTargetException e) {
                NeoForgeLoaderPatch.Result result = patch.get();
                if (result != null && !result.complete()) {
                    throw new IllegalStateException(
                            "PLEXUS-COMPAT-215 FML's FMLLoader is not the code Poly Mod Loader checked (" + name()
                                    + "); missing: " + result.missing(),
                            e.getCause());
                }
                if (e.getCause() instanceof Exception cause) {
                    throw cause;
                }
                throw e;
            }
        }
        if (request.sharedMixin()) {
            // Other loaders' custom payloads next to NeoForge's (ADR-0026 40).
            NeoForgeForeignPayloads.install(
                    launch.gameLayer().modules().iterator().next().getClassLoader());
        }
    }

    /**
     * NeoForge's loader libraries (those Poly Mod Loader's class path does not have) and the host as modules of the
     * game class loader, defined as they are except {@code FMLLoader} ({@link NeoForgeLoaderPatch}). NeoForge runs these
     * libraries from the class path, so each is an automatic module here ({@link #automatic}), and every module reads
     * Poly Mod Loader's class path, where the libraries it shares with Minecraft are.
     */
    private ModuleLayer defineLibraryLayer(
            GameModuleLayers layers,
            ClassHierarchyResolver hierarchy,
            AtomicReference<NeoForgeLoaderPatch.Result> patch) {
        List<Path> jars = new ArrayList<>(modules);
        jars.add(hostJar);
        Map<String, ModuleReference> references = new HashMap<>();
        for (ModuleReference reference :
                ModuleFinder.of(jars.toArray(Path[]::new)).findAll()) {
            references.put(reference.descriptor().name(), reference);
        }
        Map<String, ModuleReference> rewritten = new HashMap<>();
        UnaryOperator<ModuleReference> modList = modListPatch();
        Path earlyScreen = request.gameDirectory().resolve(".poly").resolve("early-screen");
        for (Map.Entry<String, ModuleReference> entry : references.entrySet()) {
            ModuleReference reference = modList.apply(automatic(entry.getValue()));
            if (request.side() == GameSide.CLIENT && !request.polyEarlyWindow()) {
                // NeoForge's early loading window shows Poly Mod Loader's art and title.
                reference = EarlyScreenArt.of(reference, earlyScreen, request.log());
                reference = EarlyWindowTitle.of(reference, request.gameVersion(), request.log());
            }
            if (request.sharedMixin()) {
                reference = NeoForgeSharedMixinPatch.patching(reference, hierarchy, result -> {
                    if (!result.missing().isEmpty()) {
                        throw new IllegalStateException("PLEXUS-COMPAT-215 FML's Mixin start is not the code Poly Mod"
                                + " Loader checked (" + name() + "); missing: " + result.missing());
                    }
                    if (!result.changed().isEmpty()) {
                        request.log().accept("FML's Mixin: " + result.changed() + " (ADR-0026 30)");
                    }
                });
            }
            rewritten.put(entry.getKey(), NeoForgeLoaderPatch.patching(reference, hierarchy, result -> {
                patch.set(result);
                if (result.complete()) {
                    request.log().accept("FML's FMLLoader: " + result.changed() + " (ADR-0026)");
                } else {
                    request.log()
                            .accept("PLEXUS-COMPAT-215 FML's FMLLoader is not the code Poly Mod Loader checked ("
                                    + name() + "); missing: " + result.missing());
                }
            }));
        }
        ModuleFinder finder = new ModuleFinder() {
            @Override
            public Optional<ModuleReference> find(String name) {
                return Optional.ofNullable(rewritten.get(name));
            }

            @Override
            public Set<ModuleReference> findAll() {
                return Set.copyOf(rewritten.values());
            }
        };
        Configuration configuration =
                ModuleLayer.boot().configuration().resolveAndBind(finder, ModuleFinder.of(), rewritten.keySet());
        ModuleLayer.Controller controller = layers.define(configuration, List.of(ModuleLayer.boot()), false);
        Module classPath = NeoForgeGameHost.class.getClassLoader().getUnnamedModule();
        for (Module module : controller.layer().modules()) {
            controller.addReads(module, classPath);
            controller.addReads(module, module.getClassLoader().getUnnamedModule());
        }
        return controller.layer();
    }

    /**
     * {@code reference} as an automatic module, as a jar on the class path behaves: it reads and exports everything,
     * and its {@code module-info} (if any) no longer requires or uses what is not here, such as ModLauncher's services
     * that Mixin's names. It provides what its {@code module-info} and its {@code META-INF/services} name.
     */
    static ModuleReference automatic(ModuleReference reference) {
        ModuleDescriptor descriptor = reference.descriptor();
        if (descriptor.isAutomatic()) {
            return reference;
        }
        ModuleDescriptor.Builder builder = ModuleDescriptor.newAutomaticModule(descriptor.name());
        descriptor.version().ifPresent(builder::version);
        builder.packages(descriptor.packages());
        Map<String, List<String>> provides = new HashMap<>();
        descriptor
                .provides()
                .forEach(p -> provides.computeIfAbsent(p.service(), s -> new ArrayList<>())
                        .addAll(p.providers()));
        try (ModuleReader reader = reference.open()) {
            for (String name : reader.list().toList()) {
                if (!name.startsWith("META-INF/services/") || name.endsWith("/")) {
                    continue;
                }
                String service = name.substring("META-INF/services/".length());
                Optional<InputStream> input = reader.open(name);
                if (input.isEmpty()) {
                    continue;
                }
                try (InputStream stream = input.get()) {
                    for (String line : new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                        String provider = line.replaceFirst("#.*", "").strip();
                        int lastDot = provider.lastIndexOf('.');
                        List<String> providers = provides.computeIfAbsent(service, s -> new ArrayList<>());
                        if (lastDot > 0
                                && descriptor.packages().contains(provider.substring(0, lastDot))
                                && !providers.contains(provider)) {
                            providers.add(provider);
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read the services of module " + descriptor.name(), e);
        }
        provides.forEach((service, providers) -> {
            if (!providers.isEmpty()) {
                builder.provides(service, providers);
            }
        });
        ModuleDescriptor automatic = builder.build();
        return new ModuleReference(automatic, reference.location().orElse(null)) {
            @Override
            public ModuleReader open() throws IOException {
                return reference.open();
            }
        };
    }

    /**
     * With other mods in this launch, FML's {@code ModList} (in FML's {@code loader}, a module of the library layer,
     * whose classes are defined as they are) is read through {@link ForgeModListPatch} so that it answers for them too
     * (ADR-0025); {@code PLEXUS-COMPAT-218} when its code is not the one checked.
     */
    private UnaryOperator<ModuleReference> modListPatch() {
        if (otherMods.isEmpty()) {
            return UnaryOperator.identity();
        }
        int count = otherMods.mods().size();
        return reference -> ForgeModListPatch.patching(
                reference,
                ForgeModListPatch.Shape.NEOFORGE,
                ForgeModListPatch.Shape.NEOFORGE.foreignModList(),
                result -> {
                    if (!result.changed().isEmpty()) {
                        request.log()
                                .accept("FML's ModList " + result.changed() + " also answers for " + count
                                        + " Poly Mod(s) and other loaders' mod(s) (ADR-0025)");
                    }
                    if (!result.unchanged().isEmpty()) {
                        request.log()
                                .accept("PLEXUS-COMPAT-218 NeoForge's FML ModList " + result.unchanged()
                                        + " is not the code Poly Mod Loader checked (" + name() + "); through it,"
                                        + " NeoForge mods do not see the " + count
                                        + " Poly Mod(s) and other loaders' mod(s)");
                    }
                });
    }

    /** Opens {@code java.base}'s {@code java.lang.invoke} to {@code modules}, as NeoForge's launcher profile does. */
    private void openJdk(Set<Module> modules) {
        request.jdkModuleAccess().orElseThrow().open(Object.class.getModule(), "java.lang.invoke", modules);
    }

    /**
     * With Fabric or Quilt mods in the launch, the jars FML would refuse ({@code PLEXUS-COMPAT-217}); the runtime runs one
     * built for another loader too by that loader's metadata (ADR-0026 35).
     */
    @Override
    public List<Path> declinedJars() {
        return declined;
    }

    /** With Fabric or Quilt mods in the launch, Fabric's Mixin serves NeoForge's mods too (ADR-0026 30). */
    @Override
    public boolean sharesMixin() {
        return request.sharedMixin();
    }

    /** The configurations FML found for its mods once it started ({@link NeoForgeLaunch#sharedMixinConfigs()}). */
    @Override
    public List<SharedMixinConfig> sharedMixinConfigs() {
        return launch == null ? List.of() : launch.sharedMixinConfigs();
    }

    /** Where NeoForge's patch moved vanilla calls that Fabric mods inject at ({@link NeoForgeInjectionPoints}). */
    @Override
    public List<InjectionPointReplacement> injectionPointReplacements() {
        return NeoForgeInjectionPoints.all();
    }

    /**
     * Other loaders' mods initialize with NeoForge's registries open, as NeoForge's mods register during its {@code
     * RegisterEvent} ({@link NeoForgeRegistryOpening}).
     */
    @Override
    public void runForeignModInitialization(Runnable initialization) {
        if (launch == null) {
            throw new IllegalStateException("NeoForge was not prepared");
        }
        ClassLoader game = launch.gameLayer().modules().iterator().next().getClassLoader();
        try {
            NeoForgeRegistryOpening.run(game, initialization);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "NeoForge's registries could not be opened for other loaders' mods (" + e + ")", e);
        }
    }

    @Override
    public Set<String> modIds() {
        Set<String> ids = new LinkedHashSet<>();
        hostMods.forEach(mod -> ids.add(mod.modId()));
        return Set.copyOf(ids);
    }

    /** The mods FML runs, nested JarJar mods included, for Poly Mods' and other loaders' mod lists (ADR-0025). */
    @Override
    public List<BridgeLaunchContext.LoadedMod> mods() {
        return hostMods;
    }

    /**
     * The title screen's mod counts above NeoForge's branding ({@link NeoForgeTitleBranding}), where NeoForge's patch
     * replaced the vanilla version text.
     */
    @Override
    public List<TransformRuleDefinition> transformRules() {
        return List.of(NeoForgeTitleBranding.rule(request.gameVersion()));
    }

    /**
     * {@link #transformRules()}, and with other loaders' mods sharing the game, the rules that leave their custom
     * payloads to their own networking ({@link NeoForgeForeignPayloads}, ADR-0026 40).
     */
    @Override
    public List<TransformRuleDefinition> transformRules(String gameLayerPackage) {
        if (!request.sharedMixin() || gameLayerPackage.isEmpty()) {
            return transformRules();
        }
        List<TransformRuleDefinition> rules = new ArrayList<>(transformRules());
        rules.addAll(NeoForgeForeignPayloads.rules(request.gameVersion(), gameLayerPackage));
        return List.copyOf(rules);
    }

    /** NeoForge's universal jar, which has the class {@link NeoForgeTitleBranding}'s rule targets. */
    @Override
    public List<Path> transformTargetJars() {
        return List.of(files.universal());
    }

    @Override
    public void addBrandingLines(Supplier<List<String>> lines) {
        NeoForgeTitleBranding.install(lines, request.log());
    }

    /** FML's mod list answers for {@code mods} too, through {@link ForgeModListPatch} (ADR-0025). */
    @Override
    public void otherMods(List<BridgeLaunchContext.LoadedMod> mods) {
        otherMods = ForeignModIndex.of(mods, modIds());
    }

    @Override
    public Optional<BridgeClassTransformer> classTransformer() {
        return Optional.ofNullable(launch).map(NeoForgeLaunch::classTransformer);
    }

    @Override
    public void launch() throws Throwable {
        if (launch == null) {
            throw new IllegalStateException("NeoForge was not prepared");
        }
        launch.launch(mainClass(request.launchTarget()));
    }

    /** The game's main class for Poly Mod Loader's launch target, as FML's own entry points call it. */
    static String mainClass(String polyTarget) {
        return switch (polyTarget) {
            case "client" -> "net.minecraft.client.main.Main";
            case "game_test_server" -> "net.minecraft.gametest.Main";
            default -> "net.minecraft.server.Main";
        };
    }

    private static URL[] urls(List<Path> jars) throws IOException {
        URL[] urls = new URL[jars.size()];
        for (int i = 0; i < urls.length; i++) {
            urls[i] = jars.get(i).toUri().toURL();
        }
        return urls;
    }

    /** Whether Poly Mod Loader's class path already has this jar's classes (checked with its first class). */
    private static boolean onClassPath(JarFile jar, ClassLoader classPath) {
        var entries = jar.entries();
        while (entries.hasMoreElements()) {
            String name = entries.nextElement().getName();
            if (name.endsWith(".class") && !name.startsWith("META-INF/") && !name.equals("module-info.class")) {
                return classPath.getResource(name) != null;
            }
        }
        return false;
    }

    /** Copies Poly Mod Loader's {@code neoforge-host.jar} next to NeoForge's files, replacing an older copy. */
    private static Path extractHostJar(Path directory) throws IOException {
        byte[] bytes;
        try (InputStream in = NeoForgeGameHost.class.getResourceAsStream(HOST_JAR)) {
            if (in == null) {
                throw new IOException("Poly Mod Loader's NeoForge bridge has no " + HOST_JAR);
            }
            bytes = in.readAllBytes();
        }
        Path target = directory.resolve("polymodloader-neoforge-host.jar");
        if (Files.isRegularFile(target) && Arrays.equals(Files.readAllBytes(target), bytes)) {
            return target;
        }
        Path partial = directory.resolve("polymodloader-neoforge-host.jar.partial");
        Files.write(partial, bytes);
        Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        return target;
    }
}
