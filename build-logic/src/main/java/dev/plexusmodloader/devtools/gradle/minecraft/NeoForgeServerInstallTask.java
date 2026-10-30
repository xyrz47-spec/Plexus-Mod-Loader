package dev.plexusmodloader.devtools.gradle.minecraft;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.TaskAction;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.work.DisableCachingByDefault;

/**
 * Installs a real NeoForge dedicated server with NeoForge's own installer ({@code --installServer}), as a server owner
 * does, for comparisons with NeoForge itself, not with Poly Mod Loader (the Sinytra Connector benchmark,
 * docs/PERFORMANCE.md). The installer comes from NeoForge's Maven and is checked against {@link #getInstallerSha1()};
 * it downloads Minecraft's server and NeoForge's libraries and applies NeoForge's patches itself. A finished
 * installation is reused while the installer is the same. Nothing is started, so no EULA is accepted: the installer
 * writes no {@code eula.txt}, and the benchmarks start Mojang's game test server, which does not ask for one.
 */
@DisableCachingByDefault(because = "Runs an external installer that downloads from the network")
public abstract class NeoForgeServerInstallTask extends DefaultTask {

    /** The NeoForge version, such as {@code 26.2.0.93}. */
    @Input
    public abstract Property<String> getNeoForgeVersion();

    /** SHA-1 of {@code neoforge-<version>-installer.jar} on NeoForge's Maven. */
    @Input
    public abstract Property<String> getInstallerSha1();

    @Nested
    public abstract Property<JavaLauncher> getJavaLauncher();

    /** The server folder: {@code libraries/}, {@code run.bat}, {@code user_jvm_args.txt}. */
    @Internal
    public abstract DirectoryProperty getServerDirectory();

    @TaskAction
    public void install() throws IOException, InterruptedException {
        String version = getNeoForgeVersion().get();
        String sha1 = getInstallerSha1().get();
        Path server = getServerDirectory().get().getAsFile().toPath();
        Path stamp = server.resolve(".poly-installed");
        if (Files.isRegularFile(stamp)
                && Files.readString(stamp, StandardCharsets.UTF_8).strip().equals(sha1)
                && Files.isRegularFile(NeoForgeServerLaunch.argumentsFile(server, version))) {
            getLogger().lifecycle("NeoForge {} server already installed in {}", version, server);
            return;
        }
        VerifiedGameRunTask.deleteRecursively(server);
        Files.createDirectories(server);
        Path installer = server.getParent().resolve("neoforge-" + version + "-installer.jar");
        new VerifiedDownloader().downloadSha1Verified(installerUri(version), sha1, installer);
        List<String> command = List.of(
                getJavaLauncher().get().getExecutablePath().getAsFile().getAbsolutePath(),
                "-jar",
                installer.toString(),
                "--installServer",
                server.toString());
        Path log = server.getParent().resolve("neoforge-" + version + "-install.log");
        Process process = new ProcessBuilder(command)
                .directory(server.toFile())
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start();
        if (!process.waitFor(20, TimeUnit.MINUTES)) {
            process.destroyForcibly();
            throw new GradleException("NeoForge's installer timed out; see " + log);
        }
        String output = Files.readString(log, StandardCharsets.UTF_8);
        if (process.exitValue() != 0 || !output.contains("The server installed successfully")) {
            throw new GradleException(
                    "NeoForge's installer failed (exit " + process.exitValue() + "); see " + log);
        }
        if (!Files.isRegularFile(NeoForgeServerLaunch.argumentsFile(server, version))) {
            throw new GradleException("NeoForge's installer wrote no "
                    + NeoForgeServerLaunch.argumentsFile(server, version) + "; see " + log);
        }
        Files.writeString(stamp, sha1 + "\n", StandardCharsets.UTF_8);
        getLogger().lifecycle("Installed the NeoForge {} server in {}", version, server);
    }

    static URI installerUri(String version) {
        return URI.create("https://maven.neoforged.net/releases/net/neoforged/neoforge/" + version + "/neoforge-"
                + version + "-installer.jar");
    }
}
