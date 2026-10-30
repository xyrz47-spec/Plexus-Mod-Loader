package dev.plexusmodloader.compat.bridge;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;
import java.io.IOException;
import java.nio.file.Path;

/**
 * The JVM's own class path (the system class loader), for the few jars Java only looks up there: file systems ({@code
 * FileSystemProvider}) and URL handlers ({@code URLStreamHandlerProvider}), such as Forge's {@code securemodules}
 * (ADR-0024). Poly Mod Loader's launcher offers it when it was started with its Java agent ({@code -javaagent} in the
 * launcher profile, {@code Launcher-Agent-Class} in the server's launch jar); absent otherwise.
 *
 * <p>Thread: the launching thread, before the game starts. The JVM lists its file systems once, the first time anything
 * asks for them; a jar appended after that adds classes but no file system.
 */
@ApiStability(StabilityLevel.INTERNAL)
@FunctionalInterface
public interface SystemClassPath {

    /**
     * Appends {@code jar} to the system class loader's search path. A class the class path already has still comes
     * from there.
     *
     * @throws IOException when {@code jar} cannot be opened as a jar, or the system class loader cannot take more jars
     */
    void append(Path jar) throws IOException;
}
