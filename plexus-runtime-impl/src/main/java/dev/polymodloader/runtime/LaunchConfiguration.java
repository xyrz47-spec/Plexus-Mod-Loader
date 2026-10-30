package dev.polymodloader.runtime;

import dev.polymodloader.api.runtime.GameSide;
import dev.polymodloader.compat.bridge.JdkModuleAccess;
import dev.polymodloader.compat.bridge.SystemClassPath;
import dev.polymodloader.game.version.GameLaunchTarget;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Inputs to one Poly Mod Loader launch.
 *
 * @param target which Minecraft program to start
 * @param gameJar the Minecraft game jar; loaded through Poly Mod Loader's game class loader, never from the JVM class path
 * @param modsDirectory user-managed folder scanned for mod jars
 * @param devModJars extra mod jars, e.g. mods built in the current Gradle build
 * @param transformDumpDirectory when present, before/after class files of every transformation are written here
 * @param reportDirectory where {@code startup-report.json} and {@code transformation-report.json} are written
 * @param transformCacheDirectory when present, transformed classes are cached there across launches
 * @param transformCacheMaxBytes the most bytes the transform cache may take; least recently used
 *     entries are removed beyond it
 * @param systemClassPath appends to the JVM's class path, when the launcher was started with Poly Mod Loader's Java
 *     agent (ADR-0024); only a loader that needs it (Forge's file systems) uses it
 * @param jdkModuleAccess opens JDK packages to modules Poly Mod Loader defines, when the launcher was started with
 *     Poly Mod Loader's Java agent; only a loader that needs it (NeoForge's FML, ADR-0026) uses it
 */
public record LaunchConfiguration(
        GameLaunchTarget target,
        Path gameJar,
        Path modsDirectory,
        List<Path> devModJars,
        Optional<Path> transformDumpDirectory,
        Path reportDirectory,
        Optional<Path> transformCacheDirectory,
        long transformCacheMaxBytes,
        Optional<SystemClassPath> systemClassPath,
        Optional<JdkModuleAccess> jdkModuleAccess) {

    /** The default transform cache size limit, 256 MiB. */
    public static final long DEFAULT_TRANSFORM_CACHE_MAX_BYTES =
            dev.polymodloader.internal.transform.engine.DirectoryTransformCache.DEFAULT_MAX_BYTES;

    public LaunchConfiguration {
        devModJars = List.copyOf(devModJars);
        if (transformCacheMaxBytes <= 0) {
            throw new IllegalArgumentException(
                    "the transform cache size limit must be positive, got " + transformCacheMaxBytes);
        }
        Objects.requireNonNull(systemClassPath, "systemClassPath");
        Objects.requireNonNull(jdkModuleAccess, "jdkModuleAccess");
    }

    /** A configuration whose JDK modules cannot be opened to modules Poly Mod Loader defines. */
    public LaunchConfiguration(
            GameLaunchTarget target,
            Path gameJar,
            Path modsDirectory,
            List<Path> devModJars,
            Optional<Path> transformDumpDirectory,
            Path reportDirectory,
            Optional<Path> transformCacheDirectory,
            long transformCacheMaxBytes,
            Optional<SystemClassPath> systemClassPath) {
        this(
                target,
                gameJar,
                modsDirectory,
                devModJars,
                transformDumpDirectory,
                reportDirectory,
                transformCacheDirectory,
                transformCacheMaxBytes,
                systemClassPath,
                Optional.empty());
    }

    /** A configuration whose JVM class path cannot be appended to. */
    public LaunchConfiguration(
            GameLaunchTarget target,
            Path gameJar,
            Path modsDirectory,
            List<Path> devModJars,
            Optional<Path> transformDumpDirectory,
            Path reportDirectory,
            Optional<Path> transformCacheDirectory,
            long transformCacheMaxBytes) {
        this(
                target,
                gameJar,
                modsDirectory,
                devModJars,
                transformDumpDirectory,
                reportDirectory,
                transformCacheDirectory,
                transformCacheMaxBytes,
                Optional.empty());
    }

    /** A configuration with the default transform cache size limit. */
    public LaunchConfiguration(
            GameLaunchTarget target,
            Path gameJar,
            Path modsDirectory,
            List<Path> devModJars,
            Optional<Path> transformDumpDirectory,
            Path reportDirectory,
            Optional<Path> transformCacheDirectory) {
        this(
                target,
                gameJar,
                modsDirectory,
                devModJars,
                transformDumpDirectory,
                reportDirectory,
                transformCacheDirectory,
                DEFAULT_TRANSFORM_CACHE_MAX_BYTES);
    }

    /** The side mods see, derived from {@link #target()}. */
    public GameSide side() {
        return target.side();
    }
}
