package dev.polymodloader.compat.forge;

import dev.polymodloader.compat.bridge.BridgeClassTransformer;
import dev.polymodloader.compat.bridge.BridgeLaunchContext;
import dev.polymodloader.compat.bridge.GameClassBytes;
import dev.polymodloader.compat.bridge.GameHost;
import dev.polymodloader.compat.bridge.GameModuleLayers;
import dev.polymodloader.compat.bridge.InjectionPointReplacement;
import dev.polymodloader.compat.bridge.SharedMixinConfig;
import dev.polymodloader.internal.transform.TransformRuleDefinition;
import java.io.IOException;
import java.io.InputStream;
import java.lang.module.ModuleReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * MinecraftForge on Poly Mod Loader (ADR-0021): prepares Forge's files ({@link ForgeInstallation}), defines FML's BOOT
 * layer in the game class loader ({@link ForgeBootModules}), and hands over to Poly Mod Loader's implementation of
 * ModLauncher's launch ({@code PolyModLauncher}, inside that layer), which runs FML's own discovery and Forge's own
 * launch handler. FML sees exactly the Forge mods Poly Mod Loader found: they are resources of the game class loader, where
 * FML's {@code ClasspathLocator} finds them, and FML's own mods folder scan is left out ({@link ForgeBootModules}).
 *
 * <p>Thread: created and used by the launching thread only.
 */
final class ForgeGameHost implements GameHost {

    private static final String HOST_JAR = "/META-INF/polymodloader/forge-host.jar";
    /** The loader name of the mods FML runs, as {@link MinecraftForgeBridge#loaderName()}. */
    private static final String LOADER = "MinecraftForge";

    private final GameHost.Request request;
    private final ForgeInstallation.Prepared files;
    private final ForgeBootModules.Libraries libraries;
    private final List<Path> modJars;
    private final Path hostJar;
    private final List<SharedMixinConfig> sharedMixinConfigs;
    private final List<BridgeLaunchContext.LoadedMod> hostMods;
    private ForeignModIndex otherMods = ForeignModIndex.EMPTY;
    private ForgeLaunch launch;

    private ForgeGameHost(
            GameHost.Request request,
            ForgeInstallation.Prepared files,
            ForgeBootModules.Libraries libraries,
            List<Path> modJars,
            Path hostJar,
            List<SharedMixinConfig> sharedMixinConfigs,
            List<BridgeLaunchContext.LoadedMod> hostMods) {
        this.request = request;
        this.files = files;
        this.libraries = libraries;
        this.modJars = List.copyOf(modJars);
        this.hostJar = hostJar;
        this.sharedMixinConfigs = List.copyOf(sharedMixinConfigs);
        this.hostMods = List.copyOf(hostMods);
    }

    /**
     * Prepares Forge's files for {@code request}'s Minecraft version, to run {@code modJars} (the Forge mods Poly Mod Loader
     * found).
     *
     * @throws IOException when Poly Mod Loader has no Forge release for that version, Forge's file systems cannot be put
     *     on the JVM class path ({@link ForgeFileSystems}), or the files cannot be downloaded, checked or patched
     */
    static ForgeGameHost create(GameHost.Request request, List<Path> modJars) throws IOException {
        // Checked before downloading; Java's list of file systems is not asked for until Forge's jars are appended.
        ForgeFileSystems fileSystems = ForgeFileSystems.of(request.systemClassPath());
        fileSystems.checkAvailable();
        ForgeInstallation.Release release = ForgeInstallation.release(request.gameVersion())
                .orElseThrow(() -> new IOException(
                        "Poly Mod Loader has no MinecraftForge release for Minecraft " + request.gameVersion()));
        ForgeInstallation.Prepared files = ForgeInstallation.prepare(
                release,
                request.cacheDirectory().resolve("forge"),
                request.side(),
                request.vanillaGameJar(),
                ForgeInstallation.httpDownloader(),
                request.log());
        // Before sorting, so the libraries the class path now has are shared rather than defined as modules.
        fileSystems.install(files.fileSystemJars(), request.log());
        ForgeBootModules.Libraries libraries =
                ForgeBootModules.sort(files.libraries(), ForgeGameHost.class.getClassLoader(), !request.sharedMixin());
        if (request.sharedMixin()) {
            libraries = libraries.withSharedMixinExtras(files.directory());
        }
        List<Path> runnable = runnableMods(request, files, modJars);
        List<SharedMixinConfig> mixinConfigs = request.sharedMixin() ? ForgeMixinConfigs.read(runnable) : List.of();
        return new ForgeGameHost(
                request,
                files,
                libraries,
                runnable,
                extractHostJar(files.directory()),
                mixinConfigs,
                hostMods(runnable));
    }

    /**
     * The mods FML will accept: those failing their {@code mods.toml} requirements are left out first ({@code
     * POLY-COMPAT-209}), checked with the Maven version ranges of Forge's own {@code maven-artifact}.
     */
    private static List<Path> runnableMods(
            GameHost.Request request, ForgeInstallation.Prepared files, List<Path> modJars) throws IOException {
        Path mavenArtifact = files.libraries().stream()
                .filter(jar -> jar.getFileName().toString().startsWith("maven-artifact-"))
                .findFirst()
                .orElseThrow(() -> new IOException("Forge's libraries have no maven-artifact jar"));
        String forgeVersion = files.release().forgeVersion();
        forgeVersion = forgeVersion.substring(forgeVersion.indexOf('-') + 1);
        try (URLClassLoader maven =
                new URLClassLoader(new URL[] {mavenArtifact.toUri().toURL()}, ForgeGameHost.class.getClassLoader())) {
            ForgeModRequirements.Result result = ForgeModRequirements.check(
                    modJars,
                    request.gameVersion(),
                    forgeVersion,
                    request.side(),
                    ForgeModRequirements.maven(maven),
                    mixinLevels(request, files));
            for (ForgeModRequirements.Excluded excluded : result.excluded()) {
                request.log()
                        .accept("POLY-COMPAT-209 Forge mod " + excluded.modId() + " ("
                                + excluded.jar().getFileName() + ") is NOT loaded: "
                                + String.join("; ", excluded.reasons()));
            }
            if (!result.excluded().isEmpty()) {
                request.log()
                        .accept("MinecraftForge " + files.release().forgeVersion() + ": "
                                + result.kept().size()
                                + " of " + modJars.size() + " Forge mod file(s) meet their requirements; "
                                + result.excluded().size() + " left out (POLY-COMPAT-209)");
            }
            return result.kept();
        } catch (ReflectiveOperationException e) {
            throw new IOException("Forge's maven-artifact cannot check version ranges: " + e, e);
        }
    }

    /**
     * The compatibility levels of the Mixin that applies Forge mods' configurations: Forge's own, or with a shared
     * Mixin the one on Poly Mod Loader's class path. Empty when it cannot be read (not checked then).
     */
    private static Set<String> mixinLevels(GameHost.Request request, ForgeInstallation.Prepared files)
            throws IOException {
        String level = "org/spongepowered/asm/mixin/MixinEnvironment$CompatibilityLevel.class";
        if (request.sharedMixin()) {
            try (InputStream input = ForgeGameHost.class.getClassLoader().getResourceAsStream(level)) {
                return input == null ? Set.of() : ForgeModRequirements.mixinLevels(input);
            }
        }
        for (Path library : files.libraries()) {
            try (java.util.jar.JarFile jar = new java.util.jar.JarFile(library.toFile())) {
                java.util.zip.ZipEntry entry = jar.getEntry(level);
                if (entry != null) {
                    try (InputStream input = jar.getInputStream(entry)) {
                        return ForgeModRequirements.mixinLevels(input);
                    }
                }
            }
        }
        return Set.of();
    }

    @Override
    public String name() {
        return "MinecraftForge " + files.release().forgeVersion();
    }

    @Override
    public Path gameJar() {
        return files.patchedGame();
    }

    @Override
    public List<Path> resourceJars() {
        List<Path> jars = new ArrayList<>(libraries.modFiles());
        jars.add(files.patchedGame());
        jars.addAll(modJars);
        return jars;
    }

    @Override
    public void prepare(GameModuleLayers layers, GameClassBytes classes) throws Exception {
        ModuleLayer boot = ForgeBootModules.define(libraries, hostJar, layers, ForgeGameHost.class.getClassLoader());
        request.log()
                .accept(name() + ": " + boot.modules().size() + " boot module(s), "
                        + libraries.modFiles().size()
                        + " mod file(s) for FML, " + libraries.onClassPath().size()
                        + " librar(y/ies) shared with the game");
        Module modLauncher = boot.findModule(ForgeBootModules.MODLAUNCHER).orElseThrow();
        Class<?> host = Class.forName(modLauncher, ForgeBootModules.HOST_PACKAGE + ".PolyModLauncher");
        if (host == null) {
            throw new IllegalStateException("forge-host.jar has no PolyModLauncher");
        }
        Method start = host.getMethod("start", ForgeLaunch.Request.class);
        try {
            launch = (ForgeLaunch) start.invoke(
                    null,
                    new ForgeLaunch.Request(
                            launchTarget(request.launchTarget()),
                            request.gameVersion(),
                            request.gameDirectory(),
                            request.gameArguments(),
                            modJars,
                            otherMods,
                            boot,
                            layers,
                            classes,
                            layerModules(),
                            request.log()));
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception cause) {
                throw cause;
            }
            throw e;
        }
    }

    @Override
    public void runForeignModInitialization(Runnable initialization) {
        if (launch == null) {
            throw new IllegalStateException("Forge was not prepared");
        }
        ClassLoader game = launch.gameLayer().modules().iterator().next().getClassLoader();
        try {
            ForgeRegistryOpening.run(game, initialization);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Forge's registries could not be opened for other loaders' mods (" + e + ")", e);
        }
    }

    @Override
    public Set<String> modIds() {
        Set<String> ids = new LinkedHashSet<>();
        hostMods.forEach(mod -> ids.add(mod.modId()));
        return Set.copyOf(ids);
    }

    @Override
    public List<BridgeLaunchContext.LoadedMod> mods() {
        return hostMods;
    }

    /** FML's mod list answers for {@code mods} too, through {@link ForgeModListPatch} (ADR-0025). */
    @Override
    public void otherMods(List<BridgeLaunchContext.LoadedMod> mods) {
        otherMods = ForeignModIndex.of(mods, modIds());
    }

    /**
     * The mods FML runs from {@code modJars}, as other loaders see them: each jar's {@code mods.toml} entries, then the
     * mods of the jars nested in it ({@code META-INF/jarjar/}), which FML installs with it. A nested mod's jar is the
     * outer one, where FML reads it from.
     */
    static List<BridgeLaunchContext.LoadedMod> hostMods(List<Path> modJars) {
        List<BridgeLaunchContext.LoadedMod> own = new ArrayList<>();
        List<BridgeLaunchContext.LoadedMod> nested = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Path jar : modJars) {
            ForgeModRequirements.ModsToml toml;
            try {
                toml = ForgeModRequirements.ModsToml.read(jar);
            } catch (IOException | RuntimeException e) {
                // The requirements check left out jars whose mods.toml cannot be read; nothing to add.
                continue;
            }
            if (toml == null) {
                continue; // a ModLauncher service jar without mods.toml
            }
            toml.versions().forEach((id, version) -> {
                if (seen.add(id)) {
                    own.add(new BridgeLaunchContext.LoadedMod(
                            LOADER,
                            id,
                            version,
                            toml.displayNames().getOrDefault(id, id),
                            List.of(),
                            jar,
                            null,
                            null,
                            true));
                }
            });
            for (ForgeModRequirements.ModsToml.NestedMod mod : toml.nestedMods()) {
                if (seen.add(mod.modId())) {
                    nested.add(new BridgeLaunchContext.LoadedMod(
                            LOADER,
                            mod.modId(),
                            mod.version(),
                            mod.displayName(),
                            List.of(),
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

    @Override
    public List<SharedMixinConfig> sharedMixinConfigs() {
        return sharedMixinConfigs;
    }

    /** Where Forge's patch moved vanilla calls that Fabric mods inject at ({@link ForgeInjectionPoints}). */
    @Override
    public List<InjectionPointReplacement> injectionPointReplacements() {
        return ForgeInjectionPoints.all();
    }

    /**
     * The title screen's mod counts above Forge's branding ({@link ForgeTitleBranding}), where Forge's patch replaced
     * the vanilla version text.
     */
    @Override
    public List<TransformRuleDefinition> transformRules() {
        return List.of(ForgeTitleBranding.rule(request.gameVersion()));
    }

    @Override
    public void addBrandingLines(Supplier<List<String>> lines) {
        ForgeTitleBranding.install(lines, request.log());
    }

    @Override
    public Optional<BridgeClassTransformer> classTransformer() {
        return Optional.ofNullable(launch).map(ForgeLaunch::classTransformer);
    }

    /**
     * With other mods in this launch, FML's {@code ModList} is read through {@link ForgeModListPatch} (ADR-0025). Its
     * module ({@code fmlcore}) is an FML {@code LIBRARY}, which FML puts in the PLUGIN layer, whose classes are defined
     * as they are (as in ModLauncher), so the class transformation never sees it.
     */
    private UnaryOperator<ModuleReference> layerModules() {
        if (otherMods.isEmpty()) {
            return UnaryOperator.identity();
        }
        int count = otherMods.mods().size();
        return reference -> ForgeModListPatch.patching(reference, ForgeModListPatch.FOREIGN_MOD_LIST, result -> {
            if (!result.changed().isEmpty()) {
                request.log()
                        .accept("FML's ModList " + result.changed() + " also answers for " + count
                                + " Poly Mod(s) and other loaders' mod(s) (ADR-0025)");
            }
            if (!result.unchanged().isEmpty()) {
                request.log()
                        .accept("POLY-COMPAT-211 FML's ModList " + result.unchanged()
                                + " is not the code Poly Mod Loader"
                                + " checked (MinecraftForge " + files.release().forgeVersion() + "); through it, Forge"
                                + " mods do not see the " + count + " Poly Mod(s) and other loaders' mod(s)");
            }
        });
    }

    @Override
    public void launch() throws Throwable {
        if (launch == null) {
            throw new IllegalStateException("Forge was not prepared");
        }
        System.setProperty("fml.earlyprogresswindow", "false");
        System.setProperty("forge.forceDisplay", "false");
        launch.launch();
    }

    /**
     * Forge's launch handler for Poly Mod Loader's launch target: {@code ForgeProdLaunchHandler}'s for the client and the
     * dedicated server, and Poly Mod Loader's {@code GameTestLaunchHandler} for the game test server, which Forge runs only
     * outside production.
     */
    static String launchTarget(String polyTarget) {
        return switch (polyTarget) {
            case "client" -> "forge_client";
            case "game_test_server" -> "polymodloader_server_gametest";
            default -> "forge_server";
        };
    }

    /** Copies Poly Mod Loader's {@code forge-host.jar} next to Forge's files, replacing an older copy. */
    private static Path extractHostJar(Path directory) throws IOException {
        byte[] bytes;
        try (InputStream in = ForgeGameHost.class.getResourceAsStream(HOST_JAR)) {
            if (in == null) {
                throw new IOException("Poly Mod Loader's Forge bridge has no " + HOST_JAR);
            }
            bytes = in.readAllBytes();
        }
        Path target = directory.resolve("polymodloader-forge-host.jar");
        if (Files.isRegularFile(target) && Arrays.equals(Files.readAllBytes(target), bytes)) {
            return target;
        }
        Path partial = directory.resolve("polymodloader-forge-host.jar.partial");
        Files.write(partial, bytes);
        Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        return target;
    }
}
