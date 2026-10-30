package dev.polymodloader.devtools.gradle.minecraft;

import dev.polymodloader.devtools.gradle.mod.InitPolyModTask;
import dev.polymodloader.devtools.gradle.mod.ModResourceTasks;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.plugins.JavaLibraryPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainService;

/**
 * Plugin for Poly Mod projects (mods built on Poly Mod Loader's Native API) ({@code plugins { id("dev.polymodloader.mod") }}).
 *
 * <ul>
 *   <li>Java toolchain matching the Minecraft version the mod runs on: Poly Mod Loader's default, or the one set
 *       with {@code polyMinecraft=<version>} in {@code gradle.properties}
 *   <li>{@code compileOnly} dependencies on the public Poly Mod Loader API and SLF4J (both provided at run time)
 *   <li>{@code validatePolyModJson} — runs Poly Mod Loader's own metadata validator; part of {@code check}
 *   <li>{@code polyResources { item(..); block(..) }} and {@code generatePolyResources} — the standard model,
 *       block state, item definition, loot table, name and tool tag files of simple content
 *   <li>{@code checkPolyResources} — finds broken resource paths, JSON and references; part of {@code check}
 *   <li>{@code runClient}, {@code runServer}, {@code worldCreateSaveTest}, {@code clientWorldReloadTest},
 *       {@code serverBootSmokeTest} — run the game through the published Poly Mod Loader runtime with this mod
 *   <li>{@code initPolyMod} — renames an unzipped copy of the mod template to a new mod ID, name and package
 * </ul>
 *
 * <p>Poly Mod Loader artifacts come from the public SDK repository {@value #SDK_REPOSITORY_URL} (ADR-0008), then
 * from Maven Local, where contributors put local builds ({@code ./gradlew publishToMavenLocal} in the
 * Poly Mod Loader repository). Gradle takes a version from the first repository that has it, so a local build of a
 * published version needs {@code -P}{@value #LOCAL_SDK_PROPERTY}, which puts Maven Local first.
 */
public final class PolyModPlugin implements Plugin<Project> {

    /** The public SDK repository on the Poly Mod Loader website ({@code publishSdk} in the Poly Mod Loader repository). */
    public static final String SDK_REPOSITORY_URL = "https://www.polymc.net/maven/";

    /** The name mod projects give the SDK repository ({@code maven(SDK_REPOSITORY_URL) { name = ... }}). */
    public static final String SDK_REPOSITORY_NAME = "Poly Mod Loader";

    /**
     * The Gradle property ({@code -PpolyLocalSdk}) that puts Maven Local before the SDK repository, for
     * contributors trying a local Poly Mod Loader build of a version that is already published.
     */
    public static final String LOCAL_SDK_PROPERTY = "polyLocalSdk";

    /** The module a mod compiles against ({@code compileOnly}); its API dependencies come with it. */
    public static final String API_MODULE = "poly-lifecycle-api";

    /** The module that starts the game for {@code runClient} and the game tests, with a version adapter. */
    public static final String LAUNCHER_MODULE = "poly-bootstrap";

    /** The module that checks {@code poly.mod.json} ({@code validatePolyModJson}). */
    public static final String METADATA_MODULE = "poly-mod-metadata";

    @Override
    public void apply(Project project) {
        Properties versions = versions();
        String polyVersion = versions.getProperty("poly");
        // The Minecraft version to run: gradle.properties polyMinecraft=<version>, or Poly Mod Loader's default.
        MinecraftVersions minecraftVersions = MinecraftVersions.bundled();
        String gameVersion = minecraftVersions.selected(project);

        project.getPluginManager().apply(JavaLibraryPlugin.class);
        // -PpolyLocalSdk: Maven Local first, so a local build of a version the SDK repository already has wins.
        boolean localFirst =
                project.getProviders().gradleProperty(LOCAL_SDK_PROPERTY).isPresent();
        if (localFirst) {
            project.getRepositories().mavenLocal();
        }
        project.getRepositories().maven(repository -> {
            repository.setName(SDK_REPOSITORY_NAME);
            repository.setUrl(SDK_REPOSITORY_URL);
            repository.content(content -> content.includeGroup("dev.polymodloader"));
        });
        if (!localFirst) {
            project.getRepositories().mavenLocal();
        }
        project.getRepositories().mavenCentral();
        // The Fabric bridge needs Fabric Loader's API interfaces at run time; only that group comes
        // from FabricMC's repository.
        project.getRepositories().exclusiveContent(content -> content.forRepositories(project.getRepositories()
                        .maven(repository -> {
                            repository.setName("FabricMC");
                            repository.setUrl("https://maven.fabricmc.net/");
                        }))
                .filter(filter -> filter.includeGroup("net.fabricmc")));

        JavaPluginExtension java = project.getExtensions().getByType(JavaPluginExtension.class);
        java.getToolchain()
                .getLanguageVersion()
                .set(JavaLanguageVersion.of(minecraftVersions.javaVersion(gameVersion)));

        project.getDependencies().add("compileOnly", "dev.polymodloader:" + API_MODULE + ":" + polyVersion);
        project.getDependencies().add("compileOnly", "org.slf4j:slf4j-api:" + versions.getProperty("slf4j"));

        Configuration runtime = project.getConfigurations().create("polyRuntime", configuration -> {
            configuration.setCanBeConsumed(false);
            configuration.setCanBeResolved(true);
        });
        project.getDependencies().add("polyRuntime", "dev.polymodloader:" + LAUNCHER_MODULE + ":" + polyVersion);
        project.getDependencies()
                .add(
                        "polyRuntime",
                        "dev.polymodloader:"
                                + MinecraftVersions.adapterModule(minecraftVersions.modulesVersion(gameVersion))
                                + ":"
                                + polyVersion);

        Configuration validator = project.getConfigurations().create("polyMetadataValidator", configuration -> {
            configuration.setCanBeConsumed(false);
            configuration.setCanBeResolved(true);
        });
        project.getDependencies().add("polyMetadataValidator", "dev.polymodloader:" + METADATA_MODULE + ":" + polyVersion);

        JavaToolchainService toolchains = project.getExtensions().getByType(JavaToolchainService.class);
        java.io.File plexusJson = project.file("src/main/resources/plexus.mod.json");
        java.io.File targetJson = plexusJson.exists() ? plexusJson : project.file("src/main/resources/poly.mod.json");
        TaskProvider<JavaExec> validate = project.getTasks().register("validatePolyModJson", JavaExec.class, task -> {
            task.setGroup("verification");
            task.setDescription("Validates src/main/resources/plexus.mod.json or poly.mod.json with Plexus Mod Loader's metadata reader.");
            task.setClasspath(validator);
            task.getMainClass().set("dev.polymodloader.runtime.metadata.ModMetadataValidatorMain");
            task.args(targetJson.getAbsolutePath());
            task.getInputs().file(targetJson);
            task.getJavaLauncher().set(toolchains.launcherFor(java.getToolchain()));
        });
        project.getTasks().named("check").configure(check -> check.dependsOn(validate));
        ModResourceTasks.register(project);
        project.getTasks().register("initPolyMod", InitPolyModTask.class, task -> {
            task.setGroup("poly mod");
            task.setDescription("Renames this copy of the mod template to a new mod"
                    + " (-PmodId=<mod_id> [-PmodName=<name>] [-PmodPackage=<package>]).");
            task.getModId().set(project.getProviders().gradleProperty("modId"));
            task.getModName().set(project.getProviders().gradleProperty("modName"));
            task.getModPackage().set(project.getProviders().gradleProperty("modPackage"));
            task.getProjectDirectory().set(project.getLayout().getProjectDirectory());
        });

        MinecraftRunTasks.register(
                project,
                runtime,
                project.files(project.getTasks().named("jar")),
                gameVersion,
                project.getLayout().getProjectDirectory().dir("run"));
    }

    private static Properties versions() {
        Properties properties = new Properties();
        try (InputStream input = PolyModPlugin.class.getResourceAsStream("poly-plugin.properties")) {
            if (input == null) {
                throw new IllegalStateException("poly-plugin.properties is missing from the Poly Mod Loader Gradle plugin");
            }
            properties.load(input);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return properties;
    }
}
