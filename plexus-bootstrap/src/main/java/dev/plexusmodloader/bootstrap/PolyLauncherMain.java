package dev.plexusmodloader.bootstrap;

import dev.plexusmodloader.game.version.GameLaunchTarget;
import dev.plexusmodloader.runtime.LaunchConfiguration;
import dev.plexusmodloader.runtime.PolyRuntime;
import dev.plexusmodloader.runtime.diagnostics.PolyLaunchException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JVM entry point. Reads the launch configuration from system properties, runs the Poly Mod Loader runtime,
 * and passes all program arguments through to Minecraft unchanged.
 *
 * <table>
 *   <caption>System properties</caption>
 *   <tr><th>Property</th><th>Meaning</th></tr>
 *   <tr><td>{@code poly.launchTarget}</td><td>{@code client}, {@code dedicated_server}, or {@code game_test_server} (required)</td></tr>
 *   <tr><td>{@code poly.gameJar}</td><td>Minecraft game jar; without it, the one class path jar with a
 *       {@code version.json} (how the Minecraft Launcher starts an installed Poly Mod Loader profile)</td></tr>
 *   <tr><td>{@code poly.modsDir}</td><td>mods folder (default {@code mods})</td></tr>
 *   <tr><td>{@code poly.devModJars}</td><td>extra mod jars, separated by the path separator</td></tr>
 *   <tr><td>{@code poly.transformDumpDir}</td><td>write before/after class files of transformations here</td></tr>
 *   <tr><td>{@code poly.reportDir}</td><td>directory for Poly Mod Loader reports (default {@code .poly})</td></tr>
 *   <tr><td>{@code poly.transformCacheDir}</td><td>transformation cache (default {@code .poly/transform-cache}; {@code none} disables it)</td></tr>
 *   <tr><td>{@code poly.transformCacheMaxMegabytes}</td><td>transformation cache size limit (default 256); least recently used entries are removed beyond it</td></tr>
 * </table>
 */
public final class PolyLauncherMain {

    private static final Logger LOGGER = LoggerFactory.getLogger("Poly Mod Loader");

    private PolyLauncherMain() {}

    public static void main(String[] args) throws Throwable {
        LaunchConfiguration configuration;
        try {
            configuration = readConfiguration();
        } catch (IllegalArgumentException e) {
            LOGGER.error("[Poly Mod Loader] PLEXUS-BOOTSTRAP-003 invalid launch configuration: {}", e.getMessage());
            System.exit(2);
            return;
        }
        try {
            new PolyRuntime().launch(configuration, args);
        } catch (PolyLaunchException e) {
            LOGGER.error("[Poly Mod Loader] {}", e.getMessage(), e.getCause());
            System.exit(1);
        } catch (Throwable e) {
            // The game's main method failed. Threads that mods started (executors that are not daemons) would keep
            // the process running without a game, so it ends here.
            LOGGER.error("[Poly Mod Loader] PLEXUS-BOOTSTRAP-007 the game stopped with an error; exiting", e);
            System.exit(1);
        }
    }

    static LaunchConfiguration readConfiguration() {
        String targetText = required("poly.launchTarget");
        GameLaunchTarget target = GameLaunchTarget.parse(targetText)
                .orElseThrow(() -> new IllegalArgumentException(
                        "poly.launchTarget must be client, dedicated_server, or game_test_server, got " + targetText));
        String gameJarSetting = System.getProperty("poly.gameJar");
        Path gameJar = gameJarSetting == null || gameJarSetting.isBlank()
                ? ClassPathGameJarLocator.locate(System.getProperty("java.class.path", ""))
                : Path.of(gameJarSetting);
        if (!Files.isRegularFile(gameJar)) {
            throw new IllegalArgumentException("poly.gameJar does not exist: " + gameJar);
        }
        Path modsDirectory = Path.of(System.getProperty("poly.modsDir", "mods"));
        List<Path> devModJars = Arrays.stream(
                        System.getProperty("poly.devModJars", "").split(File.pathSeparator))
                .filter(path -> !path.isBlank())
                .map(Path::of)
                .toList();
        Optional<Path> dumpDirectory =
                Optional.ofNullable(System.getProperty("poly.transformDumpDir")).map(Path::of);
        Path reportDirectory = Path.of(System.getProperty("poly.reportDir", ".poly"));
        String cacheSetting = System.getProperty(
                "poly.transformCacheDir",
                reportDirectory.resolve("transform-cache").toString());
        Optional<Path> cacheDirectory =
                cacheSetting.equals("none") ? Optional.empty() : Optional.of(Path.of(cacheSetting));
        long cacheMaxBytes = cacheMaxMegabytes() * 1024 * 1024;
        return new LaunchConfiguration(
                target,
                gameJar,
                modsDirectory,
                devModJars,
                dumpDirectory,
                reportDirectory,
                cacheDirectory,
                cacheMaxBytes,
                SystemClassPathAgent.systemClassPath(),
                SystemClassPathAgent.jdkModuleAccess());
    }

    private static long cacheMaxMegabytes() {
        String text = System.getProperty("poly.transformCacheMaxMegabytes");
        if (text == null) {
            return LaunchConfiguration.DEFAULT_TRANSFORM_CACHE_MAX_BYTES / (1024 * 1024);
        }
        try {
            long megabytes = Long.parseLong(text.strip());
            if (megabytes > 0 && megabytes <= Long.MAX_VALUE / (1024 * 1024)) {
                return megabytes;
            }
        } catch (NumberFormatException ignored) {
            // Reported below with the same message as an out-of-range number.
        }
        throw new IllegalArgumentException(
                "poly.transformCacheMaxMegabytes must be a positive whole number of megabytes, got " + text);
    }

    private static String required(String property) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("system property " + property + " is required");
        }
        return value;
    }
}
