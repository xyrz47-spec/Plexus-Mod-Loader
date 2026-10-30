package dev.polymodloader.compat.bridge;

import dev.polymodloader.api.runtime.GameSide;
import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

/**
 * What a bridge learns about the launch once Poly Mod Loader has decided which mods load and before any entrypoint
 * runs ({@link ModLoaderBridge#prepareLaunch}). A bridge that emulates its loader's runtime API (such as Fabric
 * Loader's {@code FabricLoader}) builds it from this.
 *
 * @param side which game this is
 * @param gameVersion the Minecraft version
 * @param gameJar the Minecraft jar
 * @param gameDirectory the game directory (worlds, {@code config/}, {@code mods/})
 * @param launchArguments the arguments the game is started with
 * @param gameClassLoader the class loader of game and mod classes
 * @param mods every loaded mod of every loader: first the mods a host loader runs ({@link LoadedMod#runByHost()},
 *     ADR-0025), then those Poly Mod Loader runs, in entrypoint order
 * @param gameInstance the running game object, or {@code null} before there is one: the client's {@code Minecraft}
 *     object from its constructor on, the dedicated server once it starts running (what Fabric Loader's {@code
 *     getGameInstance} returns); safe to call from any thread
 * @param host the loader that patched the game and starts it, when mods of other loaders run with it ({@link
 *     HostedLaunch#NONE} otherwise): its mods' Mixin configurations ({@link #sharedMixinConfigs()}) and the injection
 *     points its patch moved
 */
@ApiStability(StabilityLevel.INTERNAL)
public record BridgeLaunchContext(
        GameSide side,
        String gameVersion,
        Path gameJar,
        Path gameDirectory,
        List<String> launchArguments,
        ClassLoader gameClassLoader,
        List<LoadedMod> mods,
        Supplier<Object> gameInstance,
        HostedLaunch host) {

    public BridgeLaunchContext {
        launchArguments = List.copyOf(launchArguments);
        mods = List.copyOf(mods);
        java.util.Objects.requireNonNull(gameInstance, "gameInstance");
        java.util.Objects.requireNonNull(host, "host");
    }

    /**
     * Mixin configurations of mods the host runs, for the bridge whose Mixin serves every loader in this launch ({@link
     * SharedMixinConfig}); empty when each loader runs its own Mixin or none. The bridge that runs Mixin adds them once
     * the host's mods are defined (their classes and configuration plugins live there), before {@link
     * ModLoaderBridge#beforeGame}.
     */
    public List<SharedMixinConfig> sharedMixinConfigs() {
        return host.sharedMixinConfigs();
    }

    /** A launch without a host loader ({@link HostedLaunch#NONE}). */
    public BridgeLaunchContext(
            GameSide side,
            String gameVersion,
            Path gameJar,
            Path gameDirectory,
            List<String> launchArguments,
            ClassLoader gameClassLoader,
            List<LoadedMod> mods,
            Supplier<Object> gameInstance) {
        this(
                side,
                gameVersion,
                gameJar,
                gameDirectory,
                launchArguments,
                gameClassLoader,
                mods,
                gameInstance,
                HostedLaunch.NONE);
    }

    /** A launch whose game object is not known (tests, tools): {@link #gameInstance} always gives {@code null}. */
    public BridgeLaunchContext(
            GameSide side,
            String gameVersion,
            Path gameJar,
            Path gameDirectory,
            List<String> launchArguments,
            ClassLoader gameClassLoader,
            List<LoadedMod> mods) {
        this(side, gameVersion, gameJar, gameDirectory, launchArguments, gameClassLoader, mods, () -> null);
    }

    /**
     * One loaded mod.
     *
     * @param loader {@code Poly Mod Loader} for a Poly Mod, otherwise the bridge's loader name
     * @param modId its ID
     * @param version its version text
     * @param name its display name
     * @param providedIds further IDs it answers to
     * @param jar its jar (for a nested mod, the extracted copy; for a nested mod the host runs, the jar it is nested in)
     * @param containerJar the jar it was nested in, {@code null} if installed directly
     * @param nestedEntry its path inside {@code containerJar}, {@code null} if installed directly
     * @param runByHost whether the host loader runs it ({@link GameHost#mods()}, ADR-0025): its code, resources and
     *     lifecycle are the host's, and other loaders show it in their mod lists for presence queries only
     */
    public record LoadedMod(
            String loader,
            String modId,
            String version,
            String name,
            List<String> providedIds,
            Path jar,
            Path containerJar,
            String nestedEntry,
            boolean runByHost) {

        public LoadedMod {
            providedIds = List.copyOf(providedIds);
        }

        /** A mod Poly Mod Loader runs (a Poly Mod or one of a bridge's mods), not the host. */
        public LoadedMod(
                String loader,
                String modId,
                String version,
                String name,
                List<String> providedIds,
                Path jar,
                Path containerJar,
                String nestedEntry) {
            this(loader, modId, version, name, providedIds, jar, containerJar, nestedEntry, false);
        }
    }
}
