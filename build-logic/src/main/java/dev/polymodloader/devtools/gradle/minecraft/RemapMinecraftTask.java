package dev.polymodloader.devtools.gradle.minecraft;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;
import org.gradle.jvm.toolchain.JavaLauncher;

/**
 * {@code remapMinecraft -Pminecraft=<obfuscated version> [-Pside=server]}: remaps the client (or server) jar of
 * a version before 26.1 to
 * Mojang names ({@link MojangJarRemapper}) and runs the self-check on it ({@link RemappedJarCheck}, then
 * {@link RemappedJarLoadCheck} in its own JVM), as the first step of ADR-0019.
 *
 * <p>The remapped jar is kept in the shared cache, next to the jar it comes from, keyed by the SHA-1 of that
 * jar and of the mappings and by {@link MojangJarRemapper#REVISION}. It is never written into the
 * repository (ADR-0019 point 3). The report goes to {@code build/reports/minecraft-remap/}. The task fails if
 * the self-check finds a problem.
 */
@UntrackedTask(because = "The remapped jar lives in the shared cache with its own key; the checks are the point.")
public abstract class RemapMinecraftTask extends DefaultTask {

    /** The obfuscated Minecraft version, such as {@code 1.21.11}. */
    @Input
    public abstract Property<String> getMinecraftVersion();

    /** Shared download cache, the same one the compile and run tasks use. */
    @Internal
    public abstract DirectoryProperty getCacheDirectory();

    /** Optional {@code MinecraftFiles/} folder with hand-copied game files. */
    @Internal
    public abstract DirectoryProperty getLocalFilesDirectory();

    /** {@code client} (the default) or {@code server} (the server jar out of the bundler, 1.18 and later). */
    @Input
    public abstract Property<String> getSide();

    /** The Java that runs the load check: the one every version runs on (ADR-0019 point 5). */
    @Nested
    public abstract Property<JavaLauncher> getJavaLauncher();

    /** The Markdown report. */
    @OutputFile
    public abstract RegularFileProperty getReportFile();

    @TaskAction
    public void remap() {
        String version = getMinecraftVersion().getOrNull();
        if (version == null) {
            throw new IllegalArgumentException(
                    "Name the version to remap: remapMinecraft -Pminecraft=<version before 26.1>, such as 1.21.11.");
        }
        Path cache = getCacheDirectory().get().getAsFile().toPath();
        Path localFiles = getLocalFilesDirectory().isPresent()
                ? getLocalFilesDirectory().get().getAsFile().toPath()
                : null;
        MinecraftDistributionInstaller installer =
                new MinecraftDistributionInstaller(cache, LocalMinecraftFiles.at(localFiles, getLogger()), getLogger());
        String side = getSide().get();
        Path versionDir = cache.resolve("versions").resolve(version);
        Path original;
        Path mappingsFile;
        List<Path> libraries;
        switch (side) {
            case "client" -> {
                MinecraftDistributionInstaller.ClientFiles files = installer.clientFiles(version);
                original = files.clientJar();
                mappingsFile = files.clientMappings().orElseThrow(() -> notObfuscated(version, "client_mappings"));
                libraries = installer.clientLibraries(version);
            }
            case "server" -> {
                MinecraftDistributionInstaller.ServerFiles files = installer.serverFiles(version);
                original = files.serverJar();
                mappingsFile = files.serverMappings().orElseThrow(() -> notObfuscated(version, "server_mappings"));
                libraries = files.libraries();
            }
            default -> throw new IllegalArgumentException("-Pside=" + side + ": expected client or server.");
        }
        String jarSha1 = VerifiedDownloader.hash(original, "SHA-1");
        String mappingsSha1 = VerifiedDownloader.hash(mappingsFile, "SHA-1");
        MojangMappings mappings = MojangMappings.read(mappingsFile);

        // The same file the installer gives version modules and runs.
        Path remapped = versionDir.resolve(side + "-" + version + "-mojang.jar");
        RemappedGameJar made = RemappedGameJar.ensure(original, mappingsFile, libraries, remapped, getLogger());
        MojangJarRemapper.Result remap = made.result();
        long remapMillis = made.remapMillis();
        String determinism;
        if (remap == null) {
            getLogger().lifecycle("Using the remapped jar {} (same jar, mappings and remapper).", remapped);
            determinism = "not checked (cached jar reused)";
        } else {
            Path second = versionDir.resolve(side + "-" + version + "-mojang.second.jar");
            MojangJarRemapper.remap(original, mappings, libraries, second);
            boolean same = VerifiedDownloader.hash(second, "SHA-1").equals(VerifiedDownloader.hash(remapped, "SHA-1"));
            delete(second);
            if (!same) {
                delete(remapped);
                throw new IllegalStateException("Remapping Minecraft " + version + " twice gave different jars; the"
                        + " remapper is not deterministic.");
            }
            determinism = "same SHA-1 on two runs";
        }
        String remappedSha1 = VerifiedDownloader.hash(remapped, "SHA-1");

        RemappedJarCheck.Result check = RemappedJarCheck.check(remapped, mappings, libraries);
        LoadResult load = loadCheck(remapped, libraries, check.classes(), mappings);

        Path reportFile = getReportFile().get().getAsFile().toPath();
        write(reportFile, report(version, side, jarSha1, mappingsSha1, remappedSha1, remapMillis, determinism, remap, check, load));
        boolean passed = check.passed()
                && load.failures().isEmpty()
                && load.loaded() + load.otherOsLibraries().size() == check.classCount();
        getLogger()
                .lifecycle(
                        "Minecraft {} {} remapped: {} classes; default-package left {}, duplicate members {}, unresolved"
                                + " references {} of {}; {} of {} classes loaded and linked on Java {} ({} need a"
                                + " library of another OS). {}",
                        version,
                        side,
                        check.classCount(),
                        check.defaultPackageClasses().size(),
                        check.duplicateMembers().size(),
                        check.unresolvedReferenceCount(),
                        check.referenceCount(),
                        load.loaded(),
                        check.classCount(),
                        getJavaLauncher().get().getMetadata().getLanguageVersion(),
                        load.otherOsLibraries().size(),
                        passed ? "Self-check passed." : "Self-check FAILED.");
        getLogger().lifecycle("Remapped jar (local cache only): {}", remapped);
        getLogger().lifecycle("Report: {}", reportFile);
        if (!passed) {
            throw new IllegalStateException("The remapped " + side + " jar of Minecraft " + version
                    + " failed its self-check; see "
                    + reportFile);
        }
    }

    /**
     * What the load check found.
     *
     * @param failures classes that failed to load or link, as {@code class<TAB>error}
     * @param otherOsLibraries classes that need a library this OS does not download ({@link
     *     RemappedJarCheck#isLibraryClass}); not a remap problem
     */
    private record LoadResult(int loaded, List<String> failures, List<String> otherOsLibraries) {}

    private LoadResult loadCheck(Path remapped, List<Path> libraries, Set<String> jarClasses, MojangMappings mappings) {
        List<String> command = new ArrayList<>();
        command.add(getJavaLauncher().get().getExecutablePath().getAsFile().getAbsolutePath());
        command.add("-cp");
        command.add(ownLocation());
        command.add(RemappedJarLoadCheck.class.getName());
        command.add(remapped.toString());
        command.add(libraries.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator)));
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            List<String> lines = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .lines()
                    .toList();
            int exit = process.waitFor();
            int loaded = -1;
            List<String> failures = new ArrayList<>();
            List<String> otherOs = new ArrayList<>();
            String missingClass = "\tjava.lang.NoClassDefFoundError: ";
            for (String line : lines) {
                if (line.startsWith("LOADED ")) {
                    loaded = Integer.parseInt(line.substring("LOADED ".length()).strip());
                } else if (line.startsWith("FAILED ")) {
                    String failure = line.substring("FAILED ".length());
                    int error = failure.indexOf(missingClass);
                    String missing = error < 0
                            ? null
                            : failure.substring(error + missingClass.length()).strip();
                    if (missing != null && RemappedJarCheck.isLibraryClass(missing, jarClasses, mappings)) {
                        otherOs.add(failure);
                    } else {
                        failures.add(failure);
                    }
                }
            }
            if (exit != 0 || loaded < 0) {
                throw new IllegalStateException("The load check exited with " + exit + ":\n" + String.join("\n", lines));
            }
            return new LoadResult(loaded, failures, otherOs);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to start the load check", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while running the load check", e);
        }
    }

    /** The jar or class folder this plugin was loaded from, for the load check's class path. */
    private static String ownLocation() {
        try {
            return Path.of(RemappedJarLoadCheck.class
                            .getProtectionDomain()
                            .getCodeSource()
                            .getLocation()
                            .toURI())
                    .toString();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Cannot locate the Poly Gradle plugin classes", e);
        }
    }

    private static IllegalStateException notObfuscated(String version, String download) {
        return new IllegalStateException("Minecraft " + version + " has no " + download + " in its version JSON: it is"
                + " not obfuscated (26.1 and later), or it has no official mappings (1.14.3 and earlier, out of scope"
                + " in ADR-0019). There is nothing to remap.");
    }

    private static String report(
            String version,
            String side,
            String jarSha1,
            String mappingsSha1,
            String remappedSha1,
            long remapMillis,
            String determinism,
            MojangJarRemapper.Result remap,
            RemappedJarCheck.Result check,
            LoadResult load) {
        StringBuilder out = new StringBuilder();
        out.append("# Remapped Minecraft ").append(version).append(' ').append(side).append("\n\n");
        out.append("The ")
                .append(side)
                .append(" jar of ")
                .append(version)
                .append(" remapped to Mojang names with Mojang's official mappings (ADR-0019). The remapped jar and"
                        + " the mappings stay in the local Gradle cache; they are never committed or published.\n\n");
        out.append("| | |\n|---|---|\n");
        out.append("| Original jar SHA-1 | `").append(jarSha1).append("` |\n");
        out.append("| Mappings SHA-1 | `").append(mappingsSha1).append("` |\n");
        out.append("| Remapper revision | ").append(MojangJarRemapper.REVISION).append(" |\n");
        out.append("| Remapped jar SHA-1 | `").append(remappedSha1).append("` |\n");
        out.append("| Determinism | ").append(determinism).append(" |\n");
        out.append("| Remap time | ")
                .append(remapMillis < 0 ? "not measured (cached jar reused)" : remapMillis + " ms")
                .append(" |\n\n");
        if (remap != null) {
            out.append("## Remap\n\n");
            out.append("- ")
                    .append(remap.classCount())
                    .append(" classes, ")
                    .append(remap.renamedClassCount())
                    .append(" of them listed in the mappings.\n");
            out.append("- Listed in the mappings but not in the jar: ")
                    .append(remap.absentListedClassCount())
                    .append(" (removed by the obfuscator, such as package-info).\n");
            out.append("- Signature files removed: ").append(list(remap.removedSignatureFiles())).append(".\n");
            out.append("- Stack map frames computed again; type merges with a class outside the jar, libraries and JDK: ")
                    .append(remap.unknownTypeMerges())
                    .append(".\n");
            out.append("- Other META-INF files copied as they are: ")
                    .append(list(remap.otherMetaInfFiles()))
                    .append(".\n");
            out.append("- Classes the mappings do not list (they keep their names): ")
                    .append(remap.unlistedClasses().size())
                    .append(remap.unlistedClasses().isEmpty()
                            ? ""
                            : ", such as " + list(remap.unlistedClasses().subList(0, Math.min(10, remap.unlistedClasses().size()))))
                    .append(".\n\n");
        }
        out.append("## Self-check\n\n");
        out.append("| Check | Result |\n|---|---|\n");
        out.append("| Default-package classes left (not mapped to themselves) | ")
                .append(check.defaultPackageClasses().size())
                .append(" |\n");
        out.append("| Members declared twice | ").append(check.duplicateMembers().size()).append(" |\n");
        out.append("| Field and method references to game classes | ")
                .append(check.referenceCount())
                .append(" |\n");
        out.append("| ... that no class in their hierarchy declares | ")
                .append(check.unresolvedReferenceCount())
                .append(" |\n");
        out.append("| ... whose hierarchy leaves for a class the libraries do not have | ")
                .append(check.outsideReferences())
                .append(" |\n");
        out.append("| Classes loaded and linked (not initialized) | ")
                .append(load.loaded())
                .append(" of ")
                .append(check.classCount())
                .append(" |\n");
        out.append("| ... not loaded because they need a library of another OS | ")
                .append(load.otherOsLibraries().size())
                .append(" |\n\n");
        examples(out, "Default-package classes left", List.copyOf(check.defaultPackageClasses()));
        examples(out, "Members declared twice", List.copyOf(check.duplicateMembers()));
        examples(out, "Unresolved references (first " + check.unresolvedReferences().size() + ")", check.unresolvedReferences());
        examples(out, "Classes that failed to load or link", load.failures());
        examples(out, "Classes that need a library of another OS (not a remap problem)", load.otherOsLibraries());
        return out.toString();
    }

    private static void examples(StringBuilder out, String title, List<String> lines) {
        if (lines.isEmpty()) {
            return;
        }
        out.append("### ").append(title).append("\n\n");
        lines.stream().limit(50).forEach(line -> out.append("- `").append(line).append("`\n"));
        if (lines.size() > 50) {
            out.append("- ... ").append(lines.size() - 50).append(" more\n");
        }
        out.append('\n');
    }

    private static String list(List<String> values) {
        return values.isEmpty()
                ? "none"
                : values.stream().map(value -> "`" + value + "`").collect(Collectors.joining(", "));
    }

    private static void write(Path file, String text) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, text);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write " + file, e);
        }
    }

    private static void delete(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete " + file, e);
        }
    }
}
