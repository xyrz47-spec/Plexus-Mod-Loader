package dev.polymodloader.devtools.gradle.minecraft;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.logging.Logger;
import org.gradle.api.provider.Provider;

/**
 * Minecraft files a developer copied by hand into {@code MinecraftFiles/<version>/}, used to seed the
 * download cache instead of downloading. The layout is that of a launcher version folder
 * ({@code .minecraft/versions/<version>/}): {@code <version>.json} and {@code <version>.jar} (or
 * {@code client.jar}), plus an optional {@code server.jar} (Mojang's server bundler) and an optional
 * {@code libraries/} folder in Maven layout. Every jar is checked against the SHA-1 in the version
 * JSON and copied into the cache, so the rest of the pipeline only ever sees cache paths. A file
 * that is absent falls back to downloading; a file whose hash differs fails the build.
 */
final class LocalMinecraftFiles {

    /** Gradle property naming the local files directory; overrides {@value #DEFAULT_DIRECTORY}. */
    static final String PROPERTY = "polyMinecraftFiles";

    /** Directory under the root project used when {@value #PROPERTY} is not set and it exists. */
    static final String DEFAULT_DIRECTORY = "MinecraftFiles";

    private static final LocalMinecraftFiles NONE = new LocalMinecraftFiles(null, null);

    private final Path root;
    private final Logger logger;

    private LocalMinecraftFiles(Path root, Logger logger) {
        this.root = root;
        this.logger = logger;
    }

    /** @param root the local files directory, or {@code null} when there is none */
    static LocalMinecraftFiles at(Path root, Logger logger) {
        return root == null ? NONE : new LocalMinecraftFiles(root, logger);
    }

    /**
     * The local files directory of {@code project}: the {@value #PROPERTY} Gradle property (relative
     * paths resolve against the root project), else {@code <root project>/}{@value #DEFAULT_DIRECTORY}
     * when that directory exists, else absent.
     */
    static Provider<Directory> directory(Project project) {
        File rootDir = project.getRootDir();
        Provider<File> configured = project.getProviders()
                .gradleProperty(PROPERTY)
                .map(path -> rootDir.toPath().resolve(path).toFile());
        Provider<File> fallback = project.provider(() -> {
            File candidate = new File(rootDir, DEFAULT_DIRECTORY);
            return candidate.isDirectory() ? candidate : null;
        });
        return project.getLayout().dir(configured.orElse(fallback));
    }

    /** {@code <root>/<version>/}, or empty when there is no local directory for the version. */
    Optional<Path> versionDirectory(String gameVersion) {
        if (root == null) {
            return Optional.empty();
        }
        Path directory = root.resolve(gameVersion);
        return Files.isDirectory(directory) ? Optional.of(directory) : Optional.empty();
    }

    /** The launcher's {@code <version>.json}, if present. */
    Optional<Path> versionJson(String gameVersion) {
        return versionDirectory(gameVersion)
                .map(directory -> directory.resolve(gameVersion + ".json"))
                .filter(Files::isRegularFile);
    }

    /** One line saying which local files exist for {@code gameVersion}, for task output. */
    String describe(String gameVersion) {
        Optional<Path> directory = versionDirectory(gameVersion);
        if (directory.isEmpty()) {
            return "No local Minecraft files for " + gameVersion + " (" + DEFAULT_DIRECTORY + "/" + gameVersion
                    + "/ or -P" + PROPERTY + "); the build downloads them from Mojang.";
        }
        Path dir = directory.get();
        boolean client = Files.isRegularFile(dir.resolve(gameVersion + ".jar")) || Files.isRegularFile(dir.resolve("client.jar"));
        return "Local Minecraft files in " + dir + ": " + gameVersion + ".json "
                + (versionJson(gameVersion).isPresent() ? "found" : "missing") + ", client jar "
                + (client ? "found" : "missing") + ", server.jar "
                + (Files.isRegularFile(dir.resolve("server.jar")) ? "found" : "missing") + ", libraries/ "
                + (Files.isDirectory(dir.resolve("libraries")) ? "found" : "missing")
                + ". Missing files are downloaded from Mojang.";
    }

    /**
     * Copies the local client jar ({@code <version>.jar} or {@code client.jar}) to {@code target}.
     *
     * @return whether a local jar was found and copied (or already in place)
     * @throws IllegalStateException if a local jar exists but its SHA-1 is not {@code expectedSha1}
     */
    boolean seedClientJar(String gameVersion, String expectedSha1, Path target) {
        return seed(gameVersion, List.of(gameVersion + ".jar", "client.jar"), expectedSha1, target, "downloads.client.sha1");
    }

    /**
     * Like {@link #seedClientJar}, for Mojang's client mappings of an obfuscated version ({@code client.txt}, as
     * the launcher and {@code downloads.client_mappings.url} name it, ADR-0019).
     */
    boolean seedClientMappings(String gameVersion, String expectedSha1, Path target) {
        return seed(
                gameVersion,
                List.of("client.txt", "client_mappings.txt"),
                expectedSha1,
                target,
                "downloads.client_mappings.sha1");
    }

    /** Like {@link #seedClientMappings}, for the server mappings ({@code server.txt}, ADR-0019). */
    boolean seedServerMappings(String gameVersion, String expectedSha1, Path target) {
        return seed(
                gameVersion,
                List.of("server.txt", "server_mappings.txt"),
                expectedSha1,
                target,
                "downloads.server_mappings.sha1");
    }

    /** Like {@link #seedClientJar}, for the server bundler {@code server.jar}. */
    boolean seedServerBundler(String gameVersion, String expectedSha1, Path target) {
        return seed(gameVersion, List.of("server.jar"), expectedSha1, target, "downloads.server.sha1");
    }

    /** Like {@link #seedClientJar}, for {@code libraries/<maven path>}. */
    boolean seedLibrary(String gameVersion, String mavenPath, String expectedSha1, Path target) {
        return seed(gameVersion, List.of("libraries/" + mavenPath), expectedSha1, target, "the library's sha1");
    }

    private boolean seed(
            String gameVersion, List<String> candidates, String expectedSha1, Path target, String hashSource) {
        Optional<Path> directory = versionDirectory(gameVersion);
        if (directory.isEmpty()) {
            return false;
        }
        Path source = null;
        for (String candidate : candidates) {
            Path path = directory.get().resolve(candidate);
            if (Files.isRegularFile(path)) {
                source = path;
                break;
            }
        }
        if (source == null) {
            return false;
        }
        String actual = VerifiedDownloader.hash(source, "SHA-1");
        if (!expectedSha1.equalsIgnoreCase(actual)) {
            throw new IllegalStateException("Local Minecraft file " + source + " does not match its version JSON: "
                    + hashSource + " is " + expectedSha1 + " but the file's SHA-1 is " + actual + ". The file and "
                    + gameVersion + ".json come from different versions or the file is damaged: copy"
                    + " .minecraft/versions/" + gameVersion + "/ again (re-download server.jar for the server), or"
                    + " delete the file to download it from Mojang instead.");
        }
        if (Files.isRegularFile(target) && expectedSha1.equalsIgnoreCase(VerifiedDownloader.hash(target, "SHA-1"))) {
            return true;
        }
        copy(source, target);
        logger.lifecycle("Using local Minecraft file {} (SHA-1 verified)", source);
        return true;
    }

    /** Copies {@code source} over {@code target} through a temporary file in the target directory. */
    static void copy(Path source, Path target) {
        try {
            Files.createDirectories(target.getParent());
            Path temporary = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".part");
            try {
                Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to copy " + source + " to " + target, e);
        }
    }
}
