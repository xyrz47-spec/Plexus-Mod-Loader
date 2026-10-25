package dev.polymodloader.devtools.gradle.mod;

import java.nio.file.Path;
import java.util.List;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

/**
 * {@code ./gradlew initPolyMod -PmodId=ruby_tools [-PmodName="Ruby Tools"] [-PmodPackage=io.github.you.rubytools]}
 * in an unzipped {@code poly-mod-template-<version>.zip}: renames the template's mod ID, name, Java package,
 * entry point class and project name in place with {@link ModProjectScaffolder#renameInPlace}, the same
 * renaming {@code newPolyMod} does in the Poly Mod Loader repository.
 */
@DisableCachingByDefault(because = "Rewrites the project's own sources")
public abstract class InitPolyModTask extends DefaultTask {

    @Input
    @Optional
    public abstract Property<String> getModId();

    /** Display name; defaults to the mod ID in title case. */
    @Input
    @Optional
    public abstract Property<String> getModName();

    /** Java package; defaults to {@code com.example.<mod ID without _>}. */
    @Input
    @Optional
    public abstract Property<String> getModPackage();

    @Internal
    public abstract DirectoryProperty getProjectDirectory();

    @TaskAction
    public void rename() {
        if (!getModId().isPresent()) {
            throw new GradleException("Usage: ./gradlew initPolyMod -PmodId=<mod_id> [-PmodName=\"<Name>\"]"
                    + " [-PmodPackage=<java.package>]");
        }
        Path project = getProjectDirectory().get().getAsFile().toPath();
        ModProjectScaffolder.ModNames names;
        List<Path> changed;
        try {
            names = ModProjectScaffolder.ModNames.withDefaults(
                    getModId().get(), getModName().getOrNull(), getModPackage().getOrNull());
            changed = ModProjectScaffolder.renameInPlace(project, names);
        } catch (IllegalArgumentException e) {
            throw new GradleException(e.getMessage(), e);
        }
        getLogger()
                .lifecycle(
                        """
                        Renamed the template to the mod {} ("{}", {} files changed) in {}
                          entry point: {}.{}
                          Gradle project: {} (you may rename the folder to match)
                        Next:
                          ./gradlew build        compiles, checks poly.mod.json and the resources
                          ./gradlew runClient    starts the game with the mod""",
                        names.modId(),
                        names.modName(),
                        changed.size(),
                        project,
                        names.javaPackage(),
                        names.className(),
                        names.projectName());
    }
}
