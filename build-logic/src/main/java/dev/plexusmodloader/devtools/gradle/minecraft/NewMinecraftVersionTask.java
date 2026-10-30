package dev.plexusmodloader.devtools.gradle.minecraft;

import java.nio.file.Path;
import java.util.List;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

/**
 * {@code ./gradlew newMinecraftVersion -Pfrom=26.2 -Pto=26.3 [-Pjava=25]}: copies the version
 * modules of {@code from} for {@code to} with {@link VersionScaffolder} and prints what to do next.
 */
@DisableCachingByDefault(because = "Creates source files in the repository")
public abstract class NewMinecraftVersionTask extends DefaultTask {

    /** The supported version to copy. */
    @Input
    public abstract Property<String> getFrom();

    /** The new version. */
    @Input
    public abstract Property<String> getTo();

    /** Java feature version of the new version; defaults to that of {@code from}. */
    @Input
    @Optional
    public abstract Property<Integer> getJavaVersion();

    /**
     * List the new version under {@code experimental} instead of {@code supported}: modules that build, but
     * nothing offered to players (ADR-0019, {@link MinecraftVersions}).
     */
    @Input
    public abstract Property<Boolean> getExperimental();

    @Internal
    public abstract DirectoryProperty getRepositoryRoot();

    /** Hand-copied Minecraft files ({@link LocalMinecraftFiles}); absent when there are none. */
    @Internal
    public abstract DirectoryProperty getLocalMinecraftFiles();

    @TaskAction
    public void scaffold() {
        if (!getFrom().isPresent() || !getTo().isPresent()) {
            throw new GradleException("Usage: ./gradlew newMinecraftVersion -Pfrom=<supported version> -Pto=<new version>"
                    + " [-Pjava=<Java feature version>] [-Pexperimental=true]");
        }
        Path root = getRepositoryRoot().get().getAsFile().toPath();
        String from = getFrom().get();
        String to = getTo().get();
        int java = getJavaVersion()
                .getOrElse(MinecraftVersions.read(root).javaVersion(from));
        List<Path> created;
        try {
            created = VersionScaffolder.scaffold(root, from, to, java, getExperimental().get());
        } catch (IllegalArgumentException e) {
            throw new GradleException(e.getMessage(), e);
        }
        getLogger()
                .lifecycle(
                        """
                        Created {} and {} ({} files) for Minecraft {} (Java {}), copied from {}.
                        Next:
                          1. Compile against the new game: ./gradlew :{}:compileJava
                             Fix what changed in the game (renamed classes, methods, parameters).
                          2. Check every transformation rule in the new adapter against the new jar
                             (owner, method, descriptor), e.g. with javap; rules that miss a target
                             are reported by name in .poly/transformation-report.json.
                          3. Update version data in the game layer: data/resource pack formats and
                             anything else the game versions.
                          4. Verify with the version picked: ./gradlew check
                             :poly-bootstrap:nativeApiGameTests :poly-bootstrap:compatibilityMatrix
                             :poly-bootstrap:serverBootSmokeTest -PpolyMinecraft={}
                          5. Make it the default in {} when it is ready.""",
                        MinecraftVersions.adapterModule(to),
                        MinecraftVersions.gameLayerModule(to),
                        created.size(),
                        to,
                        java,
                        from,
                        MinecraftVersions.gameLayerModule(to),
                        to,
                        MinecraftVersions.FILE);
        Path local = getLocalMinecraftFiles().isPresent()
                ? getLocalMinecraftFiles().get().getAsFile().toPath()
                : null;
        getLogger().lifecycle(LocalMinecraftFiles.at(local, getLogger()).describe(to));
    }
}
