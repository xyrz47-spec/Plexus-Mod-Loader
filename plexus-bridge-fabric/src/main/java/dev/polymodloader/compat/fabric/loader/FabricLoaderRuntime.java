package dev.polymodloader.compat.fabric.loader;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.polymodloader.api.runtime.GameSide;
import dev.polymodloader.compat.bridge.BridgeLaunchContext;
import dev.polymodloader.compat.bridge.BridgedEntrypoint;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.EntrypointException;
import net.fabricmc.loader.api.LanguageAdapter;
import net.fabricmc.loader.api.LanguageAdapterException;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.ObjectShare;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.fabricmc.loader.impl.util.DefaultLanguageAdapter;

/**
 * Poly Mod Loader's implementation of Fabric Loader's runtime API ({@code FabricLoader.getInstance()}), built from
 * the mods Poly Mod Loader decided to load (ADR-0016). Fabric mods see every loaded mod, of every loader, as a
 * {@link ModContainer}, plus the built-in {@code minecraft}, {@code java}, {@code fabricloader} and
 * {@code mixinextras}. A Quilt mod's metadata follows Quilt Loader's rules for Fabric's view ({@code getType()} is
 * {@code quilt}, ADR-0027).
 *
 * <p>Fabric and Quilt mods share one entrypoint store, as under Quilt Loader: {@code FabricLoader.getEntrypoints} and
 * {@code QuiltLoader.getEntrypoints} give the same instances ({@link #declarations}, {@link #instance}).
 *
 * <p>Entrypoints of any key ({@code main}, {@code client}, {@code modmenu}, ...) are created on request through
 * the declared language adapter, once per mod and declaration; {@code main}/{@code client}/{@code server}
 * instances are the ones Poly Mod Loader calls, as in Fabric. The mapping resolver is Fabric Loader's without mappings
 * ({@link #getMappingResolver()}). {@code getGameInstance} comes from the version adapter through the launch context
 * ({@link #getGameInstance()}).
 *
 * <p>Thread: built on the launch thread before any entrypoint runs; reads are safe from any thread.
 */
public final class FabricLoaderRuntime {

    private static final OfficialMappingResolver MAPPING_RESOLVER = new OfficialMappingResolver();

    private final GameSide side;
    private final String gameVersion;
    private final Path gameDirectory;
    private final List<String> launchArguments;
    private final ClassLoader gameClassLoader;
    private final java.util.function.Supplier<Object> gameInstance;
    private final Map<String, FabricModContainer> byId = new LinkedHashMap<>();
    private final List<FabricModContainer> all = new ArrayList<>();
    /** The containers of the launch's mods, in its order ({@link BridgeLaunchContext#mods()}). */
    private final List<FabricModContainer> loaded = new ArrayList<>();
    /** Fabric and Quilt mods in entrypoint order, with their declared entrypoints: one store for both (ADR-0027). */
    private final List<Declarations> familyMods = new ArrayList<>();

    private final Map<String, String> adapterClasses = new HashMap<>();
    private final Map<String, LanguageAdapter> adapters = new ConcurrentHashMap<>();
    private final Map<String, Object> instances = new ConcurrentHashMap<>();
    private final FabricObjectShare objectShare = new FabricObjectShare();

    private record Declarations(
            FabricModContainer mod, String loader, Map<String, List<FabricFamilyMod.Entrypoint>> entrypoints) {}

    /**
     * One entrypoint declaration in the store that Fabric and Quilt mods share, for the Quilt Loader API ({@code
     * QuiltLoader.getEntrypointContainers}): its instance is the one {@code FabricLoader.getEntrypoints} returns.
     *
     * @param key the entrypoint key ({@code main}, {@code pre_launch}, {@code modmenu}, ...)
     * @param modId the declaring mod
     * @param loader {@code Fabric} or {@code Quilt}
     * @param value the declared value
     * @param adapter its language adapter
     */
    public record DeclaredEntrypoint(String key, String modId, String loader, String value, String adapter) {}

    private FabricLoaderRuntime(BridgeLaunchContext context) {
        this.side = context.side();
        this.gameVersion = context.gameVersion();
        this.gameDirectory = context.gameDirectory().toAbsolutePath();
        this.launchArguments = context.launchArguments();
        this.gameClassLoader = context.gameClassLoader();
        this.gameInstance = context.gameInstance();
    }

    /**
     * Builds the runtime for {@code context}.
     *
     * @param fabricLoaderVersion the version the built-in {@code fabricloader} mod reports
     */
    public static FabricLoaderRuntime create(BridgeLaunchContext context, String fabricLoaderVersion) {
        return create(context, fabricLoaderVersion, Map.of());
    }

    /**
     * Builds the runtime for {@code context}, with further built-in mods such as {@code mixinextras}, which the
     * bridge provides on Poly Mod Loader's class path as Fabric Loader bundles it.
     *
     * @param fabricLoaderVersion the version the built-in {@code fabricloader} mod reports
     * @param builtInLibraries ID of each further built-in mod to its version
     */
    public static FabricLoaderRuntime create(
            BridgeLaunchContext context, String fabricLoaderVersion, Map<String, String> builtInLibraries) {
        FabricLoaderRuntime runtime = new FabricLoaderRuntime(context);
        builtInLibraries.forEach((id, version) -> runtime.add(new FabricModContainer(
                FabricModMetadata.other("builtin", id, version, id, List.of()),
                null,
                FabricModContainer.Origin.unknown())));
        runtime.add(new FabricModContainer(
                FabricModMetadata.other("builtin", "minecraft", context.gameVersion(), "Minecraft", List.of()),
                context.gameJar(),
                FabricModContainer.Origin.unknown()));
        String java = System.getProperty("java.specification.version");
        runtime.add(new FabricModContainer(
                FabricModMetadata.other("builtin", "java", java, "Java " + java, List.of()),
                null,
                FabricModContainer.Origin.unknown()));
        runtime.add(new FabricModContainer(
                FabricModMetadata.other("builtin", "fabricloader", fabricLoaderVersion, "Fabric Loader", List.of()),
                null,
                FabricModContainer.Origin.unknown()));
        // Metadata first: a nested mod usually comes before the mod it is nested in (it runs first), and its
        // origin names that mod.
        List<Optional<FabricFamilyMod>> family = new ArrayList<>();
        List<FabricModMetadata> metadata = new ArrayList<>();
        Map<Path, String> idByJar = new HashMap<>();
        for (BridgeLaunchContext.LoadedMod mod : context.mods()) {
            Optional<FabricFamilyMod> familyMod = FabricFamilyMod.read(mod);
            family.add(familyMod);
            metadata.add(familyMod
                    .map(read -> read.quiltModJson()
                            .map(FabricModMetadata::quilt)
                            .orElseGet(() -> FabricModMetadata.fabric(
                                    read.fabricModJson().orElseThrow())))
                    .orElseGet(() -> FabricModMetadata.other(
                            mod.loader().equals("Poly Mod Loader")
                                    ? "poly"
                                    : mod.loader().toLowerCase(Locale.ROOT),
                            mod.modId(),
                            mod.version(),
                            mod.name(),
                            mod.providedIds())));
            idByJar.putIfAbsent(mod.jar(), mod.modId());
        }
        Map<Path, FabricModContainer> byJar = new HashMap<>();
        List<FabricModContainer> containers = new ArrayList<>();
        for (int i = 0; i < context.mods().size(); i++) {
            BridgeLaunchContext.LoadedMod mod = context.mods().get(i);
            FabricModContainer container = new FabricModContainer(
                    metadata.get(i),
                    // A mod the host loader runs (Forge, ADR-0025) has no root paths here: the host serves its
                    // resources, and Fabric API's resource loader would make a second pack of every root path.
                    mod.runByHost() ? null : mod.jar(),
                    mod.containerJar() == null
                            ? FabricModContainer.Origin.path(mod.jar())
                            : FabricModContainer.Origin.nested(idByJar.get(mod.containerJar()), mod.nestedEntry()),
                    // Its own files can still be found (ADR-0026 36); a nested host mod's are inside its outer jar.
                    mod.runByHost() && mod.containerJar() == null ? mod.jar() : null);
            runtime.add(container);
            runtime.loaded.add(container);
            containers.add(container);
            byJar.putIfAbsent(mod.jar(), container);
            family.get(i).ifPresent(familyMod -> {
                runtime.familyMods.add(new Declarations(container, mod.loader(), familyMod.entrypoints()));
                familyMod.languageAdapters().forEach(runtime.adapterClasses::putIfAbsent);
            });
        }
        for (int i = 0; i < containers.size(); i++) {
            Path containerJar = context.mods().get(i).containerJar();
            if (containerJar != null && byJar.containsKey(containerJar)) {
                containers.get(i).containedIn(byJar.get(containerJar));
            }
        }
        return runtime;
    }

    private void add(FabricModContainer container) {
        all.add(container);
        byId.putIfAbsent(container.getMetadata().getId(), container);
        container.getMetadata().getProvides().forEach(id -> byId.putIfAbsent(id, container));
    }

    /**
     * Reads {@code fabric.mod.json} from a mod jar the bridge has already assessed.
     *
     * @return its root object, or {@code null} when the jar has none or it cannot be read again
     */
    public static JsonObject readFabricModJson(Path jar) {
        try (JarFile file = new JarFile(jar.toFile())) {
            ZipEntry entry = file.getEntry("fabric.mod.json");
            if (entry == null) {
                return null;
            }
            try (InputStream input = file.getInputStream(entry)) {
                JsonElement json = JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8));
                return json.isJsonObject() ? json.getAsJsonObject() : null;
            }
        } catch (IOException | JsonParseException e) {
            // The bridge assessed this jar already; a jar that cannot be read again gives no metadata here.
            return null;
        }
    }

    /**
     * The containers of the launch's mods, in the launch's order ({@link BridgeLaunchContext#mods()}), without the
     * built-in ones. The Quilt Loader API builds its view of each mod from these.
     */
    public List<ModContainer> loadedModContainers() {
        return List.copyOf(loaded);
    }

    /** The class loader of game and mod classes, which entrypoint classes are loaded with. */
    public ClassLoader gameClassLoader() {
        return gameClassLoader;
    }

    /**
     * Creates a mod's {@code main}, {@code client} or {@code server} entrypoint ({@code common}, {@code client},
     * {@code server} phase) with its language adapter and calls it through Fabric's initializer interface. Both
     * Fabric-family bridges call this, so Fabric and Quilt mods share one entrypoint store (ADR-0027).
     */
    public void invokeModEntrypoint(BridgedEntrypoint entrypoint) {
        switch (entrypoint.phase()) {
            case "common" ->
                entrypointInstance("main", entrypoint.className(), entrypoint.adapter(), ModInitializer.class)
                        .onInitialize();
            case "client" ->
                entrypointInstance("client", entrypoint.className(), entrypoint.adapter(), ClientModInitializer.class)
                        .onInitializeClient();
            case "server" ->
                entrypointInstance(
                                "server",
                                entrypoint.className(),
                                entrypoint.adapter(),
                                DedicatedServerModInitializer.class)
                        .onInitializeServer();
            default -> throw new IllegalArgumentException("Unknown entrypoint phase " + entrypoint.phase());
        }
    }

    /**
     * The instance Poly Mod Loader calls for one of the mod entrypoints it runs ({@code main}, {@code client},
     * {@code server}): the same instance {@link #getEntrypoints} returns for that key.
     *
     * @throws EntrypointException if no loaded Fabric or Quilt mod declares it or it cannot be created
     */
    // EntrypointException(key, modId, cause) is Fabric Loader's internal constructor; this class is that
    // internal part, and the constructor is the only one that records the key.
    @SuppressWarnings("deprecation")
    public <T> T entrypointInstance(String key, String value, String adapter, Class<T> type) {
        for (Declarations mod : familyMods) {
            for (FabricFamilyMod.Entrypoint declaration : mod.entrypoints().getOrDefault(key, List.of())) {
                if (declaration.value().equals(value) && declaration.adapter().equals(adapter)) {
                    return create(key, mod.mod(), declaration, type);
                }
            }
        }
        throw new EntrypointException(
                "no loaded Fabric or Quilt mod declares the " + key + " entrypoint " + value, (Throwable) null);
    }

    /** The declarations of {@code key} of every loaded Fabric and Quilt mod, in mod order. */
    public List<DeclaredEntrypoint> declarations(String key) {
        List<DeclaredEntrypoint> result = new ArrayList<>();
        for (Declarations mod : familyMods) {
            for (FabricFamilyMod.Entrypoint declaration : mod.entrypoints().getOrDefault(key, List.of())) {
                result.add(new DeclaredEntrypoint(
                        key,
                        mod.mod().getMetadata().getId(),
                        mod.loader(),
                        declaration.value(),
                        declaration.adapter()));
            }
        }
        return result;
    }

    /** Whether a loaded Fabric or Quilt mod declares {@code key}. */
    public boolean hasEntrypoints(String key) {
        return familyMods.stream().anyMatch(mod -> mod.entrypoints().containsKey(key));
    }

    /**
     * The instance of {@code declared}: created through its language adapter the first time it is asked for, then the
     * same instance for both loaders' APIs.
     *
     * @throws EntrypointException if it cannot be created or is not a {@code type}
     */
    // EntrypointException(key, modId, cause) is Fabric Loader's internal constructor; this class is that
    // internal part, and the constructor is the only one that records the key.
    @SuppressWarnings("deprecation")
    public <T> T instance(DeclaredEntrypoint declared, Class<T> type) {
        for (Declarations mod : familyMods) {
            if (mod.mod().getMetadata().getId().equals(declared.modId())) {
                return create(
                        declared.key(),
                        mod.mod(),
                        new FabricFamilyMod.Entrypoint(declared.value(), declared.adapter()),
                        type);
            }
        }
        throw new EntrypointException(declared.key(), declared.modId(), new IllegalStateException("not loaded"));
    }

    // EntrypointException(key, modId, cause) is Fabric Loader's internal constructor; this class is that
    // internal part, and the constructor is the only one that records the key.
    @SuppressWarnings("deprecation")
    private <T> T create(String key, FabricModContainer mod, FabricFamilyMod.Entrypoint declaration, Class<T> type) {
        String cacheKey = mod.getMetadata().getId() + "|" + declaration.adapter() + "|" + declaration.value();
        Object existing = instances.get(cacheKey);
        if (existing != null) {
            if (!type.isInstance(existing)) {
                throw new EntrypointException(
                        key,
                        mod.getMetadata().getId(),
                        new LanguageAdapterException("Class "
                                + existing.getClass().getName() + " cannot be cast to " + type.getName() + "!"));
            }
            return type.cast(existing);
        }
        try {
            T created = adapter(declaration.adapter()).create(mod, declaration.value(), type);
            Object raced = instances.putIfAbsent(cacheKey, created);
            return raced == null ? created : type.cast(raced);
        } catch (LanguageAdapterException | RuntimeException | LinkageError e) {
            throw new EntrypointException(key, mod.getMetadata().getId(), e);
        }
    }

    private LanguageAdapter adapter(String name) throws LanguageAdapterException {
        if (name.equals("default")) {
            return DefaultLanguageAdapter.INSTANCE;
        }
        String className = adapterClasses.get(name);
        if (className == null) {
            throw new LanguageAdapterException("no loaded mod provides the language adapter '" + name + "'");
        }
        LanguageAdapter existing = adapters.get(name);
        if (existing != null) {
            return existing;
        }
        try {
            LanguageAdapter adapter = (LanguageAdapter) Class.forName(className, true, gameClassLoader)
                    .getDeclaredConstructor()
                    .newInstance();
            LanguageAdapter raced = adapters.putIfAbsent(name, adapter);
            return raced == null ? adapter : raced;
        } catch (ReflectiveOperationException | ClassCastException | LinkageError e) {
            throw new LanguageAdapterException("cannot create language adapter '" + name + "' (" + className + ")", e);
        }
    }

    /**
     * {@code FabricLoader.getEntrypoints}: the instances of {@code key} of every loaded Fabric and Quilt mod, in mod
     * order (Quilt Loader keeps one store for both, ADR-0027).
     */
    public <T> List<T> getEntrypoints(String key, Class<T> type) {
        List<T> result = new ArrayList<>();
        for (EntrypointContainer<T> container : getEntrypointContainers(key, type)) {
            result.add(container.getEntrypoint());
        }
        return result;
    }

    /** {@code FabricLoader.getEntrypointContainers}: instances are created when first asked for. */
    public <T> List<EntrypointContainer<T>> getEntrypointContainers(String key, Class<T> type) {
        List<EntrypointContainer<T>> result = new ArrayList<>();
        for (Declarations mod : familyMods) {
            for (FabricFamilyMod.Entrypoint declaration : mod.entrypoints().getOrDefault(key, List.of())) {
                result.add(new LazyEntrypoint<>(this, key, mod.mod(), declaration, type));
            }
        }
        return result;
    }

    /** {@code FabricLoader.invokeEntrypoints}: every failure is collected and thrown together at the end. */
    // EntrypointException(key, modId, cause) is Fabric Loader's internal constructor; this class is that
    // internal part, and the constructor is the only one that records the key.
    @SuppressWarnings("deprecation")
    public <T> void invokeEntrypoints(String key, Class<T> type, Consumer<? super T> invoker) {
        EntrypointException failure = null;
        for (EntrypointContainer<T> container : getEntrypointContainers(key, type)) {
            try {
                invoker.accept(container.getEntrypoint());
            } catch (RuntimeException | LinkageError e) {
                if (failure == null) {
                    failure = new EntrypointException(
                            key, container.getProvider().getMetadata().getId(), e);
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private record LazyEntrypoint<T>(
            FabricLoaderRuntime runtime,
            String key,
            FabricModContainer mod,
            FabricFamilyMod.Entrypoint declaration,
            Class<T> type)
            implements EntrypointContainer<T> {

        @Override
        public T getEntrypoint() {
            return runtime.create(key, mod, declaration, type);
        }

        @Override
        public ModContainer getProvider() {
            return mod;
        }

        @Override
        public String getDefinition() {
            return declaration.value();
        }
    }

    /** Fabric Loader's resolver without mappings: the {@code official} namespace, names map to themselves. */
    public net.fabricmc.loader.api.MappingResolver getMappingResolver() {
        return MAPPING_RESOLVER;
    }

    /** {@code FabricLoader.getObjectShare}. */
    public ObjectShare getObjectShare() {
        return objectShare;
    }

    /** {@code FabricLoader.getModContainer}: by mod ID or provided ID. */
    public Optional<ModContainer> getModContainer(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    /** {@code FabricLoader.getAllMods}. */
    public Collection<ModContainer> getAllMods() {
        return List.copyOf(all);
    }

    /** {@code FabricLoader.isModLoaded}: by mod ID or provided ID. */
    public boolean isModLoaded(String id) {
        return byId.containsKey(id);
    }

    /** {@code FabricLoader.isDevelopmentEnvironment}: the {@code fabric.development} property, as in Fabric. */
    public boolean isDevelopmentEnvironment() {
        return Boolean.getBoolean("fabric.development");
    }

    /** {@code FabricLoader.getEnvironmentType}. */
    public EnvType getEnvironmentType() {
        return side == GameSide.CLIENT ? EnvType.CLIENT : EnvType.SERVER;
    }

    /**
     * {@code FabricLoader.getGameInstance}: the client's {@code Minecraft} object, or the dedicated server once it
     * runs; {@code null} before then, as in Fabric Loader (on a server it is still {@code null} during
     * {@code onInitialize}).
     */
    public Object getGameInstance() {
        return gameInstance.get();
    }

    /** {@code FabricLoader.getRawGameVersion}. */
    public String getRawGameVersion() {
        return gameVersion;
    }

    /** {@code FabricLoader.getGameDir}. */
    public Path getGameDir() {
        return gameDirectory;
    }

    /** {@code FabricLoader.getConfigDir}: {@code <game directory>/config}, created if missing. */
    public Path getConfigDir() {
        Path config = gameDirectory.resolve("config");
        try {
            Files.createDirectories(config);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create " + config, e);
        }
        return config;
    }

    /** {@code FabricLoader.getLaunchArguments}: with {@code sanitize}, the access token is left out. */
    public String[] getLaunchArguments(boolean sanitize) {
        List<String> arguments = new ArrayList<>();
        for (int i = 0; i < launchArguments.size(); i++) {
            String argument = launchArguments.get(i);
            if (sanitize && argument.equals("--accessToken")) {
                i++;
                continue;
            }
            arguments.add(argument);
        }
        return arguments.toArray(String[]::new);
    }
}
