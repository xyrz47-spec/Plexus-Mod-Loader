package dev.plexusmodloader.devtools.gradle.mod;

import dev.plexusmodloader.devtools.gradle.release.SdkRepositoryPlugin;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Zip;

/**
 * Root-project plugin of the Plexus Mod Loader repository ({@code plexus.mod-template}): registers {@code newPlexusMod},
 * which starts a mod project from {@code templates/plexus-mod-template}, and {@code packageModTemplate}, which
 * writes the template as {@code plexus-mod-template-<version>.zip} for people without the repository
 * ({@code initPlexusMod} renames an unzipped copy).
 */
public final class ModTemplatePlugin implements Plugin<Project> {

    /** The template's folder in the repository. */
    public static final String TEMPLATE_DIRECTORY = "templates/plexus-mod-template";

    /** The template zip's base name and Maven artifact ID. */
    public static final String TEMPLATE_ARTIFACT = "plexus-mod-template";

    private static final Pattern PLUGIN_VERSION = Pattern.compile("id\\(\"dev\\.(?:plexus|poly)modloader\\.mod\"\\) version \"[^\"]*\"");

    @Override
    public void apply(Project project) {
        project.getTasks().register("newPlexusMod", NewPlexusModTask.class, task -> {
            task.setGroup("plexus mod");
            task.setDescription("Creates a new Plexus Mod Loader mod project from the template"
                    + " (-PmodId=<mod_id> [-PmodName=<name>] [-PmodPackage=<package>] [-PmodDir=<folder>]).");
            task.getModId().set(project.getProviders().gradleProperty("modId"));
            task.getModName().set(project.getProviders().gradleProperty("modName"));
            task.getModPackage().set(project.getProviders().gradleProperty("modPackage"));
            task.getModDirectory().set(project.getProviders().gradleProperty("modDir"));
            task.getTemplateDirectory().set(project.getLayout().getProjectDirectory().dir(TEMPLATE_DIRECTORY));
            task.getRepositoryRoot().set(project.getLayout().getProjectDirectory());
        });
        String version = String.valueOf(project.property("plexus.version"));
        File templateGitignore = project.file(TEMPLATE_DIRECTORY + "/.gitignore");
        Provider<RegularFile> gitignoreCopy =
                project.getLayout().getBuildDirectory().file("mod-template/gitignore/gitignore");
        TaskProvider<Task> gitignore = project.getTasks().register("modTemplateGitignore", task -> {
            task.setDescription("Copies the template's .gitignore under a name the zip does not leave out.");
            task.getInputs().file(templateGitignore);
            task.getOutputs().file(gitignoreCopy);
            task.doLast(ignored -> {
                try {
                    Path target = gitignoreCopy.get().getAsFile().toPath();
                    Files.createDirectories(target.getParent());
                    Files.copy(templateGitignore.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        });
        project.getTasks().register("packageModTemplate", Zip.class, zip -> {
            zip.setGroup("plexus mod");
            zip.setDescription("Writes the mod template, set to this Plexus Mod Loader version, as a zip for mod developers"
                    + " without this repository (published by publishSdk).");
            zip.getArchiveBaseName().set(TEMPLATE_ARTIFACT);
            zip.getArchiveVersion().set(version);
            zip.getDestinationDirectory().set(project.getLayout().getBuildDirectory().dir("mod-template"));
            zip.setPreserveFileTimestamps(false);
            zip.setReproducibleFileOrder(true);
            zip.from(project.getLayout().getProjectDirectory().dir(TEMPLATE_DIRECTORY), spec -> {
                spec.exclude("build/**", ".gradle/**", "run/**", "out/**", ".idea/**", "bin/**", "*.iml");
                spec.into(TEMPLATE_ARTIFACT);
                spec.filesMatching("build.gradle.kts", file -> file.filter(line -> withPluginVersion(line, version)));
                spec.filesMatching("gradlew", file -> file.permissions(permissions -> permissions.unix("rwxr-xr-x")));
            });
            // Gradle's default excludes drop every .gitignore, so it goes in under another name and is renamed.
            zip.from(gitignore, spec -> {
                spec.into(TEMPLATE_ARTIFACT);
                spec.rename(name -> ".gitignore");
            });
            zip.getInputs().property("plexusVersion", version);
        });
    }

    /**
     * {@code id("dev.plexusmodloader.mod") version "<any>"}, or the same with the former ID {@code dev.polymodloader.mod},
     * as {@code id("dev.plexusmodloader.mod") version "<version>"}.
     */
    static String withPluginVersion(String line, String version) {
        Matcher matcher = PLUGIN_VERSION.matcher(line);
        return matcher.find()
                ? matcher.replaceFirst(Matcher.quoteReplacement("id(\"" + SdkRepositoryPlugin.MOD_PLUGIN_ID + "\") version \"" + version + "\""))
                : line;
    }
}
