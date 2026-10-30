package dev.plexusmodloader.compat.fabric.mixin;

import dev.plexusmodloader.api.runtime.GameSide;
import dev.plexusmodloader.compat.bridge.GameClassBytes;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;

/**
 * What Poly Mod Loader's Mixin service needs from the launch. Mixin creates its service itself, by class name, so the
 * service reads this shared state. Mixin keeps global state for the whole JVM, so it can be started once per
 * process.
 *
 * <p>Thread: set on the launch thread before Mixin starts; read from any thread afterwards.
 */
final class MixinLaunch {

    private static volatile MixinLaunch current;

    final GameClassBytes classes;
    final ClassLoader gameClassLoader;
    final GameSide side;
    final String fabricLoaderVersion;
    /** Mixin's global properties (its "blackboard"), which Fabric Loader also keeps in a plain map. */
    final Map<String, Object> properties = new ConcurrentHashMap<>();

    /** Offered by Mixin while it starts ({@code IMixinService.offer}). */
    volatile IMixinTransformer transformer;

    private MixinLaunch(
            GameClassBytes classes, ClassLoader gameClassLoader, GameSide side, String fabricLoaderVersion) {
        this.classes = classes;
        this.gameClassLoader = gameClassLoader;
        this.side = side;
        this.fabricLoaderVersion = fabricLoaderVersion;
    }

    /**
     * Sets the state for this process's one Mixin start.
     *
     * @throws IllegalStateException when Mixin was already started in this JVM
     */
    static synchronized MixinLaunch begin(
            GameClassBytes classes, ClassLoader gameClassLoader, GameSide side, String fabricLoaderVersion) {
        if (current != null) {
            throw new IllegalStateException(
                    "Mixin was already started in this Java process; it keeps global state and can start only once");
        }
        current = new MixinLaunch(classes, gameClassLoader, side, fabricLoaderVersion);
        return current;
    }

    /**
     * The state of this process's Mixin start.
     *
     * @throws IllegalStateException before {@link #begin}
     */
    static MixinLaunch current() {
        MixinLaunch launch = current;
        if (launch == null) {
            throw new IllegalStateException(
                    "Poly Mod Loader's Mixin service is used before the Fabric bridge started it");
        }
        return launch;
    }
}
