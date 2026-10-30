package dev.plexusmodloader.devtools.gradle.minecraft;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.gradle.api.logging.Logger;

/**
 * A game jar of an obfuscated version (before 26.1) remapped to Mojang names ({@link MojangJarRemapper},
 * ADR-0019), kept in the shared cache next to the jar it comes from. It is made again only when the jar, the
 * mappings or {@link MojangJarRemapper#REVISION} changed: a {@code .key} file next to it records the three.
 *
 * <p>The installer uses it as the game jar that version modules compile against and that runs start; {@code
 * remapMinecraft} uses it for the self-check. The jar stays on this machine (ADR-0019 point 3).
 *
 * @param jar the remapped jar
 * @param result what the remap did, or null if the cached jar was reused
 * @param remapMillis how long the remap took, or -1 if the cached jar was reused
 */
record RemappedGameJar(Path jar, MojangJarRemapper.Result result, long remapMillis) {

    /**
     * Remaps {@code obfuscatedJar} into {@code output} unless the cached one has the same inputs. {@code
     * libraries} are the jar's libraries, for computing stack map frames ({@link MojangJarRemapper}).
     */
    static RemappedGameJar ensure(
            Path obfuscatedJar, Path mappingsFile, List<Path> libraries, Path output, Logger logger) {
        String key = key(obfuscatedJar, mappingsFile);
        Path keyFile = keyFile(output);
        if (Files.isRegularFile(output) && key.equals(read(keyFile))) {
            return new RemappedGameJar(output, null, -1);
        }
        MojangMappings mappings = MojangMappings.read(mappingsFile);
        logger.lifecycle(
                "Remapping {} to Mojang names ({} classes in the mappings, ADR-0019); the result stays in the local"
                        + " cache.",
                obfuscatedJar.getFileName(),
                mappings.classCount());
        long start = System.nanoTime();
        MojangJarRemapper.Result result = MojangJarRemapper.remap(obfuscatedJar, mappings, libraries, output);
        long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        write(keyFile, key);
        return new RemappedGameJar(output, result, millis);
    }

    /** The cache key: the SHA-1 of the jar and of the mappings, and the remapper revision. */
    static String key(Path obfuscatedJar, Path mappingsFile) {
        return "jar=" + VerifiedDownloader.hash(obfuscatedJar, "SHA-1") + "\nmappings="
                + VerifiedDownloader.hash(mappingsFile, "SHA-1") + "\nremapper=" + MojangJarRemapper.REVISION + "\n";
    }

    private static Path keyFile(Path jar) {
        String name = jar.getFileName().toString();
        return jar.resolveSibling(name.substring(0, name.length() - ".jar".length()) + ".key");
    }

    private static String read(Path file) {
        try {
            return Files.isRegularFile(file) ? Files.readString(file) : null;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + file, e);
        }
    }

    private static void write(Path file, String text) {
        try {
            Files.writeString(file, text);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write " + file, e);
        }
    }
}
