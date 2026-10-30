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
 * Poly Mod Loader's stand-in for Fabric Loader's internal {@code FabricLoaderImpl}. Fabric Loader's public API reads
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

    /** Called by the Poly Mod Loader Fabric bridge once, before any Fabric entrypoint runs. */
    public void attachPolyModLoaderRuntime(FabricLoaderRuntime attached) {
        this.runtime = attached;
    }

    /** The attached runtime; Fabric's message when used before the loader is ready. */
    public FabricLoaderRuntime polyModLoaderRuntime() {
        FabricLoaderRuntime attached = runtime;
        if (attached == null) {
            throw new RuntimeException("Accessed FabricLoader too early!");
        }
        return attached;
    }

    @Override
    public <T> List<T> getEntrypoints(String key, Class<T> type) {
        return polyModLoaderRuntime().getEntrypoints(key, type);
    }

    @Override
    public <T> List<EntrypointContainer<T>> getEntrypointContainers(String key, Class<T> type) {
        return polyModLoaderRuntime().getEntrypointContainers(key, type);
    }

    @Override
    public <T> void invokeEntrypoints(String key, Class<T> type, Consumer<? super T> invoker) {
        polyModLoaderRuntime().invokeEntrypoints(key, type, invoker);
    }

    @Override
    public ObjectShare getObjectShare() {
        return polyModLoaderRuntime().getObjectShare();
    }

    /** Minecraft 26.x is not obfuscated: the {@code official} namespace, every name maps to itself. */
    @Override
    public MappingResolver getMappingResolver() {
        return polyModLoaderRuntime().getMappingResolver();
    }

    @Override
    public Optional<ModContainer> getModContainer(String id) {
        return polyModLoaderRuntime().getModContainer(id);
    }

    @Override
    public Collection<ModContainer> getAllMods() {
        return polyModLoaderRuntime().getAllMods();
    }

    @Override
    public boolean isModLoaded(String id) {
        return polyModLoaderRuntime().isModLoaded(id);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return polyModLoaderRuntime().isDevelopmentEnvironment();
    }

    @Override
    public EnvType getEnvironmentType() {
        return polyModLoaderRuntime().getEnvironmentType();
    }

    @Override
    public String getRawGameVersion() {
        return polyModLoaderRuntime().getRawGameVersion();
    }

    /** Fabric Loader's description of the running game; only its descriptive methods exist (see {@link GameProvider}). */
    public GameProvider getGameProvider() {
        return new PlexusGameProvider(getRawGameVersion(), getGameDir());
    }

    /** As {@link #getGameProvider()}; Poly Mod Loader always has a game when Fabric code runs. */
    public GameProvider tryGetGameProvider() {
        return runtime == null ? null : getGameProvider();
    }

    @Override
    @Deprecated
    public Object getGameInstance() {
        return polyModLoaderRuntime().getGameInstance();
    }

    @Override
    public Path getGameDir() {
        return polyModLoaderRuntime().getGameDir();
    }

    @Override
    @Deprecated
    public File getGameDirectory() {
        return getGameDir().toFile();
    }

    @Override
    public Path getConfigDir() {
        return polyModLoaderRuntime().getConfigDir();
    }

    @Override
    @Deprecated
    public File getConfigDirectory() {
        return getConfigDir().toFile();
    }

    @Override
    public String[] getLaunchArguments(boolean sanitize) {
        return polyModLoaderRuntime().getLaunchArguments(sanitize);
    }
}
