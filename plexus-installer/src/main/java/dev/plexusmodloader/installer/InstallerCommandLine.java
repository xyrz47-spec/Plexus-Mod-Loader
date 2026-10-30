package dev.plexusmodloader.installer;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

/**
 * {@code java -jar PolyModLoader-<version>.jar install|install-server|uninstall [--dir <folder>] [--minecraft <version>]
 * [--no-profile] [--max-memory <size>] [--min-memory <size>]}. Exit codes: 0 done, 1 failed, 2 wrong arguments.
 */
final class InstallerCommandLine {

    private final InstallerPayload payload;
    private final PrintStream out;
    private final PrintStream err;

    InstallerCommandLine(InstallerPayload payload, PrintStream out, PrintStream err) {
        this.payload = payload;
        this.out = out;
        this.err = err;
    }

    String usage() {
        return """
                Poly Mod Loader %1$s installer

                Usage:
                  java -jar PolyModLoader-%1$s.jar                      open the installer window
                  java -jar PolyModLoader-%1$s.jar install [options]         install into the Minecraft Launcher
                  java -jar PolyModLoader-%1$s.jar install-server [options]  install a dedicated server: downloads
                                                                       Mojang's server and writes run.bat, run.sh
                                                                       and polymodloader-server.jar
                  java -jar PolyModLoader-%1$s.jar uninstall [options]       remove the Poly Mod Loader version and profile

                Options:
                  --dir <folder>          the launcher folder (default %2$s);
                                          for install-server the server folder (default: this folder)
                  --minecraft <version>   Minecraft version: %3$s (default %4$s)
                  --no-profile            install the version but do not add a launcher profile
                  --max-memory <size>     the game's maximum memory (-Xmx), such as 4G or 4096M;
                                          default: the launcher's own setting (for a server, Java's)
                  --min-memory <size>     the game's starting memory (-Xms), such as 1G
                  --help, --version
                """.formatted(
                        payload.polyVersion(),
                        MinecraftDirectories.defaultDirectory(),
                        String.join(", ", payload.minecraftVersions()),
                        payload.newestMinecraftVersion());
    }

    int run(List<String> args) {
        if (args.isEmpty() || args.contains("--help") || args.contains("-h")) {
            out.print(usage());
            return args.isEmpty() ? 2 : 0;
        }
        if (args.contains("--version")) {
            out.println(payload.polyVersion());
            return 0;
        }
        String command = args.get(0);
        Path directory = command.equals("install-server")
                ? Path.of("").toAbsolutePath()
                : MinecraftDirectories.defaultDirectory();
        String minecraft = payload.newestMinecraftVersion();
        boolean profile = true;
        String maximumMemory = null;
        String minimumMemory = null;
        for (int i = 1; i < args.size(); i++) {
            String option = args.get(i);
            switch (option) {
                case "--dir", "--minecraft", "--max-memory", "--min-memory", "--xmx", "--xms" -> {
                    if (i + 1 >= args.size()) {
                        err.println(option + " needs a value");
                        return 2;
                    }
                    String value = args.get(++i);
                    switch (option) {
                        case "--dir" -> directory = Path.of(value);
                        case "--minecraft" -> minecraft = value;
                        case "--max-memory", "--xmx" -> maximumMemory = value;
                        default -> minimumMemory = value;
                    }
                }
                case "--no-profile" -> profile = false;
                default -> {
                    err.println("Unknown option " + option);
                    err.print(usage());
                    return 2;
                }
            }
        }
        JavaMemory memory;
        try {
            memory = JavaMemory.parse(maximumMemory, minimumMemory);
        } catch (IllegalArgumentException e) {
            err.println(e.getMessage());
            return 2;
        }
        LauncherProfileInstaller installer = new LauncherProfileInstaller(payload, Clock.systemUTC());
        try {
            switch (command) {
                case "install" -> {
                    MinecraftDirectories.runningLauncher()
                            .ifPresent(launcher -> err.println("Warning: the Minecraft Launcher is running (" + launcher
                                    + "). Close it before installing, or it may drop the new profile when it exits."));
                    List<String> warnings = installer.install(directory, minecraft, profile, memory, out::println);
                    warnings.forEach(warning -> err.println("Warning: " + warning));
                    out.println("Poly Mod Loader " + payload.polyVersion() + " for Minecraft " + minecraft
                            + " is installed. Choose the profile \"Poly Mod Loader " + minecraft
                            + "\" in the Minecraft Launcher.");
                }
                case "install-server" -> {
                    List<String> warnings = new ServerInstaller(payload, ServerInstaller.httpDownloader())
                            .install(directory, minecraft, memory, out::println);
                    warnings.forEach(warning -> err.println("Warning: " + warning));
                    out.println("Poly Mod Loader " + payload.polyVersion() + " server for Minecraft " + minecraft
                            + " is installed in " + directory.toAbsolutePath() + ". Start it with run.bat, run.sh or"
                            + " java -jar " + ServerInstaller.LAUNCH_JAR + " nogui.");
                }
                case "uninstall" -> {
                    installer.uninstall(directory, minecraft, out::println);
                    out.println("Poly Mod Loader " + payload.polyVersion() + " for Minecraft " + minecraft
                            + " is removed.");
                }
                default -> {
                    err.println("Unknown command " + command);
                    err.print(usage());
                    return 2;
                }
            }
            return 0;
        } catch (InstallerException e) {
            err.println("Error: " + e.getMessage());
            return 1;
        } catch (IOException e) {
            err.println("Error: could not write a file: " + e);
            return 1;
        }
    }
}
