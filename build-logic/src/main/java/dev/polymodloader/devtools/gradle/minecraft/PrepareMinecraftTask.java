package dev.polymodloader.devtools.gradle.minecraft;

import java.nio.file.Path;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

/** Downloads (or copies from local files) and verifies one side of a Minecraft distribution and writes its launch spec. */
@DisableCachingByDefault(because = "Downloads into a machine-wide cache outside the build directory")
public abstract class PrepareMinecraftTask extends DefaultTask {

    /** Minecraft version id, e.g. {@code 26.2}. */
    @Input
    public abstract Property<String> getGameVersion();

    /** {@code client} or {@code server}. */
    @Input
    public abstract Property<String> getSide();

    /** Machine-wide download cache (Gradle user home), shared between checkouts. */
    @Internal
    public abstract DirectoryProperty getCacheDirectory();

    /**
     * Hand-copied Minecraft files ({@link LocalMinecraftFiles}) that seed the cache instead of
     * downloading; absent when there are none.
     */
    @Internal
    public abstract DirectoryProperty getLocalFilesDirectory();

    @OutputFile
    public abstract RegularFileProperty getLaunchSpecFile();

    public PrepareMinecraftTask() {
        // Re-run when someone cleaned the shared cache even though inputs did not change.
        getOutputs().upToDateWhen(task -> {
            Path spec = getLaunchSpecFile().get().getAsFile().toPath();
            return spec.toFile().isFile() && MinecraftLaunchSpec.read(spec).allFilesExist();
        });
    }

    @TaskAction
    public void prepare() {
        Path localFiles = getLocalFilesDirectory().isPresent()
                ? getLocalFilesDirectory().get().getAsFile().toPath()
                : null;
        MinecraftDistributionInstaller installer = new MinecraftDistributionInstaller(
                getCacheDirectory().get().getAsFile().toPath(),
                LocalMinecraftFiles.at(localFiles, getLogger()),
                getLogger());
        String side = getSide().get();
        MinecraftLaunchSpec spec =
                switch (side) {
                    case "client" -> installer.installClient(getGameVersion().get());
                    case "server" -> installer.installServer(getGameVersion().get());
                    default -> throw new IllegalArgumentException("Unknown side '" + side + "', expected client or server");
                };
        spec.write(getLaunchSpecFile().get().getAsFile().toPath());
        getLogger()
                .lifecycle(
                        "Prepared Minecraft {} {} ({} libraries)",
                        spec.gameVersion(),
                        side,
                        spec.libraries().size());
    }
}
