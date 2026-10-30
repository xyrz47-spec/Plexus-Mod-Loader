package dev.plexusmodloader.devtools.gradle.mod;

import dev.plexusmodloader.devtools.gradle.minecraft.PolyModPlugin;
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
 * {@code ./gradlew newPolyMod -PmodId=ruby_tools [-PmodName="Ruby Tools"] [-PmodPackage=io.github.you.rubytools]
 * [-PmodDir=../ruby-tools]}: creates a ready-to-build mod project with {@link ModProjectScaffolder}.
 */
@DisableCachingByDefault(because = "Creates a project outside the build")
public abstract class NewPolyModTask extends DefaultTask {

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

    /** Where to create the project; defaults to a folder next to the Poly Mod Loader repository. */
    @Input
    @Optional
    public abstract Property<String> getModDirectory();

    @Internal
    public abstract DirectoryProperty getTemplateDirectory();

    @Internal
    public abstract DirectoryProperty getRepositoryRoot();

    @TaskAction
    public void create() {
        if (!getModId().isPresent()) {
            throw new GradleException("Usage: ./gradlew newPolyMod -PmodId=<mod_id> [-PmodName=\"<Name>\"]"
                    + " [-PmodPackage=<java.package>] [-PmodDir=<folder>]");
        }
        ModProjectScaffolder.ModNames names;
        try {
            names = ModProjectScaffolder.ModNames.withDefaults(
                    getModId().get(), getModName().getOrNull(), getModPackage().getOrNull());
        } catch (IllegalArgumentException e) {
            throw new GradleException(e.getMessage(), e);
        }
        Path root = getRepositoryRoot().get().getAsFile().toPath();
        Path target = getModDirectory().isPresent()
                ? root.resolve(getModDirectory().get()).normalize()
                : root.resolveSibling(names.projectName()).normalize();
        List<Path> created;
        try {
            created = ModProjectScaffolder.scaffold(getTemplateDirectory().get().getAsFile().toPath(), target, names);
        } catch (IllegalArgumentException e) {
            throw new GradleException(e.getMessage(), e);
        }
        getLogger()
                .lifecycle(
                        """
                        Created the mod {} ("{}", {} files) in {}
                          entry point: {}.{}
                        Poly Mod Loader comes from {}. To try a local Poly Mod Loader build
                        instead, run ./gradlew publishToMavenLocal and ./gradlew -p build-logic publishToMavenLocal
                        in this repository with a version that repository does not have.
                        Next:
                          cd {}
                          ./gradlew build        compiles, checks poly.mod.json and the resources
                          ./gradlew runClient    starts the game with the mod""",
                        names.modId(),
                        names.modName(),
                        created.size(),
                        target,
                        names.javaPackage(),
                        names.className(),
                        PolyModPlugin.SDK_REPOSITORY_URL,
                        target);
    }
}
