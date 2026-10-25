package dev.polymodloader.devtools.gradle;

import com.diffplug.gradle.spotless.SpotlessExtension;
import com.diffplug.gradle.spotless.SpotlessPlugin;
import java.io.File;
import java.util.List;
import javax.inject.Inject;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.MinimalExternalModuleDependency;
import org.gradle.api.artifacts.VersionCatalog;
import org.gradle.api.artifacts.VersionCatalogsExtension;
import org.gradle.api.component.AdhocComponentWithVariants;
import org.gradle.api.component.ConfigurationVariantDetails;
import org.gradle.api.component.SoftwareComponentFactory;
import org.gradle.api.plugins.JavaLibraryPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin;
import org.gradle.api.publish.maven.tasks.PublishToMavenLocal;
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.api.tasks.testing.Test;
import org.gradle.external.javadoc.StandardJavadocDocletOptions;
import org.gradle.jvm.tasks.Jar;
import org.gradle.jvm.toolchain.JavaLanguageVersion;

/**
 * Shared Java build rules for every Poly Mod Loader module: Java toolchain, compiler strictness, JUnit, and
 * formatting checks wired into {@code check} (design doc §6.1, §7.4).
 *
 * <p>Every module has two publications with the same coordinates {@code dev.polymodloader:<module name>}:
 *
 * <ul>
 *   <li>{@code maven}: classes, sources and JavaDoc, for {@code publishToMavenLocal} (contributors)
 *   <li>{@code sdk}: classes, never sources, and JavaDoc only when {@link SdkPublicationExtension#getJavadoc()}
 *       is set. Only the public SDK repository receives it ({@code publishSdk}, ADR-0008), and only for the
 *       modules the root plugin {@code poly.sdk-repository} picks
 * </ul>
 */
public abstract class PolyJavaConventionsPlugin implements Plugin<Project> {

    /** The publication that goes into the public SDK repository. */
    public static final String SDK_PUBLICATION = "sdk";

    /** The name of the public SDK repository in {@code publishing.repositories}. */
    public static final String SDK_REPOSITORY = "polySdk";

    private final SoftwareComponentFactory componentFactory;

    @Inject
    public PolyJavaConventionsPlugin(SoftwareComponentFactory componentFactory) {
        this.componentFactory = componentFactory;
    }

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(JavaLibraryPlugin.class);
        project.getPluginManager().apply(SpotlessPlugin.class);
        project.getPluginManager().apply(MavenPublishPlugin.class);

        VersionCatalog libs = project.getExtensions()
                .getByType(VersionCatalogsExtension.class)
                .named("libs");

        project.setGroup(project.property("poly.group"));
        project.setVersion(project.property("poly.version"));

        JavaPluginExtension java = project.getExtensions().getByType(JavaPluginExtension.class);
        int javaVersion = Integer.parseInt(requiredVersion(libs, "java"));
        java.getToolchain().getLanguageVersion().set(JavaLanguageVersion.of(javaVersion));
        java.withSourcesJar();
        java.withJavadocJar();

        // Every module is published as dev.polymodloader:<module name> so mod projects can depend on
        // the API modules and run the game through the published runtime (dev.polymodloader.mod plugin).
        PublishingExtension publishing = project.getExtensions().getByType(PublishingExtension.class);
        publishing.getPublications().create("maven", MavenPublication.class, publication -> {
            publication.from(project.getComponents().getByName("java"));
            publication.setArtifactId(project.getName());
        });
        registerSdkPublication(project, publishing);

        // Every jar carries Poly Mod Loader's license and the third-party notices, which say where the source is
        // (MPL 2.0 section 3.2).
        project.getTasks().named("jar", Jar.class).configure(jar -> jar.from(
                project.files(
                        new File(project.getRootDir(), "LICENSE"),
                        new File(project.getRootDir(), "THIRD_PARTY_NOTICES.md")),
                spec -> spec.into("META-INF")));

        project.getTasks().withType(JavaCompile.class).configureEach(compile -> {
            compile.getOptions().setEncoding("UTF-8");
            compile.getOptions().getCompilerArgs().addAll(List.of("-Xlint:all", "-Werror"));
        });
        project.getTasks().withType(Javadoc.class).configureEach(javadoc -> {
            javadoc.getOptions().setEncoding("UTF-8");
            StandardJavadocDocletOptions options = (StandardJavadocDocletOptions) javadoc.getOptions();
            options.addBooleanOption("Xdoclint:all,-missing", true);
            // Without the bundled web fonts (about 3.8 MB per module); browsers use their own.
            options.addBooleanOption("-no-fonts", true);
        });

        project.getDependencies()
                .add("testImplementation", project.getDependencies().platform(library(libs, "junit-bom")));
        project.getDependencies().add("testImplementation", library(libs, "junit-jupiter"));
        project.getDependencies().add("testRuntimeOnly", library(libs, "junit-platform-launcher"));
        // JavaDoc errors fail the build: public API documentation is part of the API (design doc §5.9).
        project.getTasks().named("check").configure(check -> check.dependsOn(project.getTasks().named("javadoc")));
        project.getTasks().withType(Test.class).configureEach(test -> {
            test.useJUnitPlatform();
            test.testLogging(logging -> logging.events("failed", "skipped"));
        });

        SpotlessExtension spotless = project.getExtensions().getByType(SpotlessExtension.class);
        spotless.java(format -> {
            format.target("src/**/*.java");
            format.palantirJavaFormat(requiredVersion(libs, "palantirJavaFormat"));
            format.removeUnusedImports();
            format.trimTrailingWhitespace();
            format.endWithNewline();
        });
    }

    /**
     * The {@code sdk} publication: the {@code java} component's classes and dependencies without sources, and
     * JavaDoc when asked for. The {@code maven} publication never goes to the SDK repository and the {@code sdk}
     * publication never goes to Maven Local.
     */
    private void registerSdkPublication(Project project, PublishingExtension publishing) {
        SdkPublicationExtension sdk =
                project.getExtensions().create("polySdkPublication", SdkPublicationExtension.class);
        sdk.getJavadoc().convention(false);
        AdhocComponentWithVariants component = componentFactory.adhoc("polySdk");
        project.getComponents().add(component);
        addPrimaryVariant(component, project, "apiElements", variant -> variant.mapToMavenScope("compile"));
        addPrimaryVariant(component, project, "runtimeElements", variant -> variant.mapToMavenScope("runtime"));
        // Read when the publication is built, after poly.sdk-repository has picked the API modules.
        addPrimaryVariant(component, project, "javadocElements", variant -> {
            if (sdk.getJavadoc().get()) {
                variant.mapToOptional();
            } else {
                variant.skip();
            }
        });
        publishing.getPublications().create(SDK_PUBLICATION, MavenPublication.class, publication -> {
            publication.from(component);
            publication.setArtifactId(project.getName());
        });
        project.getTasks()
                .withType(PublishToMavenRepository.class)
                .configureEach(task -> task.setEnabled(isSdkPublicationTask(task) == isSdkRepositoryTask(task)));
        project.getTasks()
                .withType(PublishToMavenLocal.class)
                .configureEach(task -> task.setEnabled(!isSdkPublicationTask(task)));
    }

    /**
     * Adds the variant of {@code configuration} itself. Its secondary variants (class and resource folders,
     * which the {@code java} component does not publish either) are skipped: a consumer would pick them and get
     * no files.
     */
    private static void addPrimaryVariant(
            AdhocComponentWithVariants component,
            Project project,
            String configuration,
            Action<ConfigurationVariantDetails> mapping) {
        component.addVariantsFromConfiguration(project.getConfigurations().getByName(configuration), variant -> {
            if (variant.getConfigurationVariant().getName().equals(configuration)) {
                mapping.execute(variant);
            } else {
                variant.skip();
            }
        });
    }

    /** {@code publishSdkPublicationTo...}: the task names maven-publish gives each publication and repository. */
    public static boolean isSdkPublicationTask(Task task) {
        return task.getName().startsWith("publishSdkPublicationTo");
    }

    /** {@code publish...PublicationToPolySdkRepository}. */
    public static boolean isSdkRepositoryTask(Task task) {
        return task.getName().endsWith("PublicationToPolySdkRepository");
    }

    private static Provider<MinimalExternalModuleDependency> library(VersionCatalog libs, String alias) {
        return libs.findLibrary(alias)
                .orElseThrow(() -> new IllegalStateException("Missing library alias in libs.versions.toml: " + alias));
    }

    private static String requiredVersion(VersionCatalog libs, String alias) {
        return libs.findVersion(alias)
                .orElseThrow(() -> new IllegalStateException("Missing version alias in libs.versions.toml: " + alias))
                .getRequiredVersion();
    }
}
