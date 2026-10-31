package dev.plexusmodloader.devtools.gradle.minecraft;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.gradle.api.GradleException;

/**
 * The command NeoForge's own server start uses ({@code run.bat}/{@code run.sh}: {@code java @user_jvm_args.txt
 * @libraries/net/neoforged/neoforge/<version>/win_args.txt}), with FML's game test server entry point in place of its
 * dedicated server one, for the benchmarks that compare with a real NeoForge installation ({@link
 * NeoForgeServerInstallTask}). FML's {@code net.neoforged.fml.startup.GameTestServer} starts Mojang's {@code
 * net.minecraft.gametest.Main} after FML's own startup, as {@code Server} starts {@code net.minecraft.server.Main}.
 * Paths in the argument files are relative to the server folder; they are made absolute so that every run can have a
 * fresh folder of its own.
 */
final class NeoForgeServerLaunch {

    static final String SERVER_MAIN = "net.neoforged.fml.startup.Server";
    static final String GAME_TEST_SERVER_MAIN = "net.neoforged.fml.startup.GameTestServer";

    private NeoForgeServerLaunch() {}

    /** The installed argument file for this operating system. */
    static Path argumentsFile(Path server, String neoForgeVersion) {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        return server.resolve("libraries/net/neoforged/neoforge")
                .resolve(neoForgeVersion)
                .resolve(windows ? "win_args.txt" : "unix_args.txt");
    }

    /**
     * {@code java}, {@code extraJvm}, the server's {@code user_jvm_args.txt} and argument file, the game test server
     * entry point, the argument file's program arguments and {@code programArgs}.
     */
    static List<String> gameTestServer(
            String java, Path server, String neoForgeVersion, List<String> extraJvm, List<String> programArgs)
            throws IOException {
        List<String> tokens = new ArrayList<>();
        Path userArgs = server.resolve("user_jvm_args.txt");
        if (Files.isRegularFile(userArgs)) {
            tokens.addAll(tokens(Files.readString(userArgs, StandardCharsets.UTF_8)));
        }
        Path arguments = argumentsFile(server, neoForgeVersion);
        if (!Files.isRegularFile(arguments)) {
            throw new GradleException("No NeoForge server is installed in " + server + " (" + arguments + " is missing)");
        }
        tokens.addAll(tokens(Files.readString(arguments, StandardCharsets.UTF_8)));
        int main = tokens.indexOf(SERVER_MAIN);
        if (main < 0) {
            throw new GradleException(arguments + " does not start " + SERVER_MAIN);
        }
        List<String> command = new ArrayList<>();
        command.add(java);
        command.addAll(extraJvm);
        command.addAll(absolute(tokens.subList(0, main), server));
        command.add(GAME_TEST_SERVER_MAIN);
        command.addAll(tokens.subList(main + 1, tokens.size()));
        command.addAll(programArgs);
        return command;
    }

    /** Folder in a run where FML mods keep what they build on a first start (Connector's transformed Fabric jars). */
    static final String GAME_CACHE = ".cache";

    /**
     * Puts {@code modJars} into the run's {@code mods/} folder and, when {@code warmCache} exists, a copy of it as the
     * run's {@link #GAME_CACHE}: every measured run then starts as a second start of the same installation does, as
     * Plexus Mod Loader's runs share their transform cache.
     */
    static void prepareRun(Path directory, Collection<File> modJars, Path warmCache) throws IOException {
        Path mods = directory.resolve("mods");
        Files.createDirectories(mods);
        for (File jar : modJars) {
            Files.copy(jar.toPath(), mods.resolve(jar.getName()), StandardCopyOption.REPLACE_EXISTING);
        }
        if (Files.isDirectory(warmCache)) {
            copyTree(warmCache, directory.resolve(GAME_CACHE));
        }
    }

    /** Keeps the warm-up run's {@link #GAME_CACHE} as {@code warmCache} for the measured runs. */
    static void keepCache(Path warmupDirectory, Path warmCache) throws IOException {
        VerifiedGameRunTask.deleteRecursively(warmCache);
        Path cache = warmupDirectory.resolve(GAME_CACHE);
        if (Files.isDirectory(cache)) {
            copyTree(cache, warmCache);
        }
    }

    private static void copyTree(Path from, Path to) throws IOException {
        try (Stream<Path> files = Files.walk(from)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                Path target = to.resolve(from.relativize(file).toString());
                if (Files.isDirectory(file)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    /** The arguments of a Java argument file without {@code #} comment lines, split at white space. */
    static List<String> tokens(String argumentFile) {
        return argumentFile
                .lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .flatMap(line -> Arrays.stream(line.split("\\s+")))
                .toList();
    }

    /** {@code jvmArguments} with the paths into the server folder ({@code libraries/...}) made absolute. */
    static List<String> absolute(List<String> jvmArguments, Path server) {
        List<String> result = new ArrayList<>();
        boolean pathList = false;
        for (String argument : jvmArguments) {
            if (pathList) {
                result.add(Arrays.stream(argument.split(File.pathSeparator))
                        .map(entry -> resolve(server, entry))
                        .collect(Collectors.joining(File.pathSeparator)));
                pathList = false;
            } else if (argument.startsWith("-DlibraryDirectory=")) {
                result.add("-DlibraryDirectory=" + resolve(server, argument.substring("-DlibraryDirectory=".length())));
            } else {
                result.add(argument);
                pathList = argument.equals("-classpath")
                        || argument.equals("-cp")
                        || argument.equals("-p")
                        || argument.equals("--module-path");
            }
        }
        return result;
    }

    private static String resolve(Path server, String path) {
        Path file = Path.of(path);
        return file.isAbsolute() ? path : server.resolve(file).normalize().toString();
    }
}
