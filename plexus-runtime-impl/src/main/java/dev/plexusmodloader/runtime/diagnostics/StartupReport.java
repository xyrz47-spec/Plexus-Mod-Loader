package dev.plexusmodloader.runtime.diagnostics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Machine-readable summary of one launch: which mods loaded, which files were blocked, which
 * provider is active for every system, and which transformations were planned or rejected. It is
 * the first version of the System Explorer and Transformation Report from design doc §5.10.
 *
 * @param polyVersion Poly Mod Loader runtime version
 * @param gameVersion Minecraft version
 * @param launchTarget launch target ID
 * @param adapterId version adapter used
 * @param mods loaded mods in initialisation order
 * @param blockedFiles discovered jars that were not loaded, with the reason
 * @param foreignMods mods for other loaders that a bridge assessed
 * @param systems active provider per system
 * @param acceptedTransformations accepted transformation rules
 * @param rejectedTransformations problems for rejected rules
 */
public record StartupReport(
        String polyVersion,
        String gameVersion,
        String launchTarget,
        String adapterId,
        List<Mod> mods,
        List<BlockedFile> blockedFiles,
        List<ForeignMod> foreignMods,
        List<ActiveSystem> systems,
        List<Transformation> acceptedTransformations,
        List<String> rejectedTransformations) {

    /** File name inside the report directory. */
    public static final String FILE_NAME = "startup-report.json";

    private static final Gson GSON =
            new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /**
     * A loaded mod.
     *
     * @param id mod ID
     * @param version mod version
     * @param name display name
     * @param jar jar file name
     */
    public record Mod(String id, String version, String name, String jar) {}

    /**
     * A jar that was discovered but not loaded.
     *
     * @param file jar file name
     * @param kind detected loader kind
     * @param status compatibility status
     * @param code diagnostic code
     */
    public record BlockedFile(String file, String kind, String status, String code) {}

    /**
     * A mod for another loader and the bridge's verdict.
     *
     * @param loader loader name
     * @param modId the mod's ID
     * @param version the mod's version
     * @param file jar file name
     * @param status compatibility status
     * @param findings every finding, one line each
     */
    public record ForeignMod(
            String loader, String modId, String version, String file, String status, List<String> findings) {}

    /**
     * The active provider of a system.
     *
     * @param system system ID
     * @param provider provider class
     * @param providerMod {@code poly} for the default
     * @param overriddenMods mods whose replacement was overridden
     */
    public record ActiveSystem(String system, String provider, String providerMod, List<String> overriddenMods) {}

    /**
     * An accepted transformation rule.
     *
     * @param ruleId rule ID
     * @param owner mod that owns the rule
     * @param target target method
     * @param action human-readable action
     */
    public record Transformation(String ruleId, String owner, String target, String action) {}

    /** Writes the report to {@code directory}/{@value #FILE_NAME} and returns the file. */
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
    public static StartupReport read(Path file) {
        try {
            return GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), StartupReport.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + file, e);
        }
    }
}
