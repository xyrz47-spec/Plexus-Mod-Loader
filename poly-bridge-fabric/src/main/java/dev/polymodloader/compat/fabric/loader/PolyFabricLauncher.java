package dev.polymodloader.compat.fabric.loader;

import dev.polymodloader.api.runtime.GameSide;
import dev.polymodloader.compat.bridge.GameClassBytes;
import dev.polymodloader.compat.bridge.GameClassPath;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;

/**
 * Fabric Loader's {@code FabricLauncher} answered by Poly Mod Loader's game class loader, for mods that use Fabric Loader's
 * internal launcher ({@code FabricLauncherBase.getLauncher()}).
 *
 * <p>Thread: any, like the game class loader.
 */
public final class PolyFabricLauncher extends FabricLauncherBase {

    private final ClassLoader gameClassLoader;
    private final EnvType environment;

    private PolyFabricLauncher(ClassLoader gameClassLoader, EnvType environment) {
        this.gameClassLoader = gameClassLoader;
        this.environment = environment;
    }

    /** Makes {@code FabricLauncherBase.getLauncher()} return a launcher on {@code gameClassLoader}. */
    public static void install(ClassLoader gameClassLoader, GameSide side) {
        setLauncher(new PolyFabricLauncher(gameClassLoader, side == GameSide.CLIENT ? EnvType.CLIENT : EnvType.SERVER));
    }

    @Override
    public void addToClassPath(Path path, String... allowedPrefixes) {
        if (!(gameClassLoader instanceof GameClassPath classPath)) {
            throw new UnsupportedOperationException("This game class loader cannot take more jars");
        }
        try {
            classPath.addJar(path);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot add " + path + " to the game class path", e);
        }
    }

    @Override
    public void setAllowedPrefixes(Path path, String... prefixes) {}

    @Override
    public void setValidParentClassPath(Collection<Path> paths) {}

    @Override
    public EnvType getEnvironmentType() {
        return environment;
    }

    @Override
    public boolean isClassLoaded(String name) {
        return gameClassLoader instanceof GameClassBytes classes && classes.isDefined(name);
    }

    @Override
    public Class<?> loadIntoTarget(String name) throws ClassNotFoundException {
        return gameClassLoader.loadClass(name);
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        return gameClassLoader.getResourceAsStream(name);
    }

    @Override
    public ClassLoader getTargetClassLoader() {
        return gameClassLoader;
    }

    @Override
    public byte[] getClassByteArray(String name, boolean runTransformers) throws IOException {
        if (gameClassLoader instanceof GameClassBytes classes) {
            return classes.classBytes(name.replace('.', '/')).orElse(null);
        }
        try (InputStream input = gameClassLoader.getResourceAsStream(name.replace('.', '/') + ".class")) {
            return input == null ? null : input.readAllBytes();
        }
    }

    @Override
    public Manifest getManifest(Path originPath) {
        if (!Files.isRegularFile(originPath)) {
            return null;
        }
        try (JarFile jar = new JarFile(originPath.toFile())) {
            return jar.getManifest();
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public List<Path> getClassPath() {
        return gameClassLoader instanceof GameClassPath classPath ? classPath.jars() : List.of();
    }
}
