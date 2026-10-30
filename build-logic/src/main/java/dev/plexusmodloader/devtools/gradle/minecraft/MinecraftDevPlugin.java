package dev.plexusmodloader.devtools.gradle.minecraft;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;

/**
 * In-repository plugin for the project that owns the Poly Mod Loader bootstrap. The Poly Mod Loader class path is the
 * project's own runtime class path; mods built in this repository are added with the
 * {@code polyDevMod} configuration. Tasks are described in {@link MinecraftRunTasks}.
 */
public final class MinecraftDevPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        ObjectFactory objects = project.getObjects();
        Configuration devMods = project.getConfigurations().create("polyDevMod", configuration -> {
            configuration.setTransitive(false);
            configuration.setCanBeConsumed(false);
            configuration.setCanBeResolved(true);
            configuration.attributes(attributes -> {
                attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.class, Usage.JAVA_RUNTIME));
                attributes.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.class, Category.LIBRARY));
                attributes.attribute(
                        LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                        objects.named(LibraryElements.class, LibraryElements.JAR));
            });
        });
        SourceSet main = project.getExtensions()
                .getByType(JavaPluginExtension.class)
                .getSourceSets()
                .getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        project.getExtensions().add("polyScenarios", new GameTestScenarios(project, main.getRuntimeClasspath()));
        // Runs and verification use one version: the default, or the one picked with -PpolyMinecraft.
        String gameVersion = MinecraftVersions.of(project).selected(project);
        project.getExtensions().getExtraProperties().set("polyMinecraftVersion", gameVersion);
        MinecraftRunTasks.register(
                project,
                main.getRuntimeClasspath(),
                devMods,
                gameVersion,
                project.getRootProject().getLayout().getProjectDirectory().dir("run"));
    }
}
