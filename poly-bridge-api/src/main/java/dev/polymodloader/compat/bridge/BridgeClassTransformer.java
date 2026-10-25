package dev.polymodloader.compat.bridge;

import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;

/**
 * A loader's own class transformation stage, such as Fabric's Mixin, run by the game class loader after
 * Poly Mod Loader's transformer on every class it defines (game, game layer and mod classes). Installed once,
 * before game code runs ({@link ModLoaderBridge#classTransformer}).
 *
 * <p>Thread: called from any thread that loads a class, possibly re-entrantly while another class is being
 * transformed; implementations must be thread-safe.
 */
@ApiStability(StabilityLevel.INTERNAL)
public interface BridgeClassTransformer {

    /** Short name for logs and errors, such as {@code Mixin 0.8.7}. */
    String name();

    /**
     * Transforms one class.
     *
     * @param className binary name ({@code net.minecraft.server.Main})
     * @param bytes the class file after Poly Mod Loader's transformer
     * @return the class file to define; {@code bytes} itself when nothing changes
     * @throws RuntimeException when the class cannot be transformed; the game class loader reports it with
     *     the class name and does not define the class
     */
    byte[] transform(String className, byte[] bytes);

    /**
     * A class this stage creates itself, for a name that no jar of the game class loader holds (Mixin's
     * synthetic argument classes).
     *
     * @param className binary name
     * @return the class file, or {@code null} when this stage does not create that class
     */
    default byte[] generate(String className) {
        return null;
    }
}
