package dev.plexusmodloader.devtools.gradle.minecraft;

import java.util.List;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.provider.Provider;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainService;

/**
 * Root-project plugin for the supported Minecraft versions (ADR-0011): checks {@value
 * MinecraftVersions#FILE} and registers {@code newMinecraftVersion}, {@code minecraftVersionDiff}, {@code
 * remapMinecraft} (ADR-0019) and {@code minecraftVersions}.
 */
public final class MinecraftVersionsPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        MinecraftVersions versions = MinecraftVersions.of(project);
        project.getTasks().register("newMinecraftVersion", NewMinecraftVersionTask.class, task -> {
            task.setGroup("plexus minecraft");
            task.setDescription("Copies a supported Minecraft version's modules to start supporting a new version"
                    + " (-Pfrom=<version> -Pto=<version> [-Pjava=<version>]).");
            task.getRepositoryRoot().set(project.getLayout().getProjectDirectory());
            task.getLocalMinecraftFiles().set(LocalMinecraftFiles.directory(project));
            task.getFrom().set(project.getProviders().gradleProperty("from"));
            task.getTo().set(project.getProviders().gradleProperty("to"));
            task.getJavaVersion()
                    .set(project.getProviders().gradleProperty("java").map(Integer::parseInt));
            task.getExperimental()
                    .set(project.getProviders()
                            .gradleProperty("experimental")
                            .map(Boolean::parseBoolean)
                            .orElse(false));
        });
        project.getTasks().register("minecraftVersionDiff", MinecraftVersionDiffTask.class, task -> {
            task.setGroup("plexus minecraft");
            task.setDescription("Reports what a supported version's modules use that another Minecraft version changed"
                    + " (-Pfrom=<supported version> -Pto=<version>), before newMinecraftVersion or a hotfix entry.");
            String from = project.getProviders()
                    .gradleProperty("from")
                    .getOrElse(versions.defaultVersion());
            if (!versions.supported().contains(from)) {
                throw new IllegalArgumentException("-Pfrom=" + from + " is not a supported Minecraft version "
                        + versions.supported() + "; its modules are what minecraftVersionDiff checks.");
            }
            task.getFrom().set(from);
            task.getTo().set(project.getProviders().gradleProperty("to"));
            for (String module : List.of(MinecraftVersions.adapterModule(from), MinecraftVersions.gameLayerModule(from))) {
                task.dependsOn(":" + module + ":classes");
                task.getModuleClasses().from(project.file(module + "/build/classes/java/main"));
            }
            task.getCacheDirectory().set(cacheDirectory(project));
            task.getLocalFilesDirectory().set(LocalMinecraftFiles.directory(project));
            task.getReportFile()
                    .set(project.getLayout()
                            .getBuildDirectory()
                            .file(task.getTo()
                                    .map(to -> "reports/minecraft-version-diff/" + from + "-to-" + to + ".md")));
        });
        project.getPluginManager().apply("jvm-toolchains");
        JavaToolchainService toolchains = project.getExtensions().getByType(JavaToolchainService.class);
        project.getTasks().register("remapMinecraft", RemapMinecraftTask.class, task -> {
            task.setGroup("plexus minecraft");
            task.setDescription("Remaps the client jar of a version before 26.1 to Mojang names and self-checks it"
                    + " (-Pminecraft=<version>, ADR-0019); the jar stays in the local cache.");
            task.getMinecraftVersion().set(project.getProviders().gradleProperty("minecraft"));
            task.getSide().set(project.getProviders().gradleProperty("side").orElse("client"));
            task.getCacheDirectory().set(cacheDirectory(project));
            task.getLocalFilesDirectory().set(LocalMinecraftFiles.directory(project));
            // Every version runs on the Java of the default version (ADR-0019 point 5).
            task.getJavaLauncher()
                    .set(toolchains.launcherFor(spec -> spec.getLanguageVersion()
                            .set(JavaLanguageVersion.of(versions.javaVersion(versions.defaultVersion())))));
            task.getReportFile()
                    .set(project.getLayout()
                            .getBuildDirectory()
                            .file(task.getMinecraftVersion()
                                    .zip(task.getSide(), (version, side) ->
                                            "reports/minecraft-remap/" + version + "-" + side + ".md")));
        });
        project.getTasks().register("minecraftVersions", task -> {
            task.setGroup("plexus minecraft");
            task.setDescription("Lists the supported Minecraft versions and their modules.");
            task.doLast(ignored -> {
                for (String version : versions.supported()) {
                    task.getLogger()
                            .lifecycle(
                                    "Minecraft {} (Java {}){}: {}, {}",
                                    version,
                                    versions.javaVersion(version),
                                    version.equals(versions.defaultVersion()) ? " [default]" : "",
                                    MinecraftVersions.adapterModule(version),
                                    MinecraftVersions.gameLayerModule(version));
                    for (String hotfix : versions.hotfixes().getOrDefault(version, List.of())) {
                        task.getLogger().lifecycle("Minecraft {}: hotfix, runs on the modules of {}", hotfix, version);
                    }
                }
                for (String version : versions.experimental()) {
                    task.getLogger()
                            .lifecycle(
                                    "Minecraft {} (Java {}) [experimental, not supported, ADR-0019]: {}, {}",
                                    version,
                                    versions.javaVersion(version),
                                    MinecraftVersions.adapterModule(version),
                                    MinecraftVersions.gameLayerModule(version));
                }
            });
        });
    }

    private static Provider<Directory> cacheDirectory(Project project) {
        return project.getLayout()
                .dir(project.provider(() -> project.getGradle()
                        .getGradleUserHomeDir()
                        .toPath()
                        .resolve("caches")
                        .resolve("plexus-minecraft")
                        .toFile()));
    }
}
