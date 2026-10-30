package dev.plexusmodloader.devtools.gradle.minecraft;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;

/**
 * {@code minecraftVersionDiff -Pfrom=<supported version> -Pto=<version>}: downloads the two client jars and
 * reports what the supported version's modules use that the other version changed ({@link
 * MinecraftVersionDiff}). The other version can be any id of Mojang's version manifest, snapshots included.
 */
@UntrackedTask(because = "Reads Mojang's version manifest; the report is cheap to make again.")
public abstract class MinecraftVersionDiffTask extends DefaultTask {

    /** The supported version whose modules are checked. */
    @Input
    public abstract Property<String> getFrom();

    /** The version to compare with. */
    @Input
    public abstract Property<String> getTo();

    /** The compiled classes of {@code plexus-version-adapter-v<id>} and {@code plexus-game-v<id>}. */
    @Internal
    public abstract ConfigurableFileCollection getModuleClasses();

    /** Shared download cache, the same one the compile and run tasks use. */
    @Internal
    public abstract DirectoryProperty getCacheDirectory();

    /** Optional {@code MinecraftFiles/} folder with hand-copied game files. */
    @Internal
    public abstract DirectoryProperty getLocalFilesDirectory();

    /** The Markdown report. */
    @OutputFile
    public abstract RegularFileProperty getReportFile();

    @TaskAction
    public void compare() {
        String from = getFrom().get();
        String to = getTo().getOrNull();
        if (to == null) {
            throw new IllegalArgumentException(
                    "Name the version to compare with: minecraftVersionDiff -Pfrom=" + from + " -Pto=<version>.");
        }
        if (from.equals(to)) {
            throw new IllegalArgumentException("-Pfrom and -Pto are both " + from + "; name two different versions.");
        }
        Path localFiles = getLocalFilesDirectory().isPresent()
                ? getLocalFilesDirectory().get().getAsFile().toPath()
                : null;
        MinecraftDistributionInstaller installer = new MinecraftDistributionInstaller(
                getCacheDirectory().get().getAsFile().toPath(),
                LocalMinecraftFiles.at(localFiles, getLogger()),
                getLogger());
        MinecraftDistributionInstaller.ClientFiles fromFiles = installer.clientFiles(from);
        MinecraftDistributionInstaller.ClientFiles toFiles = installer.clientFiles(to);
        List<Path> modules =
                getModuleClasses().getFiles().stream().map(File::toPath).toList();
        if (fromFiles.clientMappings().isPresent()) {
            throw new IllegalStateException("Minecraft " + from + " is obfuscated; -Pfrom must be a supported version"
                    + " whose jar uses Mojang names, as its modules are compiled against those names.");
        }
        MojangMappings toMappings =
                toFiles.clientMappings().map(MojangMappings::read).orElse(null);
        if (toMappings != null) {
            getLogger()
                    .lifecycle(
                            "Minecraft {} is obfuscated; reading it through Mojang's mappings ({} classes,"
                                    + " ADR-0019). The mappings stay in the local cache.",
                            to,
                            toMappings.classCount());
        }
        MinecraftVersionDiff.Report report = MinecraftVersionDiff.compare(
                from,
                fromFiles.versionJson(),
                fromFiles.clientJar(),
                to,
                toFiles.versionJson(),
                toFiles.clientJar(),
                toMappings,
                modules);

        Path reportFile = getReportFile().get().getAsFile().toPath();
        try {
            Files.createDirectories(reportFile.getParent());
            Files.writeString(reportFile, MinecraftVersionDiff.markdown(report));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write " + reportFile, e);
        }
        getLogger()
                .lifecycle(
                        "Minecraft {} -> {}: the {} modules use {} game classes and {} members; {} classes missing,"
                                + " {} members no longer found, changed classes: {}; Java {} -> {}; {} library"
                                + " changes. {}",
                        from,
                        to,
                        from,
                        report.usedClasses().size(),
                        report.usedMemberCount(),
                        report.missingClasses().size(),
                        report.memberChanges().size(),
                        report.toObfuscated()
                                ? "not compared (obfuscated)"
                                : String.valueOf(report.changedClasses().size()),
                        report.fromJava(),
                        report.toJava(),
                        report.libraryChanges().size(),
                        report.hotfixCandidate()
                                ? "Hotfix candidate."
                                : report.toObfuscated()
                                        ? "Needs a remapped jar and its own modules (ADR-0019)."
                                        : "Needs its own modules.");
        getLogger().lifecycle("Report: {}", reportFile);
    }
}
