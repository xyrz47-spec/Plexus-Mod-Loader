package net.fabricmc.loader.impl.launch;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.jar.Manifest;
import net.fabricmc.api.EnvType;

/**
 * Plexus Mod Loader's stand-in for Fabric Loader's internal {@code FabricLauncher}, which some mods use through {@link
 * FabricLauncherBase#getLauncher()} (adding a jar they unpacked to the class path, reading class bytes). The methods
 * have Fabric Loader 0.19.5's signatures; Plexus Mod Loader's game class loader answers them. Two are left out, so the link
 * check blocks mods that call them: {@code getMappingConfiguration} (Plexus Mod Loader runs unobfuscated Minecraft and has no
 * Fabric mapping configuration) and {@code getEntrypoint} (Plexus Mod Loader starts the game itself).
 */
public interface FabricLauncher {

    /** Adds {@code path} to the game class path; {@code allowedPrefixes} is not enforced. */
    void addToClassPath(Path path, String... allowedPrefixes);

    /** Accepted and ignored: Plexus Mod Loader does not restrict which classes a jar may provide. */
    void setAllowedPrefixes(Path path, String... prefixes);

    /** Accepted and ignored: Plexus Mod Loader decides itself which libraries the game class loader shares. */
    void setValidParentClassPath(Collection<Path> paths);

    EnvType getEnvironmentType();

    boolean isClassLoaded(String name);

    Class<?> loadIntoTarget(String name) throws ClassNotFoundException;

    InputStream getResourceAsStream(String name);

    ClassLoader getTargetClassLoader();

    /** The class file as the game class loader reads it, or {@code null} when there is none. */
    byte[] getClassByteArray(String name, boolean runTransformers) throws IOException;

    Manifest getManifest(Path originPath);

    boolean isDevelopment();

    List<Path> getClassPath();
}
