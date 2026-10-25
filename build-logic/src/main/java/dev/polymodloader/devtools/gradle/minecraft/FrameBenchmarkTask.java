package dev.polymodloader.devtools.gradle.minecraft;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
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
 * Compares client frame times of vanilla and Poly Mod Loader in the same saved world (design doc §14.1,
 * §14.4). Each run copies the world, opens it in the client with Quick Play, waits {@link
 * #getSettleSeconds()} after the player joins, and then times every {@code Minecraft.runTick} call for
 * {@link #getMeasureSeconds()}. The client's main loop calls {@code runTick} once per frame, and it
 * runs the game ticks and renders the frame, so its duration is the frame time.
 *
 * <p>Both variants get the same tick probe agent ({@code tools/tick-probe}) and the same options:
 * vsync off, no frame rate cap, no pause when the window loses focus. The client has no headless
 * mode, so every run opens a game window. Runs alternate after one discarded warm-up of each, each in a
 * fresh directory. The client is stopped without a normal exit, so the probe writes its file when the
 * task sends it {@code dump} on standard input.
 *
 * <p>Between two clients the task waits {@link #getCooldownSeconds()} after the previous process has
 * exited. Opening a new OpenGL window right after the last one was killed is what was running when a
 * development PC (NVIDIA laptop GPU) blue-screened with a display-driver fault, so the pause gives the
 * driver time to release the old context. Each finished run is appended to {@code runs.jsonl} at once,
 * so a crash part-way through keeps the runs that completed.
 */
@DisableCachingByDefault(because = "Benchmarks must run every time")
public abstract class FrameBenchmarkTask extends DefaultTask {

    static final String FRAME_TARGET = "net/minecraft/client/Minecraft.runTick(Z)V";

    private static final String WORLD_NAME = "poly_frame_benchmark";
    private static final String JOINED = "joined the game";

    /**
     * Options that keep the measurement fair: the first-launch screen off (it blocks Quick Play),
     * vsync off and the frame rate uncapped (260 means unlimited), and no pause on lost focus, so a
     * window behind other windows keeps rendering.
     */
    static final String OPTIONS = """
            onboardAccessibility:false
            tutorialStep:none
            enableVsync:false
            maxFps:260
            pauseOnLostFocus:false
            renderDistance:12
            simulationDistance:12
            fullscreen:false
            """;

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getLaunchSpecFile();

    @Classpath
    public abstract ConfigurableFileCollection getPolyRuntime();

    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getDevModJars();

    /** The tick probe agent jar. */
    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getProbeAgent();

    @Nested
    public abstract Property<JavaLauncher> getJavaLauncher();

    /** Saved world that every run opens a fresh copy of. */
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getSourceWorld();

    /** Measured runs per variant. */
    @Input
    public abstract Property<Integer> getRuns();

    /** Seconds after the player joins before frames are counted. */
    @Input
    public abstract Property<Integer> getSettleSeconds();

    /** Seconds of frames that are counted in each run. */
    @Input
    public abstract Property<Integer> getMeasureSeconds();

    /** Seconds to wait after one client has exited before the next one opens its window. */
    @Input
    public abstract Property<Integer> getCooldownSeconds();

    @Internal
    public abstract DirectoryProperty getWorkDirectory();

    @Internal
    public abstract DirectoryProperty getTransformCacheDirectory();

    /** One run's figures. */
    record RunResult(
            int frames,
            double meanFps,
            double p50Millis,
            double p95Millis,
            double p99Millis,
            double maxMillis,
            double over33MillisPercent) {

        /** This run as a JSON object. */
        String toJson() {
            return String.format(
                    Locale.ROOT,
                    "{\"frames\": %d, \"meanFps\": %.2f, \"p50Millis\": %.4f, \"p95Millis\": %.4f,"
                            + " \"p99Millis\": %.4f, \"maxMillis\": %.4f, \"over33MillisPercent\": %.4f}",
                    frames,
                    meanFps,
                    p50Millis,
                    p95Millis,
                    p99Millis,
                    maxMillis,
                    over33MillisPercent);
        }
    }

    @TaskAction
    public void run() throws IOException, InterruptedException {
        MinecraftLaunchSpec spec = MinecraftLaunchSpec.read(getLaunchSpecFile().get().getAsFile().toPath());
        Path work = getWorkDirectory().get().getAsFile().toPath();
        VerifiedGameRunTask.deleteRecursively(work);
        Path probe = getProbeAgent().getSingleFile().toPath().toAbsolutePath();
        Path world = getSourceWorld().get().getAsFile().toPath();
        int runs = getRuns().get();
        Map<String, List<RunResult>> results = new LinkedHashMap<>();
        results.put("vanilla", new ArrayList<>());
        results.put("poly", new ArrayList<>());
        Path progress = work.resolve("runs.jsonl");
        boolean first = true;
        for (int i = -1; i < runs; i++) {
            for (String variant : results.keySet()) {
                if (!first) {
                    cooldown();
                }
                first = false;
                Path directory = work.resolve(variant + "-" + i);
                ClientWorldReloadTask.copyDirectory(world, directory.resolve("saves").resolve(WORLD_NAME));
                StableVanillaDimensions.apply(directory.resolve("saves").resolve(WORLD_NAME));
                Files.createDirectories(directory.resolve("mods"));
                Files.writeString(directory.resolve("options.txt"), OPTIONS, StandardCharsets.UTF_8);
                Path frames = directory.resolve("frames.txt");
                List<String> extraJvm = List.of("-javaagent:" + probe + "=out=" + frames.toAbsolutePath() + ",target="
                        + FRAME_TARGET + ",control=stdin");
                List<String> programArgs = GameLaunchArguments.gameArguments(
                        spec, List.of(), List.of("--quickPlaySingleplayer", WORLD_NAME), directory);
                List<String> command = variant.equals("vanilla")
                        ? BenchmarkLaunch.vanilla(
                                java(), BenchmarkLaunch.Target.CLIENT, spec, directory, extraJvm, programArgs)
                        : BenchmarkLaunch.poly(
                                java(),
                                BenchmarkLaunch.Target.CLIENT,
                                spec,
                                directory,
                                getPolyRuntime().getFiles(),
                                getDevModJars().getFiles(),
                                getTransformCacheDirectory().get().getAsFile(),
                                extraJvm,
                                programArgs);
                measure(command, directory);
                RunResult result = analyse(frames);
                if (i >= 0) {
                    results.get(variant).add(result);
                }
                Files.writeString(
                        progress,
                        progressLine(variant, i, result),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND);
                getLogger().lifecycle("{} run {}: {}", variant, i < 0 ? "warm-up" : Integer.toString(i), result);
            }
        }
        String report = report(results, runs);
        Files.writeString(work.resolve("frame-benchmark.json"), report, StandardCharsets.UTF_8);
        getLogger().lifecycle(report);
    }

    /** One line of {@code runs.jsonl}: the variant, the run index ({@code -1} is the warm-up), and its figures. */
    static String progressLine(String variant, int index, RunResult result) {
        return "{\"variant\": \"" + variant + "\", \"run\": " + index + ", \"result\": " + result.toJson() + "}\n";
    }

    private void cooldown() throws InterruptedException {
        int seconds = getCooldownSeconds().get();
        if (seconds > 0) {
            getLogger().lifecycle("Waiting {} s before the next client window", seconds);
            TimeUnit.SECONDS.sleep(seconds);
        }
    }

    /**
     * Starts the client, waits for the player to join and to settle, marks the start of the window,
     * waits out the window, asks the probe to write the file, and stops the client.
     */
    private void measure(List<String> command, Path directory) throws IOException, InterruptedException {
        Path log = directory.resolve("output.log");
        Set<String> seen = ConcurrentHashMap.newKeySet();
        Process process = new ProcessBuilder(command)
                .directory(directory.toFile())
                .redirectErrorStream(true)
                .start();
        String timing = "[tick-probe] timing " + FRAME_TARGET.substring(0, FRAME_TARGET.indexOf('('));
        Thread reader = Thread.ofPlatform().name("frame-benchmark-output").start(() -> {
            try (BufferedReader lines =
                            new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                    var writer = Files.newBufferedWriter(log, StandardCharsets.UTF_8)) {
                String line;
                while ((line = lines.readLine()) != null) {
                    writer.write(line);
                    writer.newLine();
                    for (String marker : List.of(timing, JOINED, "[tick-probe] mark", "[tick-probe] wrote")) {
                        if (line.contains(marker)) {
                            seen.add(marker);
                        }
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        try (OutputStream control = process.getOutputStream()) {
            await(process, seen, JOINED, 240, log);
            if (!seen.contains(timing)) {
                throw new GradleException("The tick probe did not wrap " + FRAME_TARGET + ": " + log);
            }
            TimeUnit.SECONDS.sleep(getSettleSeconds().get());
            send(control, "mark");
            await(process, seen, "[tick-probe] mark", 10, log);
            TimeUnit.SECONDS.sleep(getMeasureSeconds().get());
            send(control, "dump");
            await(process, seen, "[tick-probe] wrote", 30, log);
        } finally {
            process.toHandle().descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            process.waitFor(30, TimeUnit.SECONDS);
            reader.join(TimeUnit.SECONDS.toMillis(10));
        }
    }

    private static void send(OutputStream control, String commandLine) throws IOException {
        control.write((commandLine + "\n").getBytes(StandardCharsets.UTF_8));
        control.flush();
    }

    private static void await(Process process, Set<String> seen, String marker, int timeoutSeconds, Path log)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        while (!seen.contains(marker)) {
            if (!process.isAlive()) {
                throw new GradleException("The client exited with " + process.exitValue() + " before '" + marker
                        + "' appeared: " + log);
            }
            if (System.nanoTime() > deadline) {
                throw new GradleException(
                        "'" + marker + "' did not appear within " + timeoutSeconds + " seconds: " + log);
            }
            Thread.sleep(100);
        }
    }

    private RunResult analyse(Path file) throws IOException {
        long[] frames = Files.readAllLines(file).stream()
                .filter(line -> !line.isBlank())
                .mapToLong(Long::parseLong)
                .toArray();
        // A window of fewer frames than seconds means the client was stuck, not slow.
        if (frames.length < getMeasureSeconds().get()) {
            throw new GradleException("Only " + frames.length + " frames were recorded in " + file);
        }
        return summarise(frames);
    }

    /** Frame figures from per-frame durations in nanoseconds, in any order. */
    static RunResult summarise(long[] frames) {
        long[] sorted = frames.clone();
        Arrays.sort(sorted);
        long total = Arrays.stream(sorted).sum();
        long over = Arrays.stream(sorted).filter(nanos -> nanos > 33_333_333L).count();
        return new RunResult(
                sorted.length,
                sorted.length * 1e9 / total,
                TickBenchmarkTask.percentile(sorted, 50) / 1e6,
                TickBenchmarkTask.percentile(sorted, 95) / 1e6,
                TickBenchmarkTask.percentile(sorted, 99) / 1e6,
                sorted[sorted.length - 1] / 1e6,
                100.0 * over / sorted.length);
    }

    private String report(Map<String, List<RunResult>> results, int runs) {
        StringBuilder json = new StringBuilder();
        json.append("{\n  \"scenario\": \"client, Quick Play into the world saved by worldCreateSaveTest, ")
                .append(getSettleSeconds().get())
                .append(" s to settle, then ")
                .append(getMeasureSeconds().get())
                .append(" s of frames; vsync off, frame rate uncapped\",\n");
        json.append("  \"runsPerVariant\": ").append(runs).append(",\n  \"warmupRunsDiscarded\": 1,\n");
        json.append("  \"java\": \"")
                .append(getJavaLauncher().get().getMetadata().getJavaRuntimeVersion())
                .append("\",\n");
        int variants = 0;
        for (Map.Entry<String, List<RunResult>> entry : results.entrySet()) {
            List<RunResult> list = entry.getValue();
            json.append("  \"").append(entry.getKey()).append("\": {\n");
            json.append("    \"runs\": [\n      ")
                    .append(String.join(",\n      ", list.stream().map(RunResult::toJson).toList()))
                    .append("\n    ],\n");
            json.append(String.format(
                    Locale.ROOT,
                    "    \"median\": {\"frames\": %.0f, \"meanFps\": %.2f, \"p50Millis\": %.3f, \"p95Millis\": %.3f,"
                            + " \"p99Millis\": %.3f, \"maxMillis\": %.3f, \"over33MillisPercent\": %.3f},\n",
                    median(list, run -> run.frames()),
                    median(list, RunResult::meanFps),
                    median(list, RunResult::p50Millis),
                    median(list, RunResult::p95Millis),
                    median(list, RunResult::p99Millis),
                    median(list, RunResult::maxMillis),
                    median(list, RunResult::over33MillisPercent)));
            json.append(String.format(
                    Locale.ROOT,
                    "    \"stdDev\": {\"meanFps\": %.2f, \"p50Millis\": %.3f, \"p95Millis\": %.3f, \"p99Millis\": %.3f}\n",
                    stdDev(list, RunResult::meanFps),
                    stdDev(list, RunResult::p50Millis),
                    stdDev(list, RunResult::p95Millis),
                    stdDev(list, RunResult::p99Millis)));
            json.append(++variants < results.size() ? "  },\n" : "  }\n");
        }
        return json.append("}\n").toString();
    }

    private static double median(List<RunResult> runs, java.util.function.ToDoubleFunction<RunResult> value) {
        return BenchmarkLaunch.median(runs.stream().map(run -> value.applyAsDouble(run)).toList());
    }

    private static double stdDev(List<RunResult> runs, java.util.function.ToDoubleFunction<RunResult> value) {
        return BenchmarkLaunch.stdDev(runs.stream().map(run -> value.applyAsDouble(run)).toList());
    }

    private String java() {
        return getJavaLauncher().get().getExecutablePath().getAsFile().getAbsolutePath();
    }
}
