package dev.plexusmodloader.devtools.gradle.release;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

/**
 * {@code verifySdkRepository}, run by {@code publishSdk} after every publication: fails when the SDK repository
 * misses an artifact, carries sources or JavaDoc it must not, or holds a POM whose Plexus Mod Loader dependency is not
 * in it ({@link SdkRepositoryCheck}).
 */
@DisableCachingByDefault(because = "Reads a repository outside the build; always checked")
public abstract class VerifySdkRepositoryTask extends DefaultTask {

    @Internal
    public abstract DirectoryProperty getRepository();

    @Input
    public abstract Property<String> getVersion();

    /** Java modules of group {@value SdkRepositoryCheck#GROUP} published without JavaDoc. */
    @Input
    public abstract ListProperty<String> getModules();

    /** Java modules of group {@value SdkRepositoryCheck#GROUP} published with JavaDoc. */
    @Input
    public abstract ListProperty<String> getJavadocModules();

    /** Other artifacts as {@code group:name:packaging} (Gradle plugin, plugin marker, template zip). */
    @Input
    public abstract ListProperty<String> getOtherArtifacts();

    @TaskAction
    public void verify() {
        List<SdkRepositoryCheck.Artifact> expected = new ArrayList<>();
        for (String module : getModules().get()) {
            expected.add(new SdkRepositoryCheck.Artifact(SdkRepositoryCheck.GROUP, module, "jar", false));
        }
        for (String module : getJavadocModules().get()) {
            expected.add(new SdkRepositoryCheck.Artifact(SdkRepositoryCheck.GROUP, module, "jar", true));
        }
        for (String artifact : getOtherArtifacts().get()) {
            String[] parts = artifact.split(":");
            expected.add(new SdkRepositoryCheck.Artifact(parts[0], parts[1], parts[2], false));
        }
        Path repository = getRepository().get().getAsFile().toPath();
        List<String> problems = SdkRepositoryCheck.problems(repository, getVersion().get(), expected);
        if (!problems.isEmpty()) {
            throw new GradleException("The SDK repository " + repository + " is not ready to deploy:\n  - "
                    + String.join("\n  - ", problems));
        }
        getLogger()
                .lifecycle(
                        "SDK {} in {}: {} artifacts, no sources, every Plexus Mod Loader dependency present",
                        getVersion().get(),
                        repository,
                        expected.size());
    }
}
