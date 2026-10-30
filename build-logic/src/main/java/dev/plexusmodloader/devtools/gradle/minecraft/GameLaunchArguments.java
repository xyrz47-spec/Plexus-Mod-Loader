package dev.plexusmodloader.devtools.gradle.minecraft;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/** Builds the JVM and program arguments every Poly Mod Loader game launch uses, so all run tasks agree. */
final class GameLaunchArguments {

    private GameLaunchArguments() {}

    private static final String LOG_CONFIG_RESOURCE = "log4j2-dev.xml";

    /**
     * Writes the bundled development log4j configuration into {@code runDirectory/.poly} and returns
     * it. Bundling it in the plugin lets external mod projects use the same logging as this repository.
     */
    static File developmentLogConfig(Path runDirectory) {
        Path target = runDirectory.resolve(".poly").resolve(LOG_CONFIG_RESOURCE);
        try (java.io.InputStream input = GameLaunchArguments.class.getResourceAsStream(LOG_CONFIG_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Plugin resource " + LOG_CONFIG_RESOURCE + " is missing");
            }
            java.nio.file.Files.createDirectories(target.getParent());
            java.nio.file.Files.copy(input, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException("Failed to write " + target, e);
        }
        return target.toFile();
    }

    /** Manifest JVM arguments plus the system properties {@code PlexusLauncherMain} reads. */
    static List<String> jvmArguments(
            MinecraftLaunchSpec spec,
            String launchTarget,
            Path runDirectory,
            Collection<File> devModJars,
            File logConfig,
            File transformCacheDirectory) {
        List<String> arguments = new ArrayList<>(spec.jvmArguments());
        arguments.add("-Dpoly.launchTarget=" + launchTarget);
        arguments.add("-Dpoly.gameJar=" + spec.gameJar());
        arguments.add("-Dpoly.modsDir=" + runDirectory.resolve("mods"));
        arguments.add("-Dpoly.devModJars="
                + devModJars.stream().map(File::getAbsolutePath).collect(Collectors.joining(File.pathSeparator)));
        arguments.add("-Dlog4j.configurationFile=" + logConfig.getAbsolutePath());
        arguments.add("-Dpoly.transformCacheDir=" + transformCacheDirectory.getAbsolutePath());
        return arguments;
    }

    /**
     * Program arguments: the manifest's arguments unless {@code overrides} is non-empty, then
     * {@code extra}. {@code ${game_directory}} is replaced with {@code runDirectory}.
     */
    static List<String> gameArguments(
            MinecraftLaunchSpec spec, List<String> overrides, List<String> extra, Path runDirectory) {
        List<String> source = new ArrayList<>(overrides.isEmpty() ? spec.gameArguments() : overrides);
        source.addAll(extra);
        List<String> resolved = new ArrayList<>();
        for (String argument : source) {
            resolved.add(argument.replace(MinecraftLaunchSpec.GAME_DIRECTORY_PLACEHOLDER, runDirectory.toString()));
        }
        return resolved;
    }
}
