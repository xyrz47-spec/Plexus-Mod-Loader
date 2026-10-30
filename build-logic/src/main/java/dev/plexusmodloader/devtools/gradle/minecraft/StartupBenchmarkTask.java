package dev.plexusmodloader.devtools.gradle.minecraft;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.gradle.api.DefaultTask;
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
 * Measures wall-clock time from JVM start to exit of Mojang's game test server running the built-in
 * {@code always_pass} test, for vanilla (game jar on the class path, no Poly Mod Loader) and for Poly Mod Loader with the
 * configured mods. Runs are interleaved (vanilla, Poly Mod Loader, vanilla, …) after one discarded warm-up of
 * each, every run in a fresh directory, and the raw times are written next to the summary so the
 * numbers can be re-analysed (design doc §14.4). Each run also notes when the server is ready: the first output line
 * {@value #READY_MARKER} (Mojang's {@code GameTestRunner}, after the world is created and before the first test).
 *
 * <p>With {@link #getReferenceName()}, a third variant runs in the same rotation: a real NeoForge installation started
 * by NeoForge's own argument files on FML's game test server entry point, with {@link #getReferenceModJars()} (for
 * Sinytra Connector and the Fabric mods it runs), and the same log configuration. Its warm-up run's game-folder cache
 * is copied into each of its measured runs, as Poly Mod Loader's runs share their transform cache.
 */
@DisableCachingByDefault(because = "Benchmarks must run every time")
public abstract class StartupBenchmarkTask extends DefaultTask {

    /** The line that marks a game test server as ready (vanilla code, so the same in every variant). */
    static final String READY_MARKER = "Running test environment";

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getLaunchSpecFile();

    @Classpath
    public abstract ConfigurableFileCollection getPolyRuntime();

    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getDevModJars();

    @Nested
    public abstract Property<JavaLauncher> getJavaLauncher();

    /**
     * Java agents for the Poly Mod Loader variant only ({@code -javaagent:<jar>}), such as Poly Mod Loader's own agent,
     * which NeoForge needs (ADR-0026) and the launcher profile starts.
     */
    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getPolyJavaAgents();

    @Input
    public abstract Property<Integer> getRuns();

    @Internal
    public abstract DirectoryProperty getWorkDirectory();

    @Internal
    public abstract DirectoryProperty getTransformCacheDirectory();

    /**
     * A fixed heap size for every variant, such as {@code 4g} ({@code -Xms} = {@code -Xmx}); when absent, the JVM picks
     * its default heap.
     */
    @Input
    @Optional
    public abstract Property<String> getHeapSize();

    /** The name of the reference variant, such as {@code connector}; when absent, only vanilla and Poly Mod Loader. */
    @Input
    @Optional
    public abstract Property<String> getReferenceName();

    /** The installed NeoForge server folder of the reference variant ({@link NeoForgeServerInstallTask}). */
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

    /** Lines every Poly Mod Loader run's output must have (which mods loaded). */
    @Input
    public abstract ListProperty<String> getPolyExpectedOutput();

    /** Lines every reference run's output must have (which mods loaded). */
    @Input
    public abstract ListProperty<String> getReferenceExpectedOutput();

    public StartupBenchmarkTask() {
        getPolyExpectedOutput().convention(List.of());
        getReferenceExpectedOutput().convention(List.of());
    }

    @TaskAction
    public void run() throws IOException, InterruptedException {
        MinecraftLaunchSpec spec = MinecraftLaunchSpec.read(getLaunchSpecFile().get().getAsFile().toPath());
        Path work = getWorkDirectory().get().getAsFile().toPath();
        VerifiedGameRunTask.deleteRecursively(work);
        Path warmCache = work.resolve("reference-cache");
        String reference = getReferenceName().getOrNull();
        Map<String, List<BenchmarkLaunch.Timing>> measured = new LinkedHashMap<>();
        Map<String, BenchmarkLaunch.Timing> warmups = new LinkedHashMap<>();
        measured.put("vanilla", new ArrayList<>());
        measured.put("poly", new ArrayList<>());
        if (reference != null) {
            measured.put(reference, new ArrayList<>());
        }
        int runs = getRuns().get();
        for (int i = -1; i < runs; i++) {
            for (String variant : measured.keySet()) {
                Path directory = work.resolve(variant + "-" + i);
                List<String> heap = TickBenchmarkTask.heapArguments(getHeapSize().getOrNull());
                List<String> command;
                if (variant.equals("vanilla")) {
                    command = vanillaCommand(spec, directory, heap);
                } else if (variant.equals("poly")) {
                    command = polyCommand(spec, directory, heap);
                } else {
                    NeoForgeServerLaunch.prepareRun(directory, getReferenceModJars().getFiles(), warmCache);
                    command = referenceCommand(directory, heap);
                }
                BenchmarkLaunch.Timing timing = BenchmarkLaunch.runTimed(
                        command, directory, "All 1 required tests passed", READY_MARKER, 10);
                if (variant.equals("poly")) {
                    BenchmarkLaunch.requireOutput(directory, getPolyExpectedOutput().get());
                } else if (variant.equals(reference)) {
                    BenchmarkLaunch.requireOutput(directory, getReferenceExpectedOutput().get());
                    if (i < 0) {
                        NeoForgeServerLaunch.keepCache(directory, warmCache);
                    }
                }
                getLogger()
                        .lifecycle(
                                "{} run {}: {}",
                                variant,
                                i < 0 ? "warm-up" : Integer.toString(i),
                                String.format(
                                        Locale.ROOT,
                                        "%.3f s to exit, %.3f s to ready",
                                        timing.seconds(),
                                        timing.readySeconds()));
                if (i < 0) {
                    warmups.put(variant, timing);
                } else {
                    measured.get(variant).add(timing);
                }
            }
        }
        StringBuilder extra = new StringBuilder();
        if (reference != null) {
            extra.append(String.format(
                    Locale.ROOT,
                    "  \"reference\": {\"name\": \"%s\", \"neoForge\": \"%s\", \"mods\": %s},\n  \"polyMods\": %s,\n",
                    reference,
                    getReferenceNeoForgeVersion().get(),
                    names(getReferenceModJars().getFiles()),
                    names(getDevModJars().getFiles())));
        }
        String report = report(
                measured,
                warmups,
                runs,
                getJavaLauncher().get().getMetadata().getJavaRuntimeVersion(),
                getHeapSize().getOrElse("JVM default"),
                extra.toString());
        Files.writeString(work.resolve("startup-benchmark.json"), report, StandardCharsets.UTF_8);
        getLogger().lifecycle(report);
    }

    /**
     * The summary: per variant the measured times to exit ({@code <variant>Seconds}, {@code Median}, {@code StdDev}),
     * the times to ready ({@code <variant>ReadySeconds}, …), and the discarded warm-up run.
     */
    static String report(
            Map<String, List<BenchmarkLaunch.Timing>> measured,
            Map<String, BenchmarkLaunch.Timing> warmups,
            int runs,
            String java,
            String heap,
            String extra) {
        StringBuilder json = new StringBuilder();
        json.append("{\n  \"scenario\": \"game test server, minecraft:always_pass, fresh world\",\n");
        json.append("  \"runsPerVariant\": ").append(runs).append(",\n  \"warmupRunsDiscarded\": 1,\n");
        json.append("  \"readyMarker\": \"").append(READY_MARKER).append("\",\n");
        json.append("  \"heapSize\": \"").append(heap).append("\",\n");
        json.append(extra);
        for (Map.Entry<String, List<BenchmarkLaunch.Timing>> entry : measured.entrySet()) {
            String variant = entry.getKey();
            List<Double> exit = entry.getValue().stream()
                    .map(BenchmarkLaunch.Timing::seconds)
                    .toList();
            List<Double> ready = entry.getValue().stream()
                    .map(BenchmarkLaunch.Timing::readySeconds)
                    .toList();
            BenchmarkLaunch.Timing warmup = warmups.get(variant);
            json.append(String.format(
                    Locale.ROOT,
                    "  \"%1$sSeconds\": %2$s,\n  \"%1$sMedian\": %3$.3f,\n  \"%1$sStdDev\": %4$.3f,\n"
                            + "  \"%1$sReadySeconds\": %5$s,\n  \"%1$sReadyMedian\": %6$.3f,\n"
                            + "  \"%1$sReadyStdDev\": %7$.3f,\n  \"%1$sWarmup\": {\"seconds\": %8$.3f, \"readySeconds\": %9$.3f},\n",
                    variant,
                    list(exit),
                    BenchmarkLaunch.median(exit),
                    BenchmarkLaunch.stdDev(exit),
                    list(ready),
                    BenchmarkLaunch.median(ready),
                    BenchmarkLaunch.stdDev(ready),
                    warmup == null ? Double.NaN : warmup.seconds(),
                    warmup == null ? Double.NaN : warmup.readySeconds()));
        }
        json.append("  \"java\": \"").append(java).append("\"\n}\n");
        return json.toString();
    }

    private static String list(List<Double> values) {
        return values.stream()
                .map(value -> String.format(Locale.ROOT, "%.3f", value))
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private static String names(Collection<File> files) {
        return files.stream()
                .map(file -> "\"" + file.getName() + "\"")
                .sorted()
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private static final List<String> PROGRAM_ARGUMENTS =
            List.of("--universe", "universe", "--tests", "minecraft:always_pass");

    private List<String> vanillaCommand(MinecraftLaunchSpec spec, Path directory, List<String> heap)
            throws IOException {
        return BenchmarkLaunch.vanilla(
                java(), BenchmarkLaunch.Target.GAME_TEST_SERVER, spec, directory, heap, PROGRAM_ARGUMENTS);
    }

    private List<String> polyCommand(MinecraftLaunchSpec spec, Path directory, List<String> heap)
            throws IOException {
        List<String> polyJvm = new ArrayList<>(BenchmarkLaunch.javaAgents(getPolyJavaAgents().getFiles()));
        polyJvm.addAll(heap);
        return BenchmarkLaunch.poly(
                java(),
                BenchmarkLaunch.Target.GAME_TEST_SERVER,
                spec,
                directory,
                getPolyRuntime().getFiles(),
                getDevModJars().getFiles(),
                getTransformCacheDirectory().get().getAsFile(),
                polyJvm,
                PROGRAM_ARGUMENTS);
    }

    /** NeoForge's own start on FML's game test server entry point, with the same log configuration as the others. */
    private List<String> referenceCommand(Path directory, List<String> heap) throws IOException {
        List<String> jvm = new ArrayList<>();
        jvm.add("-Dlog4j.configurationFile="
                + GameLaunchArguments.developmentLogConfig(directory).getAbsolutePath());
        jvm.addAll(heap);
        return NeoForgeServerLaunch.gameTestServer(
                java(),
                getReferenceServer().get().getAsFile().toPath(),
                getReferenceNeoForgeVersion().get(),
                jvm,
                PROGRAM_ARGUMENTS);
    }

    private String java() {
        return getJavaLauncher().get().getExecutablePath().getAsFile().getAbsolutePath();
    }
}
