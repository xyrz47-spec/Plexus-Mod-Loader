package org.quiltmc.loader.impl;

import dev.plexusmodloader.compat.quilt.QuiltLoaderRuntime;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.ObjectShare;
import org.quiltmc.loader.api.MappingResolver;
import org.quiltmc.loader.api.ModContainer;
import org.quiltmc.loader.api.entrypoint.EntrypointContainer;
import org.quiltmc.loader.impl.game.GameProvider;

/**
 * Plexus Mod Loader's stand-in for Quilt Loader's internal {@code QuiltLoaderImpl}. Quilt Loader's public API ({@code
 * QuiltLoader}, {@code MinecraftQuiltLoader}, {@code EntrypointUtils}) reads {@code QuiltLoaderImpl.INSTANCE} and calls
 * its methods; this class has that field and those methods, with Quilt Loader 0.31.0-beta.4's signatures, and hands
 * every call to {@link QuiltLoaderRuntime}, which the Fabric bridge attaches before any entrypoint runs (ADR-0027
 * decision 4). It is not Quilt Loader's implementation: mods that use other members of this class are blocked by the
 * link check ({@code PLEXUS-COMPAT-111}).
 */
public final class QuiltLoaderImpl {

    /** The instance Quilt Loader's API reads. */
    public static final QuiltLoaderImpl INSTANCE = new QuiltLoaderImpl();

    private volatile QuiltLoaderRuntime runtime;

    private QuiltLoaderImpl() {}

    /** Called by the Plexus Mod Loader Fabric bridge once, before any Fabric or Quilt entrypoint runs. */
    public void attachPlexusModLoaderRuntime(QuiltLoaderRuntime attached) {
        this.runtime = attached;
    }

    /**
     * The attached runtime.
     *
     * @throws IllegalStateException with Quilt Loader's message when used before the loader is ready
     */
    public QuiltLoaderRuntime plexusModLoaderRuntime() {
        QuiltLoaderRuntime attached = runtime;
        if (attached == null) {
            throw new IllegalStateException("Accessed QuiltLoader too early!");
        }
        return attached;
    }

    public <T> List<T> getEntrypoints(String key, Class<T> type) {
        return plexusModLoaderRuntime().getEntrypoints(key, type);
    }

    public <T> List<EntrypointContainer<T>> getEntrypointContainers(String key, Class<T> type) {
        return plexusModLoaderRuntime().getEntrypointContainers(key, type);
    }

    public boolean hasEntrypoints(String key) {
        return plexusModLoaderRuntime().hasEntrypoints(key);
    }

    public MappingResolver getMappingResolver() {
        return plexusModLoaderRuntime().getMappingResolver();
    }

    public Optional<ModContainer> getModContainer(String id) {
        return plexusModLoaderRuntime().getModContainer(id);
    }

    public Optional<ModContainer> getModContainer(Class<?> type) {
        return plexusModLoaderRuntime().getModContainer(type);
    }

    public Collection<ModContainer> getAllMods() {
        return plexusModLoaderRuntime().getAllMods();
    }

    public boolean isModLoaded(String id) {
        return plexusModLoaderRuntime().isModLoaded(id);
    }

    public boolean isDevelopmentEnvironment() {
        return plexusModLoaderRuntime().isDevelopmentEnvironment();
    }

    public Object getGameInstance() {
        return plexusModLoaderRuntime().getGameInstance();
    }

    public GameProvider getGameProvider() {
        return plexusModLoaderRuntime().getGameProvider();
    }

    public Path getGameDir() {
        return plexusModLoaderRuntime().getGameDir();
    }

    public Path getCacheDir() {
        return plexusModLoaderRuntime().getCacheDir();
    }

    public Path getConfigDir() {
        return plexusModLoaderRuntime().getConfigDir();
    }

    public String[] getLaunchArguments(boolean sanitize) {
        return plexusModLoaderRuntime().getLaunchArguments(sanitize);
    }

    public ObjectShare getObjectShare() {
        return plexusModLoaderRuntime().getObjectShare();
    }

    public String createModTable() {
        return plexusModLoaderRuntime().createModTable();
    }

    public EnvType getEnvironmentType() {
        return plexusModLoaderRuntime().getEnvironmentType();
    }

    /**
     * Creates {@code path} with its parents when it is missing, as Quilt Loader does.
     *
     * @param name what the directory is, for the message
     * @return {@code path}, or {@code null} for {@code null}
     * @throws RuntimeException with Quilt Loader's message when it cannot be created
     */
    public static Path ensureDirExists(Path path, String name) {
        if (path == null) {
            return null;
        }
        if (!Files.exists(path)) {
            try {
                Files.createDirectories(path);
            } catch (IOException e) {
                throw new RuntimeException(String.format("Failed to create %s directory at '%s'", name, path), e);
            }
        }
        return path;
    }
}
