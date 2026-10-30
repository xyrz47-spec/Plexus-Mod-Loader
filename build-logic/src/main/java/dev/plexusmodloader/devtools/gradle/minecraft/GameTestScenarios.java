package dev.plexusmodloader.devtools.gradle.minecraft;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.TaskProvider;

/**
 * Build-script extension {@code polyScenarios}: registers game test server scenarios with their own
 * mod sets, e.g. {@code polyScenarios.gameTest("nativeOnly", files(...)) { expectedOutput.add(...) }}.
 */
public class GameTestScenarios {

    private final Project project;
    private final FileCollection polyRuntime;

    GameTestScenarios(Project project, FileCollection polyRuntime) {
        this.project = project;
        this.polyRuntime = polyRuntime;
    }

    /** Registers scenario task {@code name} that runs with exactly {@code mods}. */
    public TaskProvider<VerifiedGameRunTask> gameTest(String name, FileCollection mods, Action<VerifiedGameRunTask> configure) {
        TaskProvider<VerifiedGameRunTask> task =
                MinecraftRunTasks.registerGameTestScenario(project, name, polyRuntime, mods);
        task.configure(configure);
        return task;
    }
}
