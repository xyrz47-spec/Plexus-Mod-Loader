package dev.polymodloader.compat.quilt;

import dev.polymodloader.compat.bridge.BridgeLaunchContext;
import dev.polymodloader.compat.fabric.loader.FabricFamilyMod;
import dev.polymodloader.compat.fabric.loader.FabricLoaderRuntime;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.ObjectShare;
import org.quiltmc.loader.api.MappingResolver;
import org.quiltmc.loader.api.ModContainer;
import org.quiltmc.loader.api.ModContainer.BasicSourceType;
import org.quiltmc.loader.api.entrypoint.EntrypointContainer;
import org.quiltmc.loader.api.entrypoint.EntrypointException;
import org.quiltmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.quiltmc.loader.impl.QuiltLoaderImpl;
import org.quiltmc.loader.impl.game.GameProvider;

/**
 * Poly Mod Loader's implementation of Quilt Loader's runtime API ({@code QuiltLoader}, through {@link QuiltLoaderImpl}),
 * built from the mods Poly Mod Loader decided to load (ADR-0027 decision 4). It wraps the Fabric bridge's {@link
 * FabricLoaderRuntime}, so entrypoint instances, the shared object store ({@link ObjectShare}), the game object, folders
 * and launch arguments are the same ones the Fabric Loader API gives, as under Quilt Loader.
 *
 * <ul>
 *   <li>Mods: every loaded mod of every loader ({@link BridgeLaunchContext#mods()}) as a {@link QuiltModContainer}:
 *       Quilt mods {@code NORMAL_QUILT} with their own group, Fabric mods {@code NORMAL_FABRIC} in {@code
 *       loader.fabric}, Poly Mods and the mods a host loader runs {@code OTHER} in {@code loader.poly}, {@code
 *       loader.minecraftforge}, ...; and the built-in {@code quilt_loader} (which provides {@code fabricloader}), {@code
 *       minecraft}, {@code java} and {@code mixinextras} ({@code BUILTIN}, {@code builtin}).
 *   <li>Entrypoints: the declarations of every Fabric and Quilt mod in mod order, with the Fabric runtime's instances.
 *   <li>{@link #getModContainer(Class)}: the mod whose jar the class's {@link CodeSource} names (the game class loader
 *       gives each jar its own).
 *   <li>Folders: {@code <game>/<loader.configDir, default config>} and {@code <game>/<loader.cacheDir, default
 *       .cache>}, created on request; {@code loader.development} is the development flag, as in Quilt Loader.
 * </ul>
 *
 * <p>Thread: built on the launch thread before any entrypoint runs; reads are safe from any thread.
 */
public final class QuiltLoaderRuntime {

    private static final MappingResolver MAPPING_RESOLVER = new QuiltOfficialMappingResolver();

    private final FabricLoaderRuntime fabric;
    private final String gameVersion;
    private final EnvType environment;
    private final List<QuiltModContainer> all = new ArrayList<>();
    private final Map<String, QuiltModContainer> byId = new LinkedHashMap<>();
    private final Map<String, QuiltModContainer> byOwnId = new HashMap<>();
    private final Map<Path, QuiltModContainer> byJar = new LinkedHashMap<>();
    private Path emptyRoot;

    /** Quilt Loader's internal game description, as far as its API asks for it. */
    private record Game(String version) implements GameProvider {

        @Override
        public String getRawGameVersion() {
            return version;
        }

        @Override
        public String getNormalizedGameVersion() {
            return version;
        }
    }

    /** An entrypoint that could not be created or is not of the requested type. */
    private static final class Failure extends EntrypointException {

        private static final long serialVersionUID = 1L;

        private final String key;

        Failure(String key, String message, Throwable cause) {
            super(message, cause);
            this.key = key;
        }

        @Override
        public String getKey() {
            return key;
        }
    }

    private QuiltLoaderRuntime(FabricLoaderRuntime fabric, BridgeLaunchContext context) {
        this.fabric = fabric;
        this.gameVersion = context.gameVersion();
        this.environment = fabric.getEnvironmentType();
    }

    /**
     * Builds the runtime for {@code context} on top of the Fabric runtime built for the same launch.
     *
     * @param quiltLoaderVersion the version the built-in {@code quilt_loader} reports
     */
    public static QuiltLoaderRuntime create(
            BridgeLaunchContext context, FabricLoaderRuntime fabric, String quiltLoaderVersion) {
        QuiltLogForwarder.install();
        QuiltLoaderRuntime runtime = new QuiltLoaderRuntime(fabric, context);
        ClassLoader classLoader = context.gameClassLoader();
        net.fabricmc.loader.api.metadata.ModMetadata fabricLoader =
                fabric.getModContainer("fabricloader").orElseThrow().getMetadata();
        QuiltModMetadata quiltLoader = QuiltModMetadata.other(
                "builtin",
                new BuiltIn("quilt_loader", "Quilt Loader", quiltLoaderVersion, fabricLoader),
                List.of(),
                List.of());
        runtime.add(new QuiltModContainer(
                quiltLoader, BasicSourceType.BUILTIN, null, null, null, classLoader, runtime::emptyRoot));
        for (String id : List.of("minecraft", "java", "mixinextras")) {
            fabric.getModContainer(id)
                    .ifPresent(builtIn -> runtime.add(new QuiltModContainer(
                            QuiltModMetadata.other("builtin", builtIn.getMetadata(), List.of(), List.of()),
                            BasicSourceType.BUILTIN,
                            id.equals("minecraft") ? context.gameJar() : null,
                            null,
                            null,
                            classLoader,
                            runtime::emptyRoot)));
        }
        List<net.fabricmc.loader.api.ModContainer> fabricViews = fabric.loadedModContainers();
        for (int i = 0; i < context.mods().size(); i++) {
            BridgeLaunchContext.LoadedMod mod = context.mods().get(i);
            Optional<FabricFamilyMod> family = FabricFamilyMod.read(mod);
            net.fabricmc.loader.api.metadata.ModMetadata fabricView =
                    fabricViews.get(i).getMetadata();
            QuiltModMetadata metadata;
            BasicSourceType sourceType;
            if (family.flatMap(FabricFamilyMod::quiltModJson).isPresent()) {
                metadata = QuiltModMetadata.quilt(family.get().quiltModJson().get());
                sourceType = BasicSourceType.NORMAL_QUILT;
            } else if (family.isPresent()) {
                metadata = QuiltModMetadata.other(
                        "loader.fabric",
                        fabricView,
                        family.get().mixinConfigs(),
                        family.get().classTweakers());
                sourceType = BasicSourceType.NORMAL_FABRIC;
            } else {
                metadata = QuiltModMetadata.other(
                        "loader." + fabricView.getType().toLowerCase(Locale.ROOT), fabricView, List.of(), List.of());
                sourceType = BasicSourceType.OTHER;
            }
            runtime.add(new QuiltModContainer(
                    metadata,
                    sourceType,
                    mod.jar(),
                    mod.runByHost() ? null : mod.containerJar(),
                    mod.nestedEntry(),
                    classLoader,
                    runtime::emptyRoot));
        }
        return runtime;
    }

    /** The built-in {@code quilt_loader} as Fabric metadata: it provides {@code fabricloader}, as Quilt Loader does. */
    private record BuiltIn(
            String id, String name, String version, net.fabricmc.loader.api.metadata.ModMetadata fabricLoader)
            implements net.fabricmc.loader.api.metadata.ModMetadata {

        @Override
        public String getType() {
            return "builtin";
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public Collection<String> getProvides() {
            return List.of(fabricLoader.getId());
        }

        @Override
        public net.fabricmc.loader.api.Version getVersion() {
            try {
                return net.fabricmc.loader.api.Version.parse(version);
            } catch (net.fabricmc.loader.api.VersionParsingException e) {
                throw new IllegalStateException("Quilt Loader version " + version + " is not a version", e);
            }
        }

        @Override
        public net.fabricmc.loader.api.metadata.ModEnvironment getEnvironment() {
            return net.fabricmc.loader.api.metadata.ModEnvironment.UNIVERSAL;
        }

        @Override
        public Collection<net.fabricmc.loader.api.metadata.ModDependency> getDependencies() {
            return List.of();
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getDescription() {
            return "";
        }

        @Override
        public Collection<net.fabricmc.loader.api.metadata.Person> getAuthors() {
            return List.of();
        }

        @Override
        public Collection<net.fabricmc.loader.api.metadata.Person> getContributors() {
            return List.of();
        }

        @Override
        public net.fabricmc.loader.api.metadata.ContactInformation getContact() {
            return net.fabricmc.loader.api.metadata.ContactInformation.EMPTY;
        }

        @Override
        public Collection<String> getLicense() {
            return List.of("Apache-2.0");
        }

        @Override
        public Optional<String> getIconPath(int size) {
            return Optional.empty();
        }

        @Override
        public boolean containsCustomValue(String key) {
            return false;
        }

        @Override
        public net.fabricmc.loader.api.metadata.CustomValue getCustomValue(String key) {
            return null;
        }

        @Override
        public Map<String, net.fabricmc.loader.api.metadata.CustomValue> getCustomValues() {
            return Map.of();
        }

        @Override
        @Deprecated
        public boolean containsCustomElement(String key) {
            return false;
        }
    }

    private void add(QuiltModContainer container) {
        all.add(container);
        String id = container.metadata().id();
        byId.putIfAbsent(id, container);
        byOwnId.putIfAbsent(id, container);
        container.metadata().provides().forEach(provided -> byId.putIfAbsent(provided.id(), container));
        if (container.jar() != null) {
            byJar.putIfAbsent(container.jar().toAbsolutePath().normalize(), container);
        }
    }

    /** The empty directory a built-in mod without a jar has as its root, under the cache directory. */
    private synchronized Path emptyRoot() {
        if (emptyRoot == null) {
            Path directory = getCacheDir().resolve("poly-mod-loader").resolve("empty-mod-root");
            try {
                Files.createDirectories(directory);
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot create " + directory, e);
            }
            emptyRoot = directory;
        }
        return emptyRoot;
    }

    /** The class loader of game and mod classes, which entrypoint classes are loaded with. */
    public ClassLoader gameClassLoader() {
        return fabric.gameClassLoader();
    }

    /** {@code QuiltLoader.getEntrypoints}: the instances of {@code key} of every Fabric and Quilt mod, in mod order. */
    public <T> List<T> getEntrypoints(String key, Class<T> type) {
        List<T> instances = new ArrayList<>();
        for (EntrypointContainer<T> container : getEntrypointContainers(key, type)) {
            instances.add(container.getEntrypoint());
        }
        return instances;
    }

    /** {@code QuiltLoader.getEntrypointContainers}: instances are created when first asked for. */
    public <T> List<EntrypointContainer<T>> getEntrypointContainers(String key, Class<T> type) {
        List<EntrypointContainer<T>> containers = new ArrayList<>();
        for (FabricLoaderRuntime.DeclaredEntrypoint declared : fabric.declarations(key)) {
            containers.add(new LazyEntrypoint<>(this, declared, type));
        }
        return containers;
    }

    /** Whether a loaded Fabric or Quilt mod declares {@code key}. */
    public boolean hasEntrypoints(String key) {
        return fabric.hasEntrypoints(key);
    }

    private record LazyEntrypoint<T>(
            QuiltLoaderRuntime runtime, FabricLoaderRuntime.DeclaredEntrypoint declared, Class<T> type)
            implements EntrypointContainer<T> {

        @Override
        public T getEntrypoint() {
            try {
                return runtime.fabric.instance(declared, type);
            } catch (net.fabricmc.loader.api.EntrypointException e) {
                throw new Failure(
                        declared.key(),
                        "Exception while loading entries for entrypoint '" + declared.key() + "' provided by '"
                                + declared.modId() + "'",
                        e.getCause() == null ? e : e.getCause());
            }
        }

        @Override
        public ModContainer getProvider() {
            return runtime.byOwnId.get(declared.modId());
        }

        @Override
        public String getDefinition() {
            return declared.value();
        }
    }

    /**
     * Quilt Loader's {@code invokePreLaunch} part for Quilt mods: calls the {@code pre_launch} entrypoints ({@link
     * PreLaunchEntrypoint#onPreLaunch}) that Quilt mods declare, in mod order. A Fabric mod's {@code pre_launch} is not
     * called, as Fabric Loader does not know the key. Every failure is collected and thrown together.
     *
     * @throws RuntimeException naming the stage, the mod and the declaration, with the further failures suppressed
     */
    public void invokePreLaunch() {
        RuntimeException failure = null;
        for (EntrypointContainer<PreLaunchEntrypoint> container :
                getEntrypointContainers("pre_launch", PreLaunchEntrypoint.class)) {
            if (container.getProvider().getSourceType() != BasicSourceType.NORMAL_QUILT) {
                continue;
            }
            try {
                container.getEntrypoint().onPreLaunch(container.getProvider());
            } catch (RuntimeException | LinkageError e) {
                RuntimeException wrapped = new RuntimeException(
                        String.format(
                                "Could not execute entrypoint stage '%s' due to errors, provided by '%s' at '%s'!",
                                "pre_launch", container.getProvider().metadata().id(), container.getDefinition()),
                        e);
                if (failure == null) {
                    failure = wrapped;
                } else {
                    failure.addSuppressed(wrapped);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    /** {@code QuiltLoader.getMappingResolver}: the {@code official} namespace, every name maps to itself. */
    public MappingResolver getMappingResolver() {
        return MAPPING_RESOLVER;
    }

    /** {@code QuiltLoader.getModContainer}: by mod ID or provided ID. */
    public Optional<ModContainer> getModContainer(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    /** {@code QuiltLoader.getModContainer(Class)}: the mod whose jar defines {@code type}. */
    public Optional<ModContainer> getModContainer(Class<?> type) {
        ProtectionDomain domain = type.getProtectionDomain();
        CodeSource source = domain == null ? null : domain.getCodeSource();
        URL location = source == null ? null : source.getLocation();
        if (location == null || !location.getProtocol().equals("file")) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(
                    byJar.get(Path.of(location.toURI()).toAbsolutePath().normalize()));
        } catch (URISyntaxException | IllegalArgumentException e) {
            // A code source that is not a file path belongs to no mod.
            return Optional.empty();
        }
    }

    /** {@code QuiltLoader.getAllMods}. */
    public Collection<ModContainer> getAllMods() {
        return List.copyOf(all);
    }

    /** {@code QuiltLoader.isModLoaded}: by mod ID or provided ID. */
    public boolean isModLoaded(String id) {
        return byId.containsKey(id);
    }

    /** {@code QuiltLoader.isDevelopmentEnvironment}: the {@code loader.development} property, as in Quilt Loader. */
    public boolean isDevelopmentEnvironment() {
        return Boolean.getBoolean("loader.development");
    }

    /** {@code MinecraftQuiltLoader.getEnvironmentType}: {@code CLIENT} on the client, {@code SERVER} otherwise. */
    public EnvType getEnvironmentType() {
        return environment;
    }

    /** {@code QuiltLoader.getGameInstance}: the Fabric runtime's game object. */
    public Object getGameInstance() {
        return fabric.getGameInstance();
    }

    /** What {@code QuiltLoader.getRawGameVersion} and {@code getNormalizedGameVersion} read. */
    public GameProvider getGameProvider() {
        return new Game(gameVersion);
    }

    /** {@code QuiltLoader.getGameDir}. */
    public Path getGameDir() {
        return fabric.getGameDir();
    }

    /** {@code QuiltLoader.getConfigDir}: {@code <game>/<loader.configDir, default config>}, created if missing. */
    public Path getConfigDir() {
        return QuiltLoaderImpl.ensureDirExists(
                getGameDir().resolve(System.getProperty("loader.configDir", "config")), "config");
    }

    /** {@code QuiltLoader.getCacheDir}: {@code <game>/<loader.cacheDir, default .cache>}, created if missing. */
    public Path getCacheDir() {
        return QuiltLoaderImpl.ensureDirExists(
                getGameDir().resolve(System.getProperty("loader.cacheDir", ".cache")), "cache");
    }

    /** {@code QuiltLoader.getLaunchArguments}: with {@code sanitize}, the access token is left out. */
    public String[] getLaunchArguments(boolean sanitize) {
        return fabric.getLaunchArguments(sanitize);
    }

    /** {@code QuiltLoader.getObjectShare}: the Fabric Loader API's store, as under Quilt Loader. */
    public ObjectShare getObjectShare() {
        return fabric.getObjectShare();
    }

    /** {@code QuiltLoader.createModTable}: a text table of every mod's ID, version and source type. */
    public String createModTable() {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {"Index", "Mod", "ID", "Version", "Type"});
        for (int i = 0; i < all.size(); i++) {
            ModContainer mod = all.get(i);
            rows.add(new String[] {
                String.valueOf(i),
                mod.metadata().name(),
                mod.metadata().id(),
                mod.metadata().version().raw(),
                mod.getSourceType().name()
            });
        }
        int[] widths = new int[5];
        for (String[] row : rows) {
            for (int column = 0; column < row.length; column++) {
                widths[column] = Math.max(widths[column], row[column].length());
            }
        }
        StringBuilder table = new StringBuilder();
        for (String[] row : rows) {
            table.append('|');
            for (int column = 0; column < row.length; column++) {
                table.append(' ')
                        .append(row[column])
                        .append(" ".repeat(widths[column] - row[column].length()))
                        .append(" |");
            }
            table.append('\n');
        }
        return table.toString();
    }
}
