package net.fabricmc.loader.impl.launch;

import java.util.Map;

/**
 * Poly Mod Loader's stand-in for Fabric Loader's internal {@code FabricLauncherBase}: the static access to the launcher
 * ({@link #getLauncher()}) that mods use, with Fabric Loader 0.19.5's signatures. The Poly Mod Loader Fabric bridge sets the
 * launcher before any Fabric code runs.
 */
public abstract class FabricLauncherBase implements FabricLauncher {

    private static volatile FabricLauncher launcher;

    protected FabricLauncherBase() {}

    /** The class {@code name} from the game class loader, not initialised. */
    public static Class<?> getClass(String name) throws ClassNotFoundException {
        return Class.forName(name, false, getLauncher().getTargetClassLoader());
    }

    /** Fabric Loader's development flag, the system property {@code fabric.development}. */
    @Override
    public final boolean isDevelopment() {
        return Boolean.getBoolean("fabric.development");
    }

    public static void setLauncher(FabricLauncher value) {
        launcher = value;
    }

    public static FabricLauncher getLauncher() {
        FabricLauncher current = launcher;
        if (current == null) {
            throw new IllegalStateException(
                    "The Fabric launcher is not ready yet (Poly Mod Loader sets it before Fabric mods run)");
        }
        return current;
    }

    /** Fabric Loader's launch properties; Poly Mod Loader has none. */
    public static Map<String, Object> getProperties() {
        return Map.of();
    }

    /** Whether Mixin is ready: Poly Mod Loader starts it before any Fabric code runs, as Fabric Loader does. */
    public static boolean isMixinReady() {
        return launcher != null;
    }
}
