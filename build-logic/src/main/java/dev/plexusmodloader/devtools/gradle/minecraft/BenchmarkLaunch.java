package dev.plexusmodloader.devtools.gradle.minecraft;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.gradle.api.GradleException;

/**
 * Builds and runs the two variants every benchmark compares: vanilla (game jar on the class path, no
 * Poly Mod Loader) and Poly Mod Loader with the development mods (design doc §14.4). Both get the same extra JVM
 * and program arguments.
 */
final class BenchmarkLaunch {

    private BenchmarkLaunch() {}

    /** What a benchmark run starts: the vanilla main class and the matching Poly Mod Loader launch target. */
    enum Target {
        GAME_TEST_SERVER("net.minecraft.gametest.Main", "game_test_server"),
        CLIENT("net.minecraft.client.main.Main", "client");

        final String vanillaMainClass;
        final String polyLaunchTarget;

        Target(String vanillaMainClass, String polyLaunchTarget) {
            this.vanillaMainClass = vanillaMainClass;
            this.polyLaunchTarget = polyLaunchTarget;
        }
    }

    /** The vanilla command: {@code target} straight from the game jar. */
    static List<String> vanilla(
            String java,
            Target target,
            MinecraftLaunchSpec spec,
            Path directory,
            List<String> extraJvm,
            List<String> programArgs)
            throws IOException {
        List<String> command = new ArrayList<>();
        command.add(java);
        command.addAll(spec.jvmArguments());
        command.add("-Dlog4j.configurationFile="
                + GameLaunchArguments.developmentLogConfig(directory).getAbsolutePath());
        command.addAll(extraJvm);
        command.add("-cp");
        List<String> classpath = new ArrayList<>(spec.libraries());
        classpath.add(spec.gameJar());
        command.add(String.join(File.pathSeparator, classpath));
        command.add(target.vanillaMainClass);
        command.addAll(programArgs);
        return command;
    }

    /** {@code -javaagent:<jar>} for each of {@code agents}. */
    static List<String> javaAgents(Collection<File> agents) {
        List<String> arguments = new ArrayList<>();
        agents.forEach(agent -> arguments.add("-javaagent:" + agent.getAbsolutePath()));
        return arguments;
    }

    /** The Poly Mod Loader command: the bootstrap with the development mods and a shared transform cache. */
    static List<String> poly(
            String java,
            Target target,
            MinecraftLaunchSpec spec,
            Path directory,
            Collection<File> polyRuntime,
            Collection<File> devModJars,
            File transformCache,
            List<String> extraJvm,
            List<String> programArgs)
            throws IOException {
        List<String> command = new ArrayList<>();
        command.add(java);
        command.addAll(GameLaunchArguments.jvmArguments(
                spec,
                target.polyLaunchTarget,
                directory,
                devModJars,
                GameLaunchArguments.developmentLogConfig(directory),
                transformCache));
        command.addAll(extraJvm);
        command.add("-cp");
        List<String> classpath = new ArrayList<>();
        polyRuntime.forEach(file -> classpath.add(file.getAbsolutePath()));
        classpath.addAll(spec.libraries());
        // The game jar too, last, as the Minecraft Launcher and the server install put it (see RunMinecraftTask).
        classpath.add(spec.gameJar());
        command.add(String.join(File.pathSeparator, classpath));
        command.add(RunMinecraftTask.BOOTSTRAP_MAIN_CLASS);
        command.addAll(programArgs);
        return command;
    }

    /**
     * Runs {@code command} in {@code directory} and returns the wall-clock seconds.
     *
     * @throws GradleException when the run times out, exits with an error, or its output lacks {@code
     *     mustContain}
     */
    static double run(List<String> command, Path directory, String mustContain, int timeoutMinutes)
            throws IOException, InterruptedException {
        Files.createDirectories(directory.resolve("mods"));
        Path log = directory.resolve("output.log");
        long start = System.nanoTime();
        Process process = new ProcessBuilder(command)
                .directory(directory.toFile())
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start();
        if (!process.waitFor(timeoutMinutes, TimeUnit.MINUTES)) {
            process.destroyForcibly();
            throw new GradleException("Benchmark run timed out: " + directory);
        }
        double seconds = (System.nanoTime() - start) / 1e9;
        String output = Files.readString(log, StandardCharsets.UTF_8);
        if (process.exitValue() != 0 || !output.contains(mustContain)) {
            throw new GradleException("Benchmark run failed (exit " + process.exitValue() + ", expected '"
                    + mustContain + "' in the output): " + log);
        }
        return seconds;
    }

    /**
     * One run's wall-clock times.
     *
     * @param seconds from start to exit
     * @param readySeconds from start to the first output line with the ready marker
     */
    record Timing(double seconds, double readySeconds) {}

    /**
     * Like {@link #run}, but reads the output itself (into {@code output.log}) and also notes when the first line with
     * {@code readyMarker} appears. Every variant of one comparison must be run the same way.
     *
     * @throws GradleException as {@link #run} does, and when no line has {@code readyMarker}
     */
    static Timing runTimed(
            List<String> command, Path directory, String mustContain, String readyMarker, int timeoutMinutes)
            throws IOException, InterruptedException {
        Files.createDirectories(directory.resolve("mods"));
        Path log = directory.resolve("output.log");
        long start = System.nanoTime();
        Process process = new ProcessBuilder(command)
                .directory(directory.toFile())
                .redirectErrorStream(true)
                .start();
        long[] ready = {-1};
        // ISO-8859-1 keeps every byte as it is; the markers are ASCII.
        Thread pump = Thread.ofPlatform().daemon().name("benchmark-output").start(() -> {
            try (var reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(process.getInputStream(), StandardCharsets.ISO_8859_1));
                    var writer = Files.newBufferedWriter(log, StandardCharsets.ISO_8859_1)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (ready[0] < 0 && line.contains(readyMarker)) {
                        ready[0] = System.nanoTime();
                    }
                    writer.write(line);
                    writer.newLine();
                }
            } catch (IOException e) {
                throw new java.io.UncheckedIOException("Failed to copy the output to " + log, e);
            }
        });
        if (!process.waitFor(timeoutMinutes, TimeUnit.MINUTES)) {
            process.destroyForcibly();
            throw new GradleException("Benchmark run timed out: " + directory);
        }
        double seconds = (System.nanoTime() - start) / 1e9;
        pump.join(TimeUnit.MINUTES.toMillis(1));
        String output = Files.readString(log, StandardCharsets.ISO_8859_1);
        if (process.exitValue() != 0 || !output.contains(mustContain)) {
            throw new GradleException("Benchmark run failed (exit " + process.exitValue() + ", expected '"
                    + mustContain + "' in the output): " + log);
        }
        if (ready[0] < 0) {
            throw new GradleException("Benchmark run never printed '" + readyMarker + "': " + log);
        }
        return new Timing(seconds, (ready[0] - start) / 1e9);
    }

    /**
     * Checks that {@code output.log} of a run has every expected line, so that a comparison does not silently measure
     * a different set of mods after one stopped loading.
     */
    static void requireOutput(Path directory, List<String> expected) throws IOException {
        String output = Files.readString(directory.resolve("output.log"), StandardCharsets.ISO_8859_1);
        List<String> missing =
                expected.stream().filter(line -> !output.contains(line)).toList();
        if (!missing.isEmpty()) {
            throw new GradleException("Benchmark run " + directory + " lacks the expected output " + missing);
        }
    }

    static double median(List<Double> values) {
        List<Double> sorted = values.stream().sorted().toList();
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(middle) : (sorted.get(middle - 1) + sorted.get(middle)) / 2;
    }

    static double stdDev(List<Double> values) {
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        return Math.sqrt(
                values.stream().mapToDouble(v -> (v - mean) * (v - mean)).sum() / Math.max(1, values.size() - 1));
    }
}
