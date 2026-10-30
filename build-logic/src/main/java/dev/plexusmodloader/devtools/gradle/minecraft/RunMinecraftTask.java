package dev.plexusmodloader.devtools.gradle.minecraft;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.inject.Inject;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.work.DisableCachingByDefault;

/**
 * Starts Minecraft through the Poly Mod Loader bootstrap. The JVM class path holds Poly Mod Loader plus Minecraft's
 * libraries; the game jar and mod jars are handed to Poly Mod Loader, which loads them through its
 * transforming game class loader (ADR-0003).
 */
@DisableCachingByDefault(because = "Runs the game")
public abstract class RunMinecraftTask extends JavaExec {

    static final String BOOTSTRAP_MAIN_CLASS = "dev.plexusmodloader.bootstrap.PlexusLauncherMain";

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getLaunchSpecFile();

    /** {@code client}, {@code dedicated_server}, or {@code game_test_server}. */
    @Input
    public abstract Property<String> getLaunchTarget();

    /** Program arguments replacing the manifest's; empty means use the manifest's. */
    @Input
    public abstract ListProperty<String> getGameArgumentOverrides();

    /** Game directory (worlds, options, logs). The user-managed {@code mods/} folder lives here. */
    @Internal
    public abstract DirectoryProperty getRunDirectory();

    /** Mod jars built in this repository, passed to Poly Mod Loader directly instead of being copied. */
    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getDevModJars();

    /** Transformation cache shared by all development runs; cache keys make sharing safe. */
    @Internal
    public abstract DirectoryProperty getTransformCacheDirectory();


    /**
     * Dedicated servers refuse to start until the Minecraft EULA is accepted. Poly Mod Loader never accepts
     * it on the user's behalf; when true this task fails early with instructions instead.
     */
    @Input
    public abstract Property<Boolean> getRequireAcceptedEula();

    @Inject
    protected abstract ObjectFactory getObjects();

    public RunMinecraftTask() {
        getMainClass().set(BOOTSTRAP_MAIN_CLASS);
        getRequireAcceptedEula().convention(false);
        getGameArgumentOverrides().convention(List.of());
    }

    @Override
    public void exec() {
        MinecraftLaunchSpec spec = MinecraftLaunchSpec.read(getLaunchSpecFile().get().getAsFile().toPath());
        Path runDirectory = getRunDirectory().get().getAsFile().toPath();
        try {
            Files.createDirectories(runDirectory.resolve("mods"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (getRequireAcceptedEula().get() && !eulaAccepted(runDirectory)) {
            throw new GradleException("The Minecraft dedicated server requires accepting the Minecraft EULA "
                    + "(https://aka.ms/MinecraftEULA). Read it, then create " + runDirectory.resolve("eula.txt")
                    + " containing 'eula=true'. Poly Mod Loader does not accept it for you.");
        }

        // The game jar goes on the class path last, as the Minecraft Launcher and the server install put it, so mods
        // that read the game's files through the system class loader (CustomSkinLoader reads version.json) find
        // them as in a real install; poly.gameJar still names it.
        setClasspath(getClasspath()
                .plus(getObjects().fileCollection().from(spec.libraries()))
                .plus(getObjects().fileCollection().from(spec.gameJar())));
        setWorkingDir(runDirectory.toFile());
        jvmArgs(GameLaunchArguments.jvmArguments(
                spec,
                getLaunchTarget().get(),
                runDirectory,
                getDevModJars().getFiles(),
                GameLaunchArguments.developmentLogConfig(runDirectory),
                getTransformCacheDirectory().get().getAsFile()));
        args(GameLaunchArguments.gameArguments(spec, getGameArgumentOverrides().get(), List.of(), runDirectory));
        super.exec();
    }

    private static boolean eulaAccepted(Path runDirectory) {
        Path eula = runDirectory.resolve("eula.txt");
        try {
            return Files.isRegularFile(eula)
                    && Files.readAllLines(eula, StandardCharsets.UTF_8).stream()
                            .map(String::strip)
                            .anyMatch("eula=true"::equalsIgnoreCase);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
