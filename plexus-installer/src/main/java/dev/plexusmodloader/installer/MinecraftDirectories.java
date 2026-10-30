package dev.plexusmodloader.installer;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Where the Minecraft Launcher keeps its files, and whether it is running. */
final class MinecraftDirectories {

    private MinecraftDirectories() {}

    /** The launcher's default folder on this operating system ({@code .minecraft}). */
    static Path defaultDirectory() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        Path home = Path.of(System.getProperty("user.home"));
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            return (appData == null ? home.resolve("AppData").resolve("Roaming") : Path.of(appData))
                    .resolve(".minecraft");
        }
        if (os.contains("mac")) {
            return home.resolve("Library").resolve("Application Support").resolve("minecraft");
        }
        return home.resolve(".minecraft");
    }

    private static final List<String> LAUNCHER_EXECUTABLES =
            List.of("minecraftlauncher.exe", "minecraft.exe", "minecraft-launcher", "minecraftlauncher");

    /**
     * The command of a running Minecraft Launcher, if there is one. The launcher rewrites
     * {@code launcher_profiles.json} when it closes, which can drop a profile added while it was open.
     */
    static Optional<String> runningLauncher() {
        return ProcessHandle.allProcesses()
                .map(process -> process.info().command().orElse(""))
                .filter(command -> {
                    int separator = Math.max(command.lastIndexOf('/'), command.lastIndexOf('\\'));
                    String name = command.substring(separator + 1).toLowerCase(Locale.ROOT);
                    return LAUNCHER_EXECUTABLES.contains(name);
                })
                .findFirst();
    }
}
