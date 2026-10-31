package dev.plexusmodloader.devtools.gradle.minecraft;

import java.util.ArrayList;
import java.util.List;
import org.gradle.api.Project;
import org.gradle.api.artifacts.VersionCatalogsExtension;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.toolchain.JavaToolchainService;

/**
 * Registers the Minecraft download, run, and verification tasks. Shared by the in-repository
 * {@code plexus.minecraft-dev} plugin and the published {@code dev.plexusmodloader.mod} plugin; they
 * differ only in where the Plexus Mod Loader runtime class path and the mods come from.
 */
final class MinecraftRunTasks {

    private static final String GROUP = "plexus minecraft";

    /** Level name Mojang's game test server uses inside its universe directory. */
    static final String GAME_TEST_WORLD = "gametestworld";

    private MinecraftRunTasks() {}

    /**
     * @param plexusRuntime Plexus Mod Loader bootstrap, runtime, and version adapter jars (the JVM class path)
     * @param devMods mod jars to load in development runs
     * @param gameVersion Minecraft version to download and run
     * @param runRoot directory holding {@code client/} and {@code server/} game directories
     */
    static void register(
            Project project, FileCollection plexusRuntime, FileCollection devMods, String gameVersion, Directory runRoot) {
        Provider<Directory> cache = project.getLayout()
                .dir(project.provider(() -> project.getGradle()
                        .getGradleUserHomeDir()
                        .toPath()
                        .resolve("caches")
                        .resolve("plexus-minecraft")
                        .toFile()));
        Provider<Directory> launchSpecs = project.getLayout().getBuildDirectory().dir("plexus/minecraft");

        TaskProvider<PrepareMinecraftTask> prepareClient = project.getTasks()
                .register("prepareMinecraftClient", PrepareMinecraftTask.class, task -> {
                    task.setGroup(GROUP);
                    task.setDescription("Downloads and verifies the Minecraft client, libraries, and assets.");
                    task.getGameVersion().convention(gameVersion);
                    task.getSide().set("client");
                    task.getCacheDirectory().set(cache);
                    task.getLocalFilesDirectory().set(LocalMinecraftFiles.directory(project));
                    task.getLaunchSpecFile()
                            .set(launchSpecs.flatMap(dir ->
                                    task.getGameVersion().map(version -> dir.file(version + "-client.json"))));
                });
        TaskProvider<PrepareMinecraftTask> prepareServer = project.getTasks()
                .register("prepareMinecraftServer", PrepareMinecraftTask.class, task -> {
                    task.setGroup(GROUP);
                    task.setDescription("Downloads, verifies, and unbundles the Minecraft dedicated server.");
                    task.getGameVersion().convention(gameVersion);
                    task.getSide().set("server");
                    task.getCacheDirectory().set(cache);
                    task.getLocalFilesDirectory().set(LocalMinecraftFiles.directory(project));
                    task.getLaunchSpecFile()
                            .set(launchSpecs.flatMap(dir ->
                                    task.getGameVersion().map(version -> dir.file(version + "-server.json"))));
                });

        JavaToolchainService toolchains = project.getExtensions().getByType(JavaToolchainService.class);
        JavaPluginExtension java = project.getExtensions().getByType(JavaPluginExtension.class);
        Provider<Directory> transformCache = project.getLayout().getBuildDirectory().dir("plexus/transform-cache");

        project.getTasks().register("runClient", RunMinecraftTask.class, task -> {
            task.setGroup(GROUP);
            task.setDescription("Starts the Minecraft client through Plexus Mod Loader.");
            configure(task, prepareClient, plexusRuntime, devMods, toolchains, java, transformCache);
            task.getLaunchTarget().set("client");
            task.getRunDirectory().set(runRoot.dir("client"));
        });
        project.getTasks().register("runServer", RunMinecraftTask.class, task -> {
            task.setGroup(GROUP);
            task.setDescription("Starts the dedicated server through Plexus Mod Loader (requires an accepted EULA).");
            configure(task, prepareServer, plexusRuntime, devMods, toolchains, java, transformCache);
            task.getLaunchTarget().set("dedicated_server");
            task.getRunDirectory().set(runRoot.dir("server"));
            task.getRequireAcceptedEula().set(true);
        });
        project.getTasks().register("serverBootSmokeTest", VerifiedGameRunTask.class, task -> {
            task.setGroup("verification");
            task.setDescription("Boots the dedicated server through Plexus Mod Loader up to the EULA check and verifies the log.");
            configure(task, prepareServer, plexusRuntime, devMods, toolchains, java, transformCache);
            task.getLaunchTarget().set("dedicated_server");
            task.getRunDirectory().set(project.getLayout().getBuildDirectory().dir("plexus/server-boot-smoke"));
            task.getRequireAcceptedEula().set(false);
        });
        TaskProvider<VerifiedGameRunTask> worldCreateSave = project.getTasks()
                .register("worldCreateSaveTest", VerifiedGameRunTask.class, task -> {
                    task.setGroup("verification");
                    task.setDescription("Creates a world on Mojang's game test server through Plexus Mod Loader, runs the built-in "
                            + "always_pass GameTest, and verifies the world was saved.");
                    configure(task, prepareServer, plexusRuntime, devMods, toolchains, java, transformCache);
                    task.getLaunchTarget().set("game_test_server");
                    task.getGameArgumentOverrides()
                            .set(List.of("--universe", "universe", "--tests", "minecraft:always_pass"));
                    task.getRunDirectory().set(project.getLayout().getBuildDirectory().dir("plexus/world-create-save"));
                    task.getExpectedOutput().add("All 1 required tests passed");
                    task.getExpectedFiles().add("universe/" + GAME_TEST_WORLD + "/level.dat");
                });
        project.getTasks().register("gameTests", VerifiedGameRunTask.class, task -> {
            task.setGroup("verification");
            task.setDescription("Runs the mods' in-game tests (GameTestRegistrar) on Mojang's game test server through "
                    + "Plexus Mod Loader; -PplexusTests=<selector> picks tests, for example my_mod:*.");
            configure(task, prepareServer, plexusRuntime, devMods, toolchains, java, transformCache);
            task.getLaunchTarget().set("game_test_server");
            List<String> arguments = new ArrayList<>(List.of("--universe", "universe"));
            Object selector = project.findProperty("plexusTests");
            if (selector != null) {
                arguments.addAll(List.of("--tests", selector.toString()));
            }
            task.getGameArgumentOverrides().set(arguments);
            task.getRunDirectory().set(project.getLayout().getBuildDirectory().dir("plexus/game-tests"));
            task.getExpectedOutput().add("required tests passed");
        });
        project.getTasks().register("startupBenchmark", StartupBenchmarkTask.class, task -> {
            task.setGroup("verification");
            task.setDescription("Measures game test server startup-to-exit time, vanilla versus Plexus Mod Loader (design doc §14.4).");
            task.getLaunchSpecFile().set(prepareServer.flatMap(PrepareMinecraftTask::getLaunchSpecFile));
            task.getPlexusRuntime().from(plexusRuntime);
            task.getDevModJars().from(devMods);
            task.getJavaLauncher().set(toolchains.launcherFor(java.getToolchain()));
            task.getRuns().convention(5);
            task.getWorkDirectory().set(project.getLayout().getBuildDirectory().dir("plexus/startup-benchmark"));
            task.getTransformCacheDirectory().set(transformCache);
        });
        // The build script supplies the tick probe agent jar (getProbeAgent).
        project.getTasks().register("tickBenchmark", TickBenchmarkTask.class, task -> {
            task.setGroup("verification");
            task.setDescription(
                    "Compares server tick times, heap and GC of vanilla and Plexus Mod Loader under the same load (design doc §14.4).");
            task.getLaunchSpecFile().set(prepareServer.flatMap(PrepareMinecraftTask::getLaunchSpecFile));
            task.getPlexusRuntime().from(plexusRuntime);
            task.getDevModJars().from(devMods);
            task.getJavaLauncher().set(toolchains.launcherFor(java.getToolchain()));
            task.getRuns().convention(5);
            task.getHoldTicks().convention(1200);
            task.getWarmupTicks().convention(200);
            task.getWorkDirectory().set(project.getLayout().getBuildDirectory().dir("plexus/tick-benchmark"));
            task.getTransformCacheDirectory().set(transformCache);
        });
        project.getTasks().register("clientWorldReloadTest", ClientWorldReloadTask.class, task -> {
            task.setGroup("verification");
            task.setDescription("Opens the world saved by worldCreateSaveTest in the client through Plexus Mod Loader (opens a window).");
            task.dependsOn(worldCreateSave);
            task.getLaunchSpecFile().set(prepareClient.flatMap(PrepareMinecraftTask::getLaunchSpecFile));
            task.getClasspath().from(plexusRuntime);
            task.getDevModJars().from(devMods);
            task.getJavaLauncher().set(toolchains.launcherFor(java.getToolchain()));
            task.getSourceWorld()
                    .set(worldCreateSave.flatMap(run -> run.getRunDirectory().dir("universe/" + GAME_TEST_WORLD)));
            task.getWorldName().set("plexus_roundtrip");
            task.getRunDirectory().set(project.getLayout().getBuildDirectory().dir("plexus/client-world-reload"));
            task.getTimeoutSeconds().convention(180);
            task.getTransformCacheDirectory().set(transformCache);
            task.getExpectedOutput().add("joined the game");
        });
        // The build script supplies the tick probe agent jar (getProbeAgent).
        project.getTasks().register("frameBenchmark", FrameBenchmarkTask.class, task -> {
            task.setGroup("verification");
            task.setDescription(
                    "Compares client frame times of vanilla and Plexus Mod Loader in the same world (design doc §14.4; opens windows).");
            task.dependsOn(worldCreateSave);
            task.getLaunchSpecFile().set(prepareClient.flatMap(PrepareMinecraftTask::getLaunchSpecFile));
            task.getPlexusRuntime().from(plexusRuntime);
            task.getDevModJars().from(devMods);
            task.getJavaLauncher().set(toolchains.launcherFor(java.getToolchain()));
            task.getSourceWorld()
                    .set(worldCreateSave.flatMap(run -> run.getRunDirectory().dir("universe/" + GAME_TEST_WORLD)));
            task.getRuns().convention(5);
            task.getSettleSeconds().convention(10);
            task.getMeasureSeconds().convention(30);
            task.getCooldownSeconds().convention(20);
            task.getWorkDirectory().set(project.getLayout().getBuildDirectory().dir("plexus/frame-benchmark"));
            task.getTransformCacheDirectory().set(transformCache);
        });
    }

    /**
     * Registers a headless scenario on Mojang's game test server with its own mod set. Used for the
     * mixed-loader matrix (design doc §15.4): every scenario gets a fresh directory and verifies its
     * own expected log lines.
     */
    static org.gradle.api.tasks.TaskProvider<VerifiedGameRunTask> registerGameTestScenario(
            Project project, String name, FileCollection plexusRuntime, FileCollection mods) {
        JavaToolchainService toolchains = project.getExtensions().getByType(JavaToolchainService.class);
        JavaPluginExtension java = project.getExtensions().getByType(JavaPluginExtension.class);
        TaskProvider<PrepareMinecraftTask> prepareServer =
                project.getTasks().named("prepareMinecraftServer", PrepareMinecraftTask.class);
        return project.getTasks().register(name, VerifiedGameRunTask.class, task -> {
            task.setGroup("verification");
            task.setDescription("Game test server scenario " + name + " through Plexus Mod Loader.");
            configure(
                    task,
                    prepareServer,
                    plexusRuntime,
                    mods,
                    toolchains,
                    java,
                    project.getLayout().getBuildDirectory().dir("plexus/transform-cache"));
            task.getLaunchTarget().set("game_test_server");
            task.getGameArgumentOverrides().set(List.of("--universe", "universe", "--tests", "minecraft:always_pass"));
            task.getRunDirectory().set(project.getLayout().getBuildDirectory().dir("plexus/scenarios/" + name));
            task.getExpectedOutput().add("All 1 required tests passed");
        });
    }

    private static void configure(
            RunMinecraftTask task,
            TaskProvider<PrepareMinecraftTask> prepare,
            FileCollection plexusRuntime,
            FileCollection devMods,
            JavaToolchainService toolchains,
            JavaPluginExtension java,
            Provider<Directory> transformCache) {
        task.getLaunchSpecFile().set(prepare.flatMap(PrepareMinecraftTask::getLaunchSpecFile));
        task.setClasspath(plexusRuntime);
        task.getDevModJars().from(devMods);
        task.getJavaLauncher().set(toolchains.launcherFor(java.getToolchain()));
        task.getTransformCacheDirectory().set(transformCache);
    }
}
