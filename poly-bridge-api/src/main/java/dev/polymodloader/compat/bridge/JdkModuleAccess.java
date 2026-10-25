package dev.polymodloader.compat.bridge;

import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;
import java.util.Set;

/**
 * Opens a package of a JDK module to modules of a layer Poly Mod Loader defined, as {@code --add-opens} does for the
 * class path: a loader that reflects into the JDK (NeoForge's FML reads {@code MethodHandles.Lookup.IMPL_LOOKUP}) and
 * runs from the class path under its own launcher needs it once Poly Mod Loader defines it as a module (ADR-0026).
 * Poly Mod Loader's launcher offers it when it was started with its Java agent ({@code -javaagent} in the launcher
 * profile, {@code Launcher-Agent-Class} in the server's launch jar); absent otherwise.
 *
 * <p>Thread: the launching thread, before the game starts.
 */
@ApiStability(StabilityLevel.INTERNAL)
@FunctionalInterface
public interface JdkModuleAccess {

    /**
     * Opens {@code packageName} of {@code jdkModule} to every module in {@code targets}.
     *
     * @throws IllegalArgumentException when {@code jdkModule} does not have that package
     * @throws UnsupportedOperationException when the JVM does not allow changing that module
     */
    void open(Module jdkModule, String packageName, Set<Module> targets);
}
