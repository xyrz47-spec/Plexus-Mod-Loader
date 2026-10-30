package dev.plexusmodloader.devtools.gradle.minecraft;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.toolchain.JavaLanguageVersion;

/**
 * Compiles a version module ({@code plexus-game-v<id>}, ADR-0011) against the Minecraft client jar of
 * the version in its name and that jar's libraries (ADR-0010). Minecraft 26.x
 * ships unobfuscated, so the downloaded jar is used as is; it is added as {@code compileOnly} and is
 * therefore never packaged or published. The client jar is used because it contains the server
 * classes too.
 *
 * <p>A module not named after a version (a test mod that compiles against game classes) names its version with the
 * Gradle property {@value #VERSION_PROPERTY}, usually in its own {@code gradle.properties}.
 */
public final class MinecraftCompilePlugin implements Plugin<Project> {

    static final String VERSION_PROPERTY = "polyCompileMinecraft";

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply("java-library");
        Provider<Directory> cache = project.getLayout()
                .dir(project.provider(() -> project.getGradle()
                        .getGradleUserHomeDir()
                        .toPath()
                        .resolve("caches")
                        .resolve("poly-minecraft")
                        .toFile()));
        // A version module compiles against, and for the Java of, the version in its name.
        MinecraftVersions versions = MinecraftVersions.of(project);
        String gameVersion = versions.versionOfModule(project.getName())
                .orElseGet(() -> project.hasProperty(VERSION_PROPERTY)
                        ? versions.modulesVersion(
                                project.property(VERSION_PROPERTY).toString())
                        : versions.ofModule(project));
        project.getExtensions()
                .getByType(JavaPluginExtension.class)
                .getToolchain()
                .getLanguageVersion()
                .set(JavaLanguageVersion.of(versions.javaVersion(gameVersion)));
        TaskProvider<PrepareMinecraftTask> prepare = project.getTasks()
                .register("prepareMinecraftCompile", PrepareMinecraftTask.class, task -> {
                    task.setGroup("poly minecraft");
                    task.setDescription("Downloads and verifies the Minecraft client jar and libraries to compile against.");
                    task.getGameVersion().set(gameVersion);
                    task.getSide().set("client");
                    task.getCacheDirectory().set(cache);
                    task.getLocalFilesDirectory().set(LocalMinecraftFiles.directory(project));
                    task.getLaunchSpecFile()
                            .set(project.getLayout()
                                    .getBuildDirectory()
                                    .file("poly/minecraft-compile/" + gameVersion + "-client.json"));
                });
        // Derived from the task's output so Gradle knows the producer without reading the spec, which
        // does not exist until prepareMinecraftCompile runs (a clean checkout has no build directory).
        ConfigurableFileCollection minecraft =
                project.files(prepare.flatMap(PrepareMinecraftTask::getLaunchSpecFile).map(specFile -> {
                    MinecraftLaunchSpec spec =
                            MinecraftLaunchSpec.read(specFile.getAsFile().toPath());
                    List<File> files = new ArrayList<>();
                    files.add(new File(spec.gameJar()));
                    spec.libraries().forEach(library -> files.add(new File(library)));
                    return files;
                }));
        project.getDependencies().add("compileOnly", minecraft);
        // Unit tests of game-layer code that needs no running game (NBT conversion, for example).
        project.getDependencies().add("testImplementation", minecraft);
    }
}
