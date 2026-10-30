package dev.plexusmodloader.devtools.gradle.release;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

/**
 * Writes what the end-user installer ({@code poly-installer}) installs: the Poly Mod Loader runtime jars in the
 * Minecraft Launcher's {@code libraries/} layout below {@code META-INF/polymodloader/libraries/} and the index
 * {@code META-INF/polymodloader/installer.json} (version, supported Minecraft versions, main class, and each jar's
 * coordinates, path, SHA-1 and size). With {@link #getAgentLibrary()} and {@link #getAgentClass()} the index also names
 * Poly Mod Loader's Java agent (ADR-0024); the build fails when that library is not among the jars, does not contain the
 * class, or its manifest does not name it as {@code Premain-Class}.
 */
@DisableCachingByDefault(because = "Copies jars; faster than caching")
public abstract class InstallerPayloadTask extends DefaultTask {

    /** Maven coordinates {@code group:artifact:version} of each runtime jar, mapped to the jar. */
    @Input
    public abstract MapProperty<String, String> getLibraries();

    /** The same jars, so a changed jar rebuilds the payload. */
    @InputFiles
    @PathSensitive(PathSensitivity.NAME_ONLY)
    public abstract ConfigurableFileCollection getLibraryFiles();

    @Input
    public abstract Property<String> getPolyVersion();

    @Input
    public abstract ListProperty<String> getMinecraftVersions();

    @Input
    public abstract Property<String> getMainClass();

    /** Maven coordinates of the runtime jar that holds the Java agent; one of {@link #getLibraries()}. */
    @Input
    @Optional
    public abstract Property<String> getAgentLibrary();

    /** The agent class, which that jar's manifest names as {@code Premain-Class}. */
    @Input
    @Optional
    public abstract Property<String> getAgentClass();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @TaskAction
    public void write() throws IOException {
        Path root = getOutputDirectory().get().getAsFile().toPath();
        deleteRecursively(root);
        Path payload = root.resolve("META-INF").resolve("polymodloader");
        JsonArray libraries = new JsonArray();
        for (Map.Entry<String, String> entry : new TreeMap<>(getLibraries().get()).entrySet()) {
            String coordinates = entry.getKey();
            Path jar = new File(entry.getValue()).toPath();
            String path = libraryPath(coordinates);
            Path target = payload.resolve("libraries").resolve(path);
            Files.createDirectories(target.getParent());
            Files.copy(jar, target, StandardCopyOption.REPLACE_EXISTING);
            JsonObject library = new JsonObject();
            library.addProperty("name", coordinates);
            library.addProperty("path", path);
            library.addProperty("sha1", sha1(target));
            library.addProperty("size", Files.size(target));
            libraries.add(library);
        }
        JsonObject index = new JsonObject();
        index.addProperty("polyVersion", getPolyVersion().get());
        JsonArray versions = new JsonArray();
        getMinecraftVersions().get().forEach(versions::add);
        index.add("minecraftVersions", versions);
        index.addProperty("mainClass", getMainClass().get());
        if (getAgentLibrary().isPresent() || getAgentClass().isPresent()) {
            index.add("agent", agent());
        }
        index.add("libraries", libraries);
        Files.writeString(
                payload.resolve("installer.json"),
                new GsonBuilder().setPrettyPrinting().create().toJson(index),
                StandardCharsets.UTF_8);
        getLogger().lifecycle("Installer payload: {} libraries for Minecraft {}", libraries.size(), getMinecraftVersions().get());
    }

    /** The index's {@code agent} entry, after checking that the agent jar is in the payload and holds the class. */
    private JsonObject agent() throws IOException {
        String coordinates = getAgentLibrary().getOrNull();
        String agentClass = getAgentClass().getOrNull();
        if (coordinates == null || agentClass == null) {
            throw new GradleException("The installer payload needs both agentLibrary and agentClass, or neither");
        }
        String jarPath = getLibraries().get().get(coordinates);
        if (jarPath == null) {
            throw new GradleException("The agent library " + coordinates + " is not one of the installer's libraries");
        }
        try (JarFile jar = new JarFile(jarPath)) {
            if (jar.getEntry(agentClass.replace('.', '/') + ".class") == null) {
                throw new GradleException("The agent library " + coordinates + " has no class " + agentClass);
            }
            Manifest manifest = jar.getManifest();
            String premain = manifest == null ? null : manifest.getMainAttributes().getValue("Premain-Class");
            if (!agentClass.equals(premain)) {
                throw new GradleException("The agent library " + coordinates + " names " + premain
                        + " as Premain-Class, not " + agentClass + "; -javaagent would not start it");
            }
        }
        JsonObject agent = new JsonObject();
        agent.addProperty("library", coordinates);
        agent.addProperty("class", agentClass);
        return agent;
    }

    /** {@code group:artifact:version} to the Maven repository path the Minecraft Launcher uses. */
    static String libraryPath(String coordinates) {
        String[] parts = coordinates.split(":");
        if (parts.length != 3) {
            throw new GradleException("Expected group:artifact:version, got " + coordinates);
        }
        return parts[0].replace('.', '/') + "/" + parts[1] + "/" + parts[2] + "/" + parts[1] + "-" + parts[2] + ".jar";
    }

    private static String sha1(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void deleteRecursively(Path directory) {
        if (!Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted((a, b) -> b.getNameCount() - a.getNameCount()).toList()) {
                Files.delete(path);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to clear " + directory, e);
        }
    }
}
