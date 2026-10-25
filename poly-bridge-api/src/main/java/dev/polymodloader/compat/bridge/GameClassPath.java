package dev.polymodloader.compat.bridge;

import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * The jars the game class loader defines classes from, for a loader whose mods add jars while the game starts (Fabric
 * Loader's {@code FabricLauncher.addToClassPath}). The game class loader ({@link BridgeLaunchContext#gameClassLoader})
 * implements this.
 *
 * <p>Thread: any; additions are safe while classes load.
 */
@ApiStability(StabilityLevel.INTERNAL)
public interface GameClassPath {

    /**
     * Adds {@code jar}. Its classes are then defined, and transformed, by the game class loader like a mod jar's
     * classes, and its resources are found; jars already there win for a class they also have. Adding a jar twice
     * does nothing.
     *
     * @throws IOException when {@code jar} cannot be opened as a jar
     */
    void addJar(Path jar) throws IOException;

    /** The jars classes are defined from, in lookup order: the game jar, the mod jars, then the jars added since. */
    List<Path> jars();
}
