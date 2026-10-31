package dev.plexusmodloader.devtools.gradle.minecraft;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.work.DisableCachingByDefault;

/**
 * Compares server tick times, heap and garbage collection of vanilla and Plexus Mod Loader under the same load
 * (design doc §14.1, §14.4). Each run starts Mojang's game test server with a data pack that defines
 * the test {@code plexus_tick_benchmark:hold}, which waits {@link #getHoldTicks()} ticks and passes, and
 * a tick function that keeps 40 pigs alive and fills and clears a 16×16×16 cube of glass every tick. The
 * pack works without Plexus Mod Loader, so both variants do the same work.
 *
 * <p>Both variants get the same measuring tools: the tick probe agent ({@code tools/tick-probe}) times
 * every {@code GameTestServer.tickServer} call, and Java Flight Recorder records the heap and the
 * collections. Runs alternate (vanilla, Plexus Mod Loader, …) after one discarded warm-up of each, each in a
 * fresh directory; the first {@link #getWarmupTicks()} ticks of every run are discarded. Raw tick
 * times stay in each run directory, and {@code tick-benchmark.json} holds every run's figures.
 *
 * <p>Peak heap use depends on when G1 decides to grow the heap, so it varies widely between runs. To
 * compare memory, set {@link #getHeapSize()}: both variants then get the same fixed heap ({@code -Xms}
 * = {@code -Xmx}), and the largest heap still in use after a collection shows what each variant keeps
 * alive.
 *
 * <p>With {@link #getReferenceName()}, a third variant runs in the same rotation: a real NeoForge installation started
 * by NeoForge's own argument files on FML's game test server entry point, with {@link #getReferenceModJars()} (for
 * Sinytra Connector and the Fabric mods it runs). It gets the same tools, heap, data pack and log configuration. Its
 * warm-up run's game-folder cache is copied into each of its measured runs.
 */
@DisableCachingByDefault(because = "Benchmarks must run every time")
public abstract class TickBenchmarkTask extends DefaultTask {

    static final String TICK_TARGET =
            "net/minecraft/gametest/framework/GameTestServer.tickServer(Ljava/util/function/BooleanSupplier;)V";

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getLaunchSpecFile();

    @Classpath
    public abstract ConfigurableFileCollection getPlexusRuntime();

    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getDevModJars();

    /** The tick probe agent jar. */
    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getProbeAgent();

    @Nested
    public abstract Property<JavaLauncher> getJavaLauncher();

    /**
     * Java agents for the Plexus Mod Loader variant only ({@code -javaagent:<jar>}), such as Plexus Mod Loader's own agent,
     * which NeoForge needs (ADR-0026) and the launcher profile starts.
     */
    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getPlexusJavaAgents();

    /** Measured runs per variant. */
    @Input
    public abstract Property<Integer> getRuns();

    /** How many ticks the hold test waits, which sets how long each run lasts. */
    @Input
    public abstract Property<Integer> getHoldTicks();

    /** Ticks at the start of each run that are not counted. */
    @Input
    public abstract Property<Integer> getWarmupTicks();

    /**
     * A fixed heap size for both variants, such as {@code 2g}, passed as {@code -Xms} and {@code -Xmx}.
     * When absent, the JVM picks its default heap.
     */
    @Input
    @Optional
    public abstract Property<String> getHeapSize();

    @Internal
    public abstract DirectoryProperty getWorkDirectory();

    @Internal
    public abstract DirectoryProperty getTransformCacheDirectory();

    /**
     * The name of a third variant that runs a real NeoForge installation ({@link NeoForgeServerInstallTask}) with
     * {@link #getReferenceModJars()} instead of Plexus Mod Loader, such as {@code connector}. When absent, only vanilla
     * and Plexus Mod Loader run.
     */
    @Input
    @Optional
    public abstract Property<String> getReferenceName();

    /** The installed NeoForge server folder of the reference variant. */
    @Internal
    public abstract DirectoryProperty getReferenceServer();

    /** The NeoForge version installed in {@link #getReferenceServer()}. */
    @Input
    @Optional
    public abstract Property<String> getReferenceNeoForgeVersion();

    /** The jars the reference variant gets in its {@code mods/} folder. */
    @InputFiles
    @PathSensitive(PathSensitivity.NAME_ONLY)
    public abstract ConfigurableFileCollection getReferenceModJars();

    /**
     * The Java Flight Recorder settings of every run: {@code default} (the measurement setting), {@code profile}, or the
     * path of a {@code .jfc} file. The heap and collection figures come from the same recording either way.
     */
    @Input
    public abstract Property<String> getJfrSettings();

    /**
     * When true, the tick probe also commits each tick as a JFR event ({@code plexus.TickProbe.Call}), so the recording
     * can be split into slow and ordinary ticks. For profiling; every variant gets the same setting.
     */
    @Input
    public abstract Property<Boolean> getTickEvents();

    /** Lines every Plexus Mod Loader run's output must have (which mods loaded). */
    @Input
    public abstract ListProperty<String> getPlexusExpectedOutput();

    /** Lines every reference run's output must have (which mods loaded). */
    @Input
    public abstract ListProperty<String> getReferenceExpectedOutput();

    /** {@code extraJvm} with the Plexus Mod Loader variant's own Java agents first. */
    private List<String> plexusJvm(List<String> extraJvm) {
        List<String> arguments = new ArrayList<>(BenchmarkLaunch.javaAgents(getPlexusJavaAgents().getFiles()));
        arguments.addAll(extraJvm);
        return arguments;
    }

    public TickBenchmarkTask() {
        getPlexusExpectedOutput().convention(List.of());
        getReferenceExpectedOutput().convention(List.of());
        getJfrSettings().convention("default");
        getTickEvents().convention(false);
    }

    /** One run's figures. */
    record RunResult(
            int ticks,
            double p50Millis,
            double p95Millis,
            double p99Millis,
            double maxMillis,
            double over50MillisPercent,
            double peakHeapMiB,
            double peakHeapAfterGcMiB,
            int collections,
            double pauseMillis,
            double longestPauseMillis) {

        /** This run as a JSON object. */
        String toJson() {
            return String.format(
                    Locale.ROOT,
                    "{\"ticks\": %d, \"p50Millis\": %.4f, \"p95Millis\": %.4f, \"p99Millis\": %.4f, \"maxMillis\": %.4f,"
                            + " \"over50MillisPercent\": %.4f, \"peakHeapMiB\": %.1f, \"peakHeapAfterGcMiB\": %.1f,"
                            + " \"collections\": %d,"
                            + " \"pauseMillis\": %.2f, \"longestPauseMillis\": %.2f}",
                    ticks,
                    p50Millis,
                    p95Millis,
                    p99Millis,
                    maxMillis,
                    over50MillisPercent,
                    peakHeapMiB,
                    peakHeapAfterGcMiB,
                    collections,
                    pauseMillis,
                    longestPauseMillis);
        }
    }

    @TaskAction
    public void run() throws IOException, InterruptedException {
        MinecraftLaunchSpec spec = MinecraftLaunchSpec.read(getLaunchSpecFile().get().getAsFile().toPath());
        Path work = getWorkDirectory().get().getAsFile().toPath();
        VerifiedGameRunTask.deleteRecursively(work);
        Path probe = getProbeAgent().getSingleFile().toPath().toAbsolutePath();
        String packFormat = packFormat(Path.of(spec.gameJar()));
        int runs = getRuns().get();
        int hold = getHoldTicks().get();
        List<String> programArgs =
                List.of("--universe", "universe", "--tests", "plexus_tick_benchmark:hold", "--packs", "packs");
        Map<String, List<RunResult>> results = new LinkedHashMap<>();
        results.put("vanilla", new ArrayList<>());
        results.put("plexus", new ArrayList<>());
        String reference = getReferenceName().getOrNull();
        Path warmCache = work.resolve("reference-cache");
        if (reference != null) {
            results.put(reference, new ArrayList<>());
        }
        for (int i = -1; i < runs; i++) {
            for (String variant : results.keySet()) {
                Path directory = work.resolve(variant + "-" + i);
                writeWorkload(directory.resolve("packs/plexus_tick_benchmark"), packFormat, hold);
                List<String> extraJvm = new ArrayList<>(heapArguments(getHeapSize().getOrNull()));
                extraJvm.add("-javaagent:" + probe + "=out=" + directory.resolve("ticks.txt").toAbsolutePath()
                        + ",target=" + TICK_TARGET + (getTickEvents().get() ? ",jfr=true" : ""));
                extraJvm.add("-XX:StartFlightRecording=filename=" + directory.resolve("run.jfr").toAbsolutePath()
                        + ",settings=" + getJfrSettings().get() + ",dumponexit=true");
                List<String> command;
                if (variant.equals("vanilla")) {
                    command = BenchmarkLaunch.vanilla(
                            java(), BenchmarkLaunch.Target.GAME_TEST_SERVER, spec, directory, extraJvm, programArgs);
                } else if (variant.equals("plexus")) {
                    command = BenchmarkLaunch.plexus(
                            java(),
                            BenchmarkLaunch.Target.GAME_TEST_SERVER,
                            spec,
                            directory,
                            getPlexusRuntime().getFiles(),
                            getDevModJars().getFiles(),
                            getTransformCacheDirectory().get().getAsFile(),
                            plexusJvm(extraJvm),
                            programArgs);
                } else {
                    NeoForgeServerLaunch.prepareRun(directory, getReferenceModJars().getFiles(), warmCache);
                    command = referenceCommand(directory, extraJvm, programArgs);
                }
                BenchmarkLaunch.run(command, directory, "All 1 required tests passed", 15);
                if (variant.equals("plexus")) {
                    BenchmarkLaunch.requireOutput(directory, getPlexusExpectedOutput().get());
                } else if (variant.equals(reference)) {
                    BenchmarkLaunch.requireOutput(directory, getReferenceExpectedOutput().get());
                    if (i < 0) {
                        NeoForgeServerLaunch.keepCache(directory, warmCache);
                    }
                }
                String output = Files.readString(directory.resolve("output.log"), StandardCharsets.ISO_8859_1);
                if (!output.contains("[tick-probe] timing " + TICK_TARGET.substring(0, TICK_TARGET.indexOf('(')))) {
                    throw new GradleException("The tick probe did not wrap the tick method: " + directory);
                }
                if (!output.contains("Included folder pack")) {
                    throw new GradleException("The workload data pack was not loaded: " + directory);
                }
                RunResult result = analyse(directory);
                if (i >= 0) {
                    results.get(variant).add(result);
                }
                getLogger().lifecycle("{} run {}: {}", variant, i < 0 ? "warm-up" : Integer.toString(i), result);
            }
        }
        String report = report(results, runs, hold);
        Files.writeString(work.resolve("tick-benchmark.json"), report, StandardCharsets.UTF_8);
        getLogger().lifecycle(report);
    }

    /**
     * The reference variant's command: NeoForge's own server start with FML's game test server entry point, and the
     * same log configuration as the other two variants, so that all three log the same way (NeoForge's own
     * configuration also writes a debug log).
     */
    private List<String> referenceCommand(Path directory, List<String> extraJvm, List<String> programArgs)
            throws IOException {
        List<String> jvm = new ArrayList<>();
        jvm.add("-Dlog4j.configurationFile="
                + GameLaunchArguments.developmentLogConfig(directory).getAbsolutePath());
        jvm.addAll(extraJvm);
        return NeoForgeServerLaunch.gameTestServer(
                java(),
                getReferenceServer().get().getAsFile().toPath(),
                getReferenceNeoForgeVersion().get(),
                jvm,
                programArgs);
    }

    /** {@code -Xms} and {@code -Xmx} for a fixed heap of {@code size}, or nothing when {@code size} is null. */
    static List<String> heapArguments(String size) {
        if (size == null) {
            return List.of();
        }
        if (!size.matches("[1-9][0-9]*[kKmMgG]?")) {
            throw new GradleException("heapSize must be a JVM memory size such as 2g or 2048m, not '" + size + "'");
        }
        return List.of("-Xms" + size, "-Xmx" + size);
    }

    private RunResult analyse(Path directory) throws IOException {
        long[] all = Files.readAllLines(directory.resolve("ticks.txt")).stream()
                .filter(line -> !line.isBlank())
                .mapToLong(Long::parseLong)
                .toArray();
        int warmup = getWarmupTicks().get();
        if (all.length < warmup + 100) {
            throw new GradleException("Only " + all.length + " ticks were recorded in " + directory
                    + "; raise holdTicks so a run has at least " + (warmup + 100));
        }
        long[] ticks = Arrays.copyOfRange(all, warmup, all.length);
        Arrays.sort(ticks);
        long over = Arrays.stream(ticks).filter(nanos -> nanos > 50_000_000L).count();
        long peakHeap = 0;
        long peakHeapAfterGc = 0;
        int collections = 0;
        Duration pauses = Duration.ZERO;
        Duration longest = Duration.ZERO;
        for (RecordedEvent event : RecordingFile.readAllEvents(directory.resolve("run.jfr"))) {
            switch (event.getEventType().getName()) {
                case "jdk.GCHeapSummary" -> {
                    long used = event.getLong("heapUsed");
                    peakHeap = Math.max(peakHeap, used);
                    if ("After GC".equals(event.getString("when"))) {
                        peakHeapAfterGc = Math.max(peakHeapAfterGc, used);
                    }
                }
                case "jdk.GarbageCollection" -> {
                    collections++;
                    pauses = pauses.plus(event.getDuration("sumOfPauses"));
                    Duration longestPause = event.getDuration("longestPause");
                    if (longestPause.compareTo(longest) > 0) {
                        longest = longestPause;
                    }
                }
                default -> {}
            }
        }
        return new RunResult(
                ticks.length,
                millis(percentile(ticks, 50)),
                millis(percentile(ticks, 95)),
                millis(percentile(ticks, 99)),
                millis(ticks[ticks.length - 1]),
                100.0 * over / ticks.length,
                peakHeap / (1024.0 * 1024.0),
                peakHeapAfterGc / (1024.0 * 1024.0),
                collections,
                pauses.toNanos() / 1e6,
                longest.toNanos() / 1e6);
    }

    /** Nearest-rank percentile of sorted values. */
    static long percentile(long[] sorted, int percent) {
        int rank = (int) Math.ceil(percent / 100.0 * sorted.length);
        return sorted[Math.max(0, Math.min(sorted.length - 1, rank - 1))];
    }

    private static double millis(long nanos) {
        return nanos / 1e6;
    }

    private String report(Map<String, List<RunResult>> results, int runs, int hold) {
        StringBuilder json = new StringBuilder();
        json.append("{\n  \"scenario\": \"game test server, test plexus_tick_benchmark:hold for ")
                .append(hold)
                .append(" ticks, data pack: 40 pigs and a 16x16x16 glass fill/clear every tick\",\n");
        json.append("  \"runsPerVariant\": ").append(runs).append(",\n  \"warmupRunsDiscarded\": 1,\n");
        json.append("  \"warmupTicksDiscarded\": ").append(getWarmupTicks().get()).append(",\n");
        json.append("  \"heapSize\": ")
                .append(getHeapSize().isPresent() ? "\"" + getHeapSize().get() + "\"" : "\"JVM default\"")
                .append(",\n");
        if (!getJfrSettings().get().equals("default") || getTickEvents().get()) {
            // A profiling run: its tick figures include the profiler's own cost.
            json.append("  \"profiling\": {\"jfrSettings\": \"")
                    .append(getJfrSettings().get().replace('\\', '/'))
                    .append("\", \"tickEvents\": ")
                    .append(getTickEvents().get())
                    .append("},\n");
        }
        json.append("  \"java\": \"")
                .append(getJavaLauncher().get().getMetadata().getJavaRuntimeVersion())
                .append("\",\n");
        if (getReferenceName().isPresent()) {
            json.append("  \"reference\": {\"name\": \"")
                    .append(getReferenceName().get())
                    .append("\", \"neoForge\": \"")
                    .append(getReferenceNeoForgeVersion().get())
                    .append("\", \"mods\": [")
                    .append(String.join(
                            ", ",
                            getReferenceModJars().getFiles().stream()
                                    .map(file -> "\"" + file.getName() + "\"")
                                    .sorted()
                                    .toList()))
                    .append("]},\n");
            json.append("  \"plexusMods\": [")
                    .append(String.join(
                            ", ",
                            getDevModJars().getFiles().stream()
                                    .map(file -> "\"" + file.getName() + "\"")
                                    .sorted()
                                    .toList()))
                    .append("],\n");
        }
        int variants = 0;
        for (Map.Entry<String, List<RunResult>> entry : results.entrySet()) {
            List<RunResult> list = entry.getValue();
            json.append("  \"").append(entry.getKey()).append("\": {\n");
            json.append("    \"runs\": [\n      ")
                    .append(String.join(",\n      ", list.stream().map(RunResult::toJson).toList()))
                    .append("\n    ],\n");
            json.append(String.format(
                    Locale.ROOT,
                    "    \"median\": {\"ticks\": %.0f, \"p50Millis\": %.3f, \"p95Millis\": %.3f, \"p99Millis\": %.3f,"
                            + " \"maxMillis\": %.3f, \"over50MillisPercent\": %.3f, \"peakHeapMiB\": %.1f,"
                            + " \"peakHeapAfterGcMiB\": %.1f, \"collections\": %.0f, \"pauseMillis\": %.1f, \"longestPauseMillis\": %.2f},\n",
                    median(list, run -> run.ticks()),
                    median(list, RunResult::p50Millis),
                    median(list, RunResult::p95Millis),
                    median(list, RunResult::p99Millis),
                    median(list, RunResult::maxMillis),
                    median(list, RunResult::over50MillisPercent),
                    median(list, RunResult::peakHeapMiB),
                    median(list, RunResult::peakHeapAfterGcMiB),
                    median(list, run -> run.collections()),
                    median(list, RunResult::pauseMillis),
                    median(list, RunResult::longestPauseMillis)));
            json.append(String.format(
                    Locale.ROOT,
                    "    \"stdDev\": {\"p50Millis\": %.3f, \"p95Millis\": %.3f, \"p99Millis\": %.3f, \"peakHeapMiB\": %.1f,"
                            + " \"peakHeapAfterGcMiB\": %.1f, \"collections\": %.1f, \"pauseMillis\": %.1f}\n",
                    stdDev(list, RunResult::p50Millis),
                    stdDev(list, RunResult::p95Millis),
                    stdDev(list, RunResult::p99Millis),
                    stdDev(list, RunResult::peakHeapMiB),
                    stdDev(list, RunResult::peakHeapAfterGcMiB),
                    stdDev(list, run -> run.collections()),
                    stdDev(list, RunResult::pauseMillis)));
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

    /**
     * The workload data pack. The load function force-loads the area and builds a walled pen; the
     * tick function keeps 40 pigs in it and fills, then clears, a cube of glass. Glass and air have
     * the same light properties, so the fill queues no lighting work: with stone, the light engine
     * fell behind and its queue grew for the whole run, which made the heap figures measure that race
     * instead of the loader.
     */
    static void writeWorkload(Path pack, String packFormat, int holdTicks) throws IOException {
        write(pack.resolve("pack.mcmeta"), "{\"pack\": {\"description\": \"Plexus Mod Loader tick benchmark workload\", "
                + packFormat + "}}\n");
        write(
                pack.resolve("data/minecraft/tags/function/load.json"),
                "{\"values\": [\"plexus_tick_benchmark:load\"]}\n");
        write(
                pack.resolve("data/minecraft/tags/function/tick.json"),
                "{\"values\": [\"plexus_tick_benchmark:tick\"]}\n");
        // Waits holdTicks before running the built-in always_pass test function, so the run lasts.
        write(
                pack.resolve("data/plexus_tick_benchmark/test_instance/hold.json"),
                "{\"type\": \"minecraft:function\", \"environment\": \"minecraft:default\", "
                        + "\"function\": \"minecraft:always_pass\", \"structure\": \"minecraft:empty\", "
                        + "\"setup_ticks\": " + holdTicks + ", \"max_ticks\": 1, \"required\": true}\n");
        write(pack.resolve("data/plexus_tick_benchmark/function/load.mcfunction"), """
                forceload add -16 -16 63 63
                scoreboard objectives add plexus_bench dummy
                fill 30 99 30 50 99 50 minecraft:grass_block
                fill 29 100 29 51 102 51 minecraft:glass hollow
                """);
        write(pack.resolve("data/plexus_tick_benchmark/function/tick.mcfunction"), """
                execute store result score #pigs plexus_bench if entity @e[type=minecraft:pig]
                execute if score #pigs plexus_bench matches ..39 run summon minecraft:pig 40 100 40
                fill 0 100 0 15 115 15 minecraft:glass replace minecraft:air
                fill 0 100 0 15 115 15 minecraft:air replace minecraft:glass
                """);
    }

    private static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    /** {@code min_format}/{@code max_format} for the game's data pack version, from its version.json. */
    static String packFormat(Path gameJar) throws IOException {
        try (JarFile jar = new JarFile(gameJar.toFile())) {
            ZipEntry entry = jar.getEntry("version.json");
            if (entry == null) {
                throw new GradleException("version.json is missing from " + gameJar);
            }
            try (InputStream input = jar.getInputStream(entry)) {
                JsonObject version = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8))
                        .getAsJsonObject()
                        .getAsJsonObject("pack_version");
                int major = version.get("data_major").getAsInt();
                int minor = version.get("data_minor").getAsInt();
                return "\"min_format\": [" + major + ", " + minor + "], \"max_format\": " + major;
            }
        }
    }

    private String java() {
        return getJavaLauncher().get().getExecutablePath().getAsFile().getAbsolutePath();
    }
}
