package dev.plexusmodloader.devtools.gradle.mod;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Stream;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.IgnoreEmptyDirectories;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/**
 * {@code generatePlexusResources}: writes the files {@link ModResourceGenerator} makes for the {@code
 * plexusResources} entries into a directory the mod's resources include.
 */
@CacheableTask
public abstract class GeneratePlexusResourcesTask extends DefaultTask {

    /** The mod ID; the plugin reads it from {@code plexus.mod.json}. */
    @Input
    public abstract Property<String> getModId();

    @Input
    public abstract ListProperty<ContentResourceDefinition> getDefinitions();

    /** The mod's own resources, so the task reruns when a hand-written file appears or changes. */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    @IgnoreEmptyDirectories
    public abstract ConfigurableFileCollection getHandWrittenFiles();

    /** Directory of the hand-written resources ({@code src/main/resources}). */
    @Internal
    public abstract DirectoryProperty getHandWrittenDirectory();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @TaskAction
    public void generate() throws IOException {
        Path output = getOutputDirectory().get().getAsFile().toPath();
        deleteContents(output);
        Files.createDirectories(output);
        Map<String, String> files;
        try {
            files = ModResourceGenerator.generate(
                    getModId().get(),
                    getDefinitions().get(),
                    getHandWrittenDirectory().get().getAsFile().toPath());
        } catch (IllegalArgumentException e) {
            throw new GradleException(e.getMessage(), e);
        }
        for (Map.Entry<String, String> file : files.entrySet()) {
            Path target = output.resolve(file.getKey());
            Files.createDirectories(target.getParent());
            Files.writeString(target, file.getValue(), StandardCharsets.UTF_8);
        }
        if (!files.isEmpty()) {
            getLogger().info("Generated {} resource files in {}", files.size(), output);
        }
    }

    private static void deleteContents(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(directory)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                if (!path.equals(directory)) {
                    Files.delete(path);
                }
            }
        }
    }

    /**
     * The {@code id} of a {@code plexus.mod.json}.
     *
     * @throws GradleException if the file is missing, is not JSON, or has no text {@code id}
     */
    public static String modId(File plexusModJson) {
        if (!plexusModJson.isFile()) {
            throw new GradleException("plexusResources needs the mod ID from " + plexusModJson + ", which does not exist");
        }
        try {
            JsonElement json = JsonParser.parseString(Files.readString(plexusModJson.toPath(), StandardCharsets.UTF_8));
            JsonElement id = json.isJsonObject() ? json.getAsJsonObject().get("id") : null;
            if (id == null || !id.isJsonPrimitive() || !id.getAsJsonPrimitive().isString()) {
                throw new GradleException(plexusModJson + " has no \"id\"; validatePlexusModJson tells what is missing");
            }
            return id.getAsString();
        } catch (JsonParseException e) {
            throw new GradleException(plexusModJson + " is not valid JSON: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new GradleException("Cannot read " + plexusModJson, e);
        }
    }
}
