package dev.plexusmodloader.devtools.gradle.minecraft;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
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
 * Runs real Quilt Loader, not Poly Mod Loader, on the vanilla dedicated server, to compare which mods Quilt Loader
 * itself loads (Quilt full support Q10, ADR-0027). Quilt Loader's own server main ({@code KnotServer}) starts with the
 * libraries of Quilt's launcher profile and Minecraft's, the game jar named by {@code loader.gameJarPath}, and a
 * {@code mods/} folder; a run that reaches the EULA check got through Quilt Loader's mod resolution, its mixins and
 * its mods' {@code main} entrypoints. Quilt Loader's error window is turned off, and the EULA is never accepted.
 *
 * <p>The first run has {@link #getBase()} alone, and must reach the EULA check. Then each jar of {@link
 * #getEachAlone()} runs once with the base. The report lists every run's outcome and Quilt Loader's own error lines.
 */
@DisableCachingByDefault(because = "Runs the game")
public abstract class QuiltLoaderReferenceTask extends DefaultTask {

    static final String MAIN_CLASS = "org.quiltmc.loader.impl.launch.knot.KnotServer";
    static final String EULA_LINE = "You need to agree to the EULA";

    /** The prepared server's launch spec: Minecraft's libraries and the game jar. */
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getLaunchSpecFile();

    /** Quilt Loader and the libraries its launcher profile lists. */
    @InputFiles
    @PathSensitive(PathSensitivity.NAME_ONLY)
    public abstract ConfigurableFileCollection getQuiltLoaderClasspath();

    /** Jars in every run. */
    @InputFiles
    @PathSensitive(PathSensitivity.NAME_ONLY)
    public abstract ConfigurableFileCollection getBase();

    /** Jars each run once on top of {@link #getBase()}. */
    @InputFiles
    @PathSensitive(PathSensitivity.NAME_ONLY)
    public abstract ConfigurableFileCollection getEachAlone();

    @Nested
    public abstract Property<JavaLauncher> getJavaLauncher();

    @Internal
    public abstract DirectoryProperty getRunDirectory();

    @Internal
    public abstract RegularFileProperty getReportFile();

    @Input
    public abstract Property<Integer> getTimeoutSeconds();

    public QuiltLoaderReferenceTask() {
        getTimeoutSeconds().convention(240);
    }

    @TaskAction
    public void run() throws IOException, InterruptedException {
        MinecraftLaunchSpec spec = MinecraftLaunchSpec.read(getLaunchSpecFile().get().getAsFile().toPath());
        Path runs = getRunDirectory().get().getAsFile().toPath();
        VerifiedGameRunTask.deleteRecursively(runs);
        List<File> base = sorted(getBase().getFiles());
        List<String> report = new ArrayList<>();
        report.add("# Real Quilt Loader on the vanilla " + spec.gameVersion() + " dedicated server");
        report.add("# base: " + base.size() + " jar(s): "
                + base.stream().map(File::getName).collect(Collectors.joining(", ")));
        Outcome baseOutcome = runOnce(spec, runs.resolve("base"), base);
        report.add("base\t" + baseOutcome.line());
        if (!baseOutcome.started()) {
            Files.createDirectories(getReportFile().get().getAsFile().toPath().getParent());
            Files.writeString(getReportFile().get().getAsFile().toPath(), String.join("\n", report) + "\n");
            throw new GradleException("Quilt Loader did not reach the EULA check with the base jars alone: "
                    + baseOutcome.summary() + ". Log: " + runs.resolve("base").resolve("output.log"));
        }
        int index = 0;
        for (File jar : sorted(getEachAlone().getFiles())) {
            List<File> mods = new ArrayList<>(base);
            mods.add(jar);
            Outcome outcome = runOnce(spec, runs.resolve(String.format("%03d", ++index)), mods);
            report.add(jar.getName() + "\t" + outcome.line());
            getLogger().lifecycle("Quilt Loader with {}: {}", jar.getName(), outcome.line());
        }
        Path reportFile = getReportFile().get().getAsFile().toPath();
        Files.createDirectories(reportFile.getParent());
        Files.writeString(reportFile, String.join("\n", report) + "\n", StandardCharsets.UTF_8);
        getLogger().lifecycle("Quilt Loader reference: {} run(s), report {}", index + 1, reportFile);
    }

    private Outcome runOnce(MinecraftLaunchSpec spec, Path directory, List<File> mods)
            throws IOException, InterruptedException {
        Path modsFolder = directory.resolve("mods");
        Files.createDirectories(modsFolder);
        for (File mod : mods) {
            Files.copy(mod.toPath(), modsFolder.resolve(mod.getName()), StandardCopyOption.REPLACE_EXISTING);
        }
        List<String> classpath = new ArrayList<>();
        getQuiltLoaderClasspath().getFiles().forEach(file -> classpath.add(file.getAbsolutePath()));
        classpath.addAll(spec.libraries());
        List<String> command = new ArrayList<>();
        command.add(getJavaLauncher().get().getExecutablePath().getAsFile().getAbsolutePath());
        command.add("-Xmx2G");
        command.add("-Djava.awt.headless=true");
        command.add("-Dloader.noGui=true");
        command.add("-Dloader.disable_forked_guis=true");
        command.add("-Dloader.gameJarPath=" + spec.gameJar());
        command.add("-cp");
        command.add(String.join(File.pathSeparator, classpath));
        command.add(MAIN_CLASS);
        command.add("nogui");
        Process process = new ProcessBuilder(command)
                .directory(directory.toFile())
                .redirectErrorStream(true)
                .start();
        process.getOutputStream().close();
        byte[][] output = new byte[1][];
        Thread reader = Thread.ofPlatform().start(() -> {
            try (InputStream in = process.getInputStream()) {
                output[0] = in.readAllBytes();
            } catch (IOException e) {
                output[0] = ("output unreadable: " + e).getBytes(StandardCharsets.UTF_8);
            }
        });
        boolean ended = process.waitFor(getTimeoutSeconds().get(), TimeUnit.SECONDS);
        if (!ended) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            process.waitFor(30, TimeUnit.SECONDS);
        }
        reader.join(10_000);
        String log = output[0] == null ? "" : new String(output[0], StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("output.log"), log, StandardCharsets.UTF_8);
        return Outcome.of(log, ended, crashReports(directory));
    }

    private static String crashReports(Path directory) throws IOException {
        Path folder = directory.resolve("crash-reports");
        if (!Files.isDirectory(folder)) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        try (Stream<Path> files = Files.list(folder)) {
            for (Path file : files.sorted().toList()) {
                text.append(Files.readString(file, StandardCharsets.UTF_8)).append('\n');
            }
        }
        return text.toString();
    }

    private static List<File> sorted(java.util.Set<File> files) {
        return files.stream().sorted(Comparator.comparing(File::getName)).toList();
    }

    /** What one run did: reached the EULA check, or stopped with Quilt Loader's error lines. */
    record Outcome(boolean started, String summary) {

        /** Quilt Loader's count of the mods it loads (built-in ones and nested ones included). */
        private static final Pattern LOADING = Pattern.compile("Loading (\\d+) mods");

        static Outcome of(String log, boolean ended, String crashReports) {
            if (log.contains(EULA_LINE)) {
                Matcher loading = LOADING.matcher(log);
                return new Outcome(
                        true, loading.find() ? "loads " + loading.group(1) + " mods, reaches the EULA check" : "reaches the EULA check");
            }
            List<String> errors = new ArrayList<>();
            for (String line : (log + "\n" + crashReports).lines().toList()) {
                String text = line.strip();
                if (text.isEmpty() || errors.contains(text)) {
                    continue;
                }
                if (text.contains("ERROR")
                        || text.contains("Exception")
                        || text.startsWith("Error:")
                        || text.contains("requires")
                        || text.contains("which is missing")
                        || text.contains("Broken mod")
                        || text.contains("Unhandled")) {
                    errors.add(text);
                }
                if (errors.size() == 8) {
                    break;
                }
            }
            String why = errors.isEmpty() ? "no error line" : String.join(" | ", errors);
            return new Outcome(false, (ended ? "stops before the EULA check: " : "killed after the timeout: ") + why);
        }

        String line() {
            return (started ? "STARTS\t" : "STOPS\t") + summary;
        }
    }
}
