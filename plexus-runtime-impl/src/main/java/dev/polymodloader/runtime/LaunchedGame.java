package dev.polymodloader.runtime;

import dev.polymodloader.runtime.lifecycle.ShutdownCoordinator;
import dev.polymodloader.runtime.system.SystemRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLClassLoader;

/**
 * A game started by {@link PolyRuntime#launch}. Closing it releases the game and mod jars held open
 * by the game class loader; the orderly shutdown phase (design doc §11 step 13) will build on this.
 *
 * @param gameVersion Minecraft version that was started
 * @param gameClassLoader loader that defined the game and mod classes
 * @param shutdown mod shutdown tasks; they also run from a JVM shutdown hook
 * @param systems the active systems
 */
public record LaunchedGame(
        String gameVersion, URLClassLoader gameClassLoader, ShutdownCoordinator shutdown, SystemRegistry systems)
        implements AutoCloseable {

    @Override
    public void close() {
        try {
            gameClassLoader.close();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to close the game class loader", e);
        }
    }
}
