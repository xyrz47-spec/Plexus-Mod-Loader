package dev.plexusmodloader.devtools.gradle.minecraft;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.work.DisableCachingByDefault;

/**
 * Opens an existing saved world in the client through Plexus Mod Loader (Quick Play) and passes once every
 * expected log line has appeared, then stops the client. This proves that a world written by an
 * earlier run is read back from disk. The client has no headless mode, so this task opens a game
 * window for a few seconds.
 */
@DisableCachingByDefault(because = "Runs the game")
public abstract class ClientWorldReloadTask extends DefaultTask {

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getLaunchSpecFile();

    @Classpath
    public abstract ConfigurableFileCollection getClasspath();

    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getDevModJars();


    @Nested
    public abstract Property<JavaLauncher> getJavaLauncher();

    /** Saved world to copy into the client's {@code saves/} folder and open. */
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getSourceWorld();

    @Input
    public abstract Property<String> getWorldName();

    @Internal
    public abstract DirectoryProperty getRunDirectory();

    @Internal
    public abstract DirectoryProperty getTransformCacheDirectory();

    @Input
    public abstract ListProperty<String> getExpectedOutput();

    @Input
    public abstract Property<Integer> getTimeoutSeconds();

    /**
     * Java agents for the client ({@code -javaagent:<jar>}), such as Plexus Mod Loader's own agent, which NeoForge needs
     * (ADR-0026) and the launcher profile starts.
     */
    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getJavaAgents();

    /** Extra Java system properties for the client, as {@code key=value}. */
    @Input
    public abstract ListProperty<String> getSystemProperties();

    @TaskAction
    public void run() throws IOException, InterruptedException {
        MinecraftLaunchSpec spec = MinecraftLaunchSpec.read(getLaunchSpecFile().get().getAsFile().toPath());
        Path runDirectory = getRunDirectory().get().getAsFile().toPath();
        VerifiedGameRunTask.deleteRecursively(runDirectory);
        Path world = runDirectory.resolve("saves").resolve(getWorldName().get());
        copyDirectory(getSourceWorld().get().getAsFile().toPath(), world);
        List<String> madeStable = StableVanillaDimensions.apply(world);
        if (!madeStable.isEmpty()) {
            getLogger()
                    .lifecycle(
                            "Gave the copied world the game's own generators for {} (the game test world is flat in "
                                    + "every dimension, which the game opens only after a backup prompt)",
                            madeStable);
        }
        Files.createDirectories(runDirectory.resolve("mods"));
        // A fresh game directory shows the first-launch accessibility screen, which blocks Quick Play.
        Files.writeString(
                runDirectory.resolve("options.txt"),
                "onboardAccessibility:false\ntutorialStep:none\n",
                StandardCharsets.UTF_8);

        List<String> command = new ArrayList<>();
        command.add(getJavaLauncher().get().getExecutablePath().getAsFile().getAbsolutePath());
        command.addAll(GameLaunchArguments.jvmArguments(
                spec,
                "client",
                runDirectory,
                getDevModJars().getFiles(),
                GameLaunchArguments.developmentLogConfig(runDirectory),
                getTransformCacheDirectory().get().getAsFile()));
        command.addAll(BenchmarkLaunch.javaAgents(getJavaAgents().getFiles()));
        getSystemProperties().getOrElse(List.of()).forEach(property -> command.add("-D" + property));
        command.add("-cp");
        List<String> classpath = new ArrayList<>();
        getClasspath().getFiles().forEach(file -> classpath.add(file.getAbsolutePath()));
        classpath.addAll(spec.libraries());
        // The game jar too, last, as the Minecraft Launcher and the server install put it (see RunMinecraftTask).
        classpath.add(spec.gameJar());
        command.add(String.join(File.pathSeparator, classpath));
        command.add(RunMinecraftTask.BOOTSTRAP_MAIN_CLASS);
        command.addAll(GameLaunchArguments.gameArguments(
                spec, List.of(), List.of("--quickPlaySingleplayer", getWorldName().get()), runDirectory));

        Set<String> remaining = ConcurrentHashMap.newKeySet();
        remaining.addAll(getExpectedOutput().get());
        Path outputLog = runDirectory.resolve("client-world-reload-output.log");
        // In an argument file: the whole command line can be longer than Windows allows (CreateProcess error=206).
        Process process = new ProcessBuilder(
                        JavaArgumentFile.commandWithArgumentFile(command, runDirectory.resolve("java.args")))
                .directory(runDirectory.toFile())
                .redirectErrorStream(true)
                .start();
        Thread reader = Thread.ofPlatform().name("client-output").start(() -> {
            try (BufferedReader lines =
                            new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                    var log = Files.newBufferedWriter(outputLog, StandardCharsets.UTF_8)) {
                String line;
                while ((line = lines.readLine()) != null) {
                    log.write(line);
                    log.newLine();
                    getLogger().info(line);
                    String current = line;
                    remaining.removeIf(current::contains);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(getTimeoutSeconds().get());
        while (!remaining.isEmpty() && process.isAlive() && System.nanoTime() < deadline) {
            Thread.sleep(250);
        }
        boolean exitedEarly = !process.isAlive();
        process.toHandle().descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
        process.waitFor(30, TimeUnit.SECONDS);
        reader.join(TimeUnit.SECONDS.toMillis(10));

        if (!remaining.isEmpty()) {
            // The client's last lines in the build output too: the log file is replaced by the next run.
            List<String> lines = Files.exists(outputLog)
                    ? Files.readAllLines(outputLog, StandardCharsets.UTF_8)
                    : List.of();
            getLogger()
                    .error(
                            "Last {} line(s) of the client output:\n{}",
                            Math.min(60, lines.size()),
                            String.join("\n", lines.subList(Math.max(0, lines.size() - 60), lines.size())));
            throw new GradleException("Client world reload check failed"
                    + (exitedEarly ? " (client exited with " + process.exitValue() + ")" : "")
                    + "; missing log lines: " + remaining.stream().sorted().collect(Collectors.joining(", "))
                    + ". Full output: " + outputLog + crashReports(runDirectory));
        }
        getLogger()
                .lifecycle(
                        "Client world reload check passed: world '{}' was loaded from disk ({} log lines verified)",
                        getWorldName().get(),
                        getExpectedOutput().get().size());
    }

    /**
     * The game's crash reports and JVM fatal error logs in {@code gameDirectory}, for a failure message, so that a failed
     * run names its evidence. Empty when there are none.
     */
    static String crashReports(Path gameDirectory) {
        List<String> found = new ArrayList<>();
        try (Stream<Path> reports = Files.exists(gameDirectory.resolve("crash-reports"))
                ? Files.list(gameDirectory.resolve("crash-reports"))
                : Stream.empty()) {
            reports.forEach(report -> found.add(report.toString()));
        } catch (IOException e) {
            found.add("(cannot list " + gameDirectory.resolve("crash-reports") + ": " + e + ")");
        }
        try (Stream<Path> files = Files.list(gameDirectory)) {
            files.filter(file -> file.getFileName().toString().startsWith("hs_err_pid"))
                    .forEach(file -> found.add(file.toString()));
        } catch (IOException e) {
            found.add("(cannot list " + gameDirectory + ": " + e + ")");
        }
        return found.isEmpty() ? "" : ". Crash reports: " + String.join(", ", found);
    }

    static void copyDirectory(Path source, Path target) throws IOException {
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path destination = target.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.copy(path, destination);
                }
            }
        }
    }
}
