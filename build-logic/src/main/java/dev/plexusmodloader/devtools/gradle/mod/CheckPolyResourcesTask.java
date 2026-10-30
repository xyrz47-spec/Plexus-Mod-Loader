package dev.plexusmodloader.devtools.gradle.mod;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.IgnoreEmptyDirectories;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/**
 * {@code checkPolyResources}: runs {@link ModResourceChecker} on the resources that go into the mod jar
 * (hand-written and generated) and fails with every problem it finds. Part of {@code check}.
 */
@CacheableTask
public abstract class CheckPolyResourcesTask extends DefaultTask {

    /** The processed resources, so the task reruns when any of them changes. */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    @IgnoreEmptyDirectories
    public abstract ConfigurableFileCollection getResourceFiles();

    /** Root of the processed resources, laid out as in the jar. */
    @Internal
    public abstract DirectoryProperty getResourceDirectory();

    /** The problems found, one per line; empty when there are none. */
    @OutputFile
    public abstract RegularFileProperty getReport();

    @TaskAction
    public void check() throws IOException {
        List<ModResourceProblem> problems =
                ModResourceChecker.check(getResourceDirectory().get().getAsFile().toPath());
        Path report = getReport().get().getAsFile().toPath();
        Files.createDirectories(report.getParent());
        String lines = problems.stream().map(ModResourceProblem::toString).collect(Collectors.joining("\n"));
        Files.writeString(report, lines.isEmpty() ? "" : lines + "\n", StandardCharsets.UTF_8);
        if (!problems.isEmpty()) {
            throw new GradleException(problems.size() + (problems.size() == 1 ? " problem" : " problems")
                    + " in the mod's resources (src/main/resources and polyResources):\n  "
                    + lines.replace("\n", "\n  ") + "\nError codes: PLEXUS-RESOURCE in the Poly Mod Loader docs. Report: "
                    + report);
        }
    }
}
