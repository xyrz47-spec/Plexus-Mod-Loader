package net.fabricmc.loader.impl;

import dev.plexusmodloader.compat.fabric.loader.FabricLoaderRuntime;
import dev.plexusmodloader.compat.fabric.loader.PlexusGameProvider;
import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.ObjectShare;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.fabricmc.loader.impl.game.GameProvider;

/**
 * Plexus Mod Loader's stand-in for Fabric Loader's internal {@code FabricLoaderImpl}. Fabric Loader's public API reads
 * {@code FabricLoaderImpl.INSTANCE} in {@code FabricLoader.getInstance()}; this class has that field and hands
 * every call to {@link FabricLoaderRuntime}, which the Fabric bridge attaches before any entrypoint runs (ADR-0016).
 * It is not Fabric Loader's implementation, and nothing but the field, the {@link FabricLoader} methods and {@link
 * #getGameProvider()} is provided: mods that use other members of this class are blocked by the link check.
 */
public final class FabricLoaderImpl implements FabricLoader {

    /** The instance {@code FabricLoader.getInstance()} returns. */
    public static final FabricLoaderImpl INSTANCE = new FabricLoaderImpl();

    private volatile FabricLoaderRuntime runtime;

    private FabricLoaderImpl() {}

    /** Called by the Plexus Mod Loader Fabric bridge once, before any Fabric entrypoint runs. */
    public void attachPlexusModLoaderRuntime(FabricLoaderRuntime attached) {
        this.runtime = attached;
    }

    /** The attached runtime; Fabric's message when used before the loader is ready. */
    public FabricLoaderRuntime plexusModLoaderRuntime() {
        FabricLoaderRuntime attached = runtime;
        if (attached == null) {
            throw new RuntimeException("Accessed FabricLoader too early!");
        }
        return attached;
    }

    @Override
    public <T> List<T> getEntrypoints(String key, Class<T> type) {
        return plexusModLoaderRuntime().getEntrypoints(key, type);
    }

    @Override
    public <T> List<EntrypointContainer<T>> getEntrypointContainers(String key, Class<T> type) {
        return plexusModLoaderRuntime().getEntrypointContainers(key, type);
    }

    @Override
    public <T> void invokeEntrypoints(String key, Class<T> type, Consumer<? super T> invoker) {
        plexusModLoaderRuntime().invokeEntrypoints(key, type, invoker);
    }

    @Override
    public ObjectShare getObjectShare() {
        return plexusModLoaderRuntime().getObjectShare();
    }

    /** Minecraft 26.x is not obfuscated: the {@code official} namespace, every name maps to itself. */
    @Override
    public MappingResolver getMappingResolver() {
        return plexusModLoaderRuntime().getMappingResolver();
    }

    @Override
    public Optional<ModContainer> getModContainer(String id) {
        return plexusModLoaderRuntime().getModContainer(id);
    }

    @Override
    public Collection<ModContainer> getAllMods() {
        return plexusModLoaderRuntime().getAllMods();
    }

    @Override
    public boolean isModLoaded(String id) {
        return plexusModLoaderRuntime().isModLoaded(id);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return plexusModLoaderRuntime().isDevelopmentEnvironment();
    }

    @Override
    public EnvType getEnvironmentType() {
        return plexusModLoaderRuntime().getEnvironmentType();
    }

    @Override
    public String getRawGameVersion() {
        return plexusModLoaderRuntime().getRawGameVersion();
    }

    /** Fabric Loader's description of the running game; only its descriptive methods exist (see {@link GameProvider}). */
    public GameProvider getGameProvider() {
        return new PlexusGameProvider(getRawGameVersion(), getGameDir());
    }

    /** As {@link #getGameProvider()}; Plexus Mod Loader always has a game when Fabric code runs. */
    public GameProvider tryGetGameProvider() {
        return runtime == null ? null : getGameProvider();
    }

    @Override
    @Deprecated
    public Object getGameInstance() {
        return plexusModLoaderRuntime().getGameInstance();
    }

    @Override
    public Path getGameDir() {
        return plexusModLoaderRuntime().getGameDir();
    }

    @Override
    @Deprecated
    public File getGameDirectory() {
        return getGameDir().toFile();
    }

    @Override
    public Path getConfigDir() {
        return plexusModLoaderRuntime().getConfigDir();
    }

    @Override
    @Deprecated
    public File getConfigDirectory() {
        return getConfigDir().toFile();
    }

    @Override
    public String[] getLaunchArguments(boolean sanitize) {
        return plexusModLoaderRuntime().getLaunchArguments(sanitize);
    }
}
