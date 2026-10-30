package dev.plexusmodloader.compat.bridge;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;
import java.util.Optional;

/**
 * Read access to the classes the game class loader can see, for a {@link BridgeClassTransformer} that must
 * inspect classes before they are defined (Mixin reads target classes, their supertypes and the mixin classes
 * themselves).
 *
 * <p>Thread: safe from any thread.
 */
@ApiStability(StabilityLevel.INTERNAL)
public interface GameClassBytes {

    /**
     * The class file of {@code className} as the game class loader would define it before any bridge transformer
     * runs: for a class in a game or mod jar, after Poly Mod Loader's transformer; for a JDK or library class, as it
     * is on Poly Mod Loader's class path. Reading a class never defines it.
     *
     * @param className binary name ({@code net.minecraft.server.Main}) or internal name
     * @return the class file, or empty when no jar or library has it
     */
    Optional<byte[]> classBytes(String className);

    /** Whether the game class loader has already defined {@code className} (binary name). */
    boolean isDefined(String className);
}
