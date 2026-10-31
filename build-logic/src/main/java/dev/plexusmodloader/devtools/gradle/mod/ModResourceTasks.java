package dev.plexusmodloader.devtools.gradle.mod;

import java.io.File;
import java.nio.file.Path;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.language.jvm.tasks.ProcessResources;

/**
 * Adds the resource tools to a mod project that applies the Java plugin: the {@code plexusResources}
 * extension, {@code generatePlexusResources} (its output is part of the main resources), and {@code
 * checkPlexusResources} (part of {@code check}).
 */
public final class ModResourceTasks {

    private static final String GROUP = "plexus mod";

    private ModResourceTasks() {}

    /** Registers the extension and both tasks on {@code project}. */
    public static void register(Project project) {
        PlexusResourcesExtension extension =
                project.getExtensions().create("plexusResources", PlexusResourcesExtension.class);
        // The 1.4.x name, so build scripts that still say polyResources { } keep working.
        project.getExtensions().add(PlexusResourcesExtension.class, "polyResources", extension);
        SourceSet main = project.getExtensions()
                .getByType(SourceSetContainer.class)
                .getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        Directory handWritten = project.getLayout().getProjectDirectory().dir("src/main/resources");
        File currentModJson = handWritten.file("plexus.mod.json").getAsFile();
        File plexusModJson = currentModJson.isFile() ? currentModJson : handWritten.file("poly.mod.json").getAsFile();
        Provider<Directory> generated = project.getLayout().getBuildDirectory().dir("generated/plexus-resources/main");

        TaskProvider<GeneratePlexusResourcesTask> generate = project.getTasks()
                .register("generatePlexusResources", GeneratePlexusResourcesTask.class, task -> {
                    task.setGroup(GROUP);
                    task.setDescription(
                            "Writes models, block states, item definitions, loot tables, names and tool tags"
                                    + " for the blocks and items listed in plexusResources.");
                    task.getModId().set(project.provider(() -> extension.getDefinitions().isEmpty()
                            ? "unused"
                            : GeneratePlexusResourcesTask.modId(plexusModJson)));
                    task.getDefinitions().set(project.provider(extension::getDefinitions));
                    task.getHandWrittenFiles().from(handWritten);
                    task.getHandWrittenDirectory().set(handWritten);
                    task.getOutputDirectory().set(generated);
                });
        main.getResources().srcDir(generate);

        // A merged file (lang/en_us.json, mineable tags) replaces the hand-written one of the same path.
        project.getTasks().named(main.getProcessResourcesTaskName(), ProcessResources.class, task -> {
            Path generatedRoot = generated.get().getAsFile().toPath();
            task.eachFile(file -> {
                if (!file.getFile().toPath().startsWith(generatedRoot)
                        && generatedRoot.resolve(file.getPath()).toFile().isFile()) {
                    file.exclude();
                }
            });
        });

        TaskProvider<ProcessResources> processResources =
                project.getTasks().named(main.getProcessResourcesTaskName(), ProcessResources.class);
        TaskProvider<CheckPlexusResourcesTask> check = project.getTasks()
                .register("checkPlexusResources", CheckPlexusResourcesTask.class, task -> {
                    task.setGroup("verification");
                    task.setDescription("Finds broken paths, JSON, model and texture references, and language files"
                            + " in the mod's resources without starting the game.");
                    task.getResourceFiles().from(processResources);
                    task.getResourceDirectory()
                            .set(project.getLayout().dir(processResources.map(ProcessResources::getDestinationDir)));
                    task.getReport()
                            .set(project.getLayout().getBuildDirectory().file("reports/plexus/resource-problems.txt"));
                });
        project.getTasks().named("check").configure(task -> task.dependsOn(check));
    }
}
