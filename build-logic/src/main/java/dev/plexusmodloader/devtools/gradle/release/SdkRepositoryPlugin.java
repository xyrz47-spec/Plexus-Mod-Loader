package dev.plexusmodloader.devtools.gradle.release;

import dev.plexusmodloader.devtools.gradle.PolyJavaConventionsPlugin;
import dev.plexusmodloader.devtools.gradle.SdkPublicationExtension;
import dev.plexusmodloader.devtools.gradle.minecraft.MinecraftVersions;
import dev.plexusmodloader.devtools.gradle.minecraft.PolyModPlugin;
import dev.plexusmodloader.devtools.gradle.mod.ModTemplatePlugin;
import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin;
import org.gradle.api.tasks.TaskProvider;

/**
 * Root-project plugin of the Poly Mod Loader repository ({@code poly.sdk-repository}, ADR-0008): {@code publishSdk}
 * publishes what a mod project needs, and nothing else, into a Maven repository folder that keeps every version
 * it was given. The website build copies that folder to {@code /maven/}.
 *
 * <ul>
 *   <li>the modules {@code dev.polymodloader.mod} resolves ({@link PolyModPlugin#API_MODULE}, {@link
 *       PolyModPlugin#LAUNCHER_MODULE}, {@link PolyModPlugin#METADATA_MODULE}, the version adapter of every
 *       supported Minecraft version) with every module they depend on, found in the dependency graph
 *   <li>JavaDoc jars only for the API module and the API modules it exposes; no sources
 *   <li>{@code dev.plexusmodloader:poly-gradle-plugin} and its marker {@code dev.polymodloader.mod}, from the included
 *       build {@code build-logic} ({@code :publishSdk} there)
 *   <li>{@code dev.plexusmodloader:plexus-mod-template:<version>@zip} ({@code packageModTemplate})
 * </ul>
 *
 * <p>The folder is {@code .poly/sdk-maven} in the repository, or {@code -PpolySdkRepo=<folder>} (relative to
 * the repository). {@code clean} does not touch it.
 */
public final class SdkRepositoryPlugin implements Plugin<Project> {

    /** The Gradle property that moves the SDK repository folder. */
    public static final String REPOSITORY_PROPERTY = "polySdkRepo";

    /** The SDK repository folder relative to the repository root, unless {@value #REPOSITORY_PROPERTY} is set. */
    public static final String DEFAULT_REPOSITORY = ".poly/sdk-maven";

    /** The Gradle plugin's artifact ID, set in {@code build-logic/build.gradle.kts}. */
    public static final String GRADLE_PLUGIN_ARTIFACT = "poly-gradle-plugin";

    /** The plugin ID mod projects apply; its marker artifact is {@code <id>:<id>.gradle.plugin}. */
    public static final String MOD_PLUGIN_ID = "dev.polymodloader.mod";

    private static final String BUILD_LOGIC = "build-logic";
    private static final List<String> NOT_SDK = List.of(":examples:", ":test-mods:", ":tools:", ":plexus-installer");

    @Override
    public void apply(Project root) {
        if (root != root.getRootProject()) {
            throw new GradleException("poly.sdk-repository belongs to the root project");
        }
        root.getPluginManager().apply(MavenPublishPlugin.class);
        root.getPluginManager().apply(ModTemplatePlugin.class);
        String group = String.valueOf(root.property("poly.group"));
        String version = String.valueOf(root.property("poly.version"));
        File repository = repositoryDirectory(root);

        PublishingExtension publishing = root.getExtensions().getByType(PublishingExtension.class);
        publishing.getRepositories().maven(maven -> {
            maven.setName(PolyJavaConventionsPlugin.SDK_REPOSITORY);
            maven.setUrl(repository);
        });
        publishing.getPublications().create("modTemplate", MavenPublication.class, publication -> {
            publication.setGroupId(group);
            publication.setArtifactId(ModTemplatePlugin.TEMPLATE_ARTIFACT);
            publication.setVersion(version);
            publication.artifact(root.getTasks().named("packageModTemplate"));
            publication.getPom().setPackaging("zip");
            publication.getPom().getDescription().set("The Poly Mod Loader mod template; unzip it, then run initPolyMod.");
        });

        TaskProvider<VerifySdkRepositoryTask> verify = root.getTasks()
                .register("verifySdkRepository", VerifySdkRepositoryTask.class, task -> {
                    task.setGroup("publishing");
                    task.setDescription("Checks the SDK repository: complete, no sources, every dependency present.");
                    task.getRepository().set(repository);
                    task.getVersion().set(version);
                    task.getOtherArtifacts()
                            .addAll(
                                    group + ":" + GRADLE_PLUGIN_ARTIFACT + ":jar",
                                    MOD_PLUGIN_ID + ":" + MOD_PLUGIN_ID + ".gradle.plugin:pom",
                                    group + ":" + ModTemplatePlugin.TEMPLATE_ARTIFACT + ":zip");
                    task.dependsOn("publishModTemplatePublicationToPolySdkRepository");
                    task.dependsOn(root.getGradle().includedBuild(BUILD_LOGIC).task(":publishSdk"));
                });
        root.getTasks().register("publishSdk", task -> {
            task.setGroup("publishing");
            task.setDescription("Publishes what mod projects need (no sources) into the SDK repository folder "
                    + DEFAULT_REPOSITORY + " or -P" + REPOSITORY_PROPERTY + "=<folder>, then checks it.");
            task.dependsOn(verify);
        });

        root.getGradle().projectsEvaluated(gradle -> {
            Set<Project> modules = closure(root, startModules(root), List.of("apiElements", "runtimeElements"));
            Set<Project> javadoc = closure(root, List.of(PolyModPlugin.API_MODULE), List.of("apiElements"));
            List<String> plain = new ArrayList<>();
            List<String> documented = new ArrayList<>();
            for (Project module : modules) {
                for (String excluded : NOT_SDK) {
                    if (module.getPath().startsWith(excluded)) {
                        throw new GradleException(module.getPath() + " is not part of the mod SDK, but an SDK module"
                                + " depends on it; remove that dependency");
                    }
                }
                SdkPublicationExtension sdk = module.getExtensions().findByType(SdkPublicationExtension.class);
                if (sdk == null) {
                    throw new GradleException(module.getPath() + " is an SDK module without poly.java-conventions");
                }
                sdk.getJavadoc().set(javadoc.contains(module));
                (javadoc.contains(module) ? documented : plain).add(module.getName());
                module.getExtensions().getByType(PublishingExtension.class).getRepositories().maven(maven -> {
                    maven.setName(PolyJavaConventionsPlugin.SDK_REPOSITORY);
                    maven.setUrl(repository);
                });
                verify.configure(task -> task.dependsOn(module.getPath() + ":publishSdkPublicationToPolySdkRepository"));
            }
            verify.configure(task -> {
                task.getModules().set(plain);
                task.getJavadocModules().set(documented);
            });
        });
    }

    /** The SDK repository folder: {@value #REPOSITORY_PROPERTY}, or {@value #DEFAULT_REPOSITORY}. */
    public static File repositoryDirectory(Project root) {
        String configured = root.getProviders()
                .gradleProperty(REPOSITORY_PROPERTY)
                .getOrElse(DEFAULT_REPOSITORY);
        return root.getRootDir().toPath().resolve(configured).normalize().toFile();
    }

    private static List<String> startModules(Project root) {
        List<String> start = new ArrayList<>(List.of(
                PolyModPlugin.API_MODULE, PolyModPlugin.LAUNCHER_MODULE, PolyModPlugin.METADATA_MODULE));
        for (String version : MinecraftVersions.of(root).supported()) {
            start.add(MinecraftVersions.adapterModule(version));
        }
        return start;
    }

    /** The projects named, with every project they reach through the project dependencies of {@code variants}. */
    private static Set<Project> closure(Project root, List<String> start, List<String> variants) {
        Set<Project> found = new LinkedHashSet<>();
        Deque<Project> pending = new ArrayDeque<>();
        for (String name : start) {
            pending.add(root.project(":" + name));
        }
        while (!pending.isEmpty()) {
            Project project = pending.removeFirst();
            if (!found.add(project)) {
                continue;
            }
            Set<String> paths = new TreeSet<>();
            for (String variant : variants) {
                project.getConfigurations()
                        .getByName(variant)
                        .getAllDependencies()
                        .withType(ProjectDependency.class)
                        .forEach(dependency -> paths.add(dependency.getPath()));
            }
            for (String path : paths) {
                pending.add(root.project(path));
            }
        }
        return found;
    }
}
