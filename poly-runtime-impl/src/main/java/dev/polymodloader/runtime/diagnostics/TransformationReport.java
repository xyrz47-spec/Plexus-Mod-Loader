package dev.polymodloader.runtime.diagnostics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.polymodloader.internal.transform.engine.TransformReportEntry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Every class that was actually transformed during a run, in load order, with the applied rules,
 * the SHA-256 before and after, and whether the result came from the cache (design doc §5.10
 * "Transformation Report", §12.3). Written when the game exits.
 *
 * @param gameVersion Minecraft version
 * @param transformedClasses one entry per transformed class
 */
public record TransformationReport(String gameVersion, List<TransformReportEntry> transformedClasses) {

    /** File name inside the report directory. */
    public static final String FILE_NAME = "transformation-report.json";

    private static final Gson GSON =
            new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public TransformationReport {
        transformedClasses = List.copyOf(transformedClasses);
    }

    /** Writes the report to {@code directory}/{@value #FILE_NAME}. */
    public Path writeTo(Path directory) {
        Path file = directory.resolve(FILE_NAME);
        try {
            Files.createDirectories(directory);
            Files.writeString(file, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write " + file, e);
        }
        return file;
    }

    /** Reads a report written by {@link #writeTo}. */
    public static TransformationReport read(Path file) {
        try {
            return GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), TransformationReport.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + file, e);
        }
    }
}
