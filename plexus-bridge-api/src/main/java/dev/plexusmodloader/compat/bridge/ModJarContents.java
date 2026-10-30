package dev.plexusmodloader.compat.bridge;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;
import java.util.Optional;

/**
 * Read access to the files of the mod jar a bridge is assessing, for files its metadata points to (such as
 * a Fabric class tweaker). Valid only during {@link ModLoaderBridge#assess(String, dev.plexusmodloader.api.runtime.GameSide,
 * ModJarContents)}; called on the thread that assesses the mod.
 */
@ApiStability(StabilityLevel.INTERNAL)
@FunctionalInterface
public interface ModJarContents {

    /** A jar with no files besides its metadata, for assessing metadata on its own. */
    ModJarContents NONE = path -> Optional.empty();

    /**
     * The bytes of one entry.
     *
     * @param path the entry name inside the jar, with {@code /} separators and no leading {@code /}
     * @return the entry's bytes, or empty when the jar has no such entry
     * @throws java.io.UncheckedIOException when the entry exists but cannot be read
     */
    Optional<byte[]> read(String path);
}
