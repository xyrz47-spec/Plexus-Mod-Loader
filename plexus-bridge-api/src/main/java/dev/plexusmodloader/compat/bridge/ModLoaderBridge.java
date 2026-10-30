package dev.plexusmodloader.compat.bridge;

import dev.plexusmodloader.api.runtime.GameSide;
import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lets Poly Mod Loader recognise and, where possible, run mods built for another loader. Bridges are found
 * with {@link java.util.ServiceLoader}. A bridge must never pretend support: every feature it cannot
 * provide is reported as a {@link CompatibilityFinding} (design doc §13).
 */
@ApiStability(StabilityLevel.INTERNAL)
public interface ModLoaderBridge {

    /** Human-readable loader name, e.g. {@code Fabric}. */
    String loaderName();

    /** Path of the metadata file that identifies this loader's mods inside a jar. */
    String metadataPath();

    /**
     * Metadata files that make a jar nested in one of this loader's mods ({@link BridgeAssessment#nestedJars()}) a mod
     * of its own, as this loader decides it. The runtime asks while it extracts nested jars, before the launch; each
     * nested mod is then assessed by the bridge of the metadata file it has. The default is {@link #metadataPath()}
     * alone; Quilt Loader also reads {@code fabric.mod.json} in nested jars. Must not throw.
     */
    default List<String> nestedMetadataPaths() {
        return List.of(metadataPath());
    }

    /** Analyses a mod from its metadata file contents. Never throws for malformed metadata; reports it. */
    BridgeAssessment assess(String metadata, GameSide side);

    /**
     * Analyses a mod from its metadata file contents and the files that metadata points to, such as a
     * Fabric class tweaker. The runtime calls this one; the default ignores {@code jar}. Never throws for
     * malformed metadata or files; reports them.
     *
     * @param jar the other entries of the same jar, readable during this call only
     */
    default BridgeAssessment assess(String metadata, GameSide side, ModJarContents jar) {
        return assess(metadata, side);
    }

    /**
     * Instantiates and calls one entrypoint of a loadable mod.
     *
     * @throws Exception whatever the mod's entrypoint throws; Poly Mod Loader reports it with the mod ID
     */
    void invokeEntrypoint(BridgedEntrypoint entrypoint, ClassLoader gameClassLoader) throws Exception;

    /**
     * Mod IDs this loader's own environment answers to, with the version Poly Mod Loader stands in for, such as
     * Fabric's {@code fabricloader}. {@code minecraft} and {@code java} are provided by the runtime for every
     * bridge and are not listed here.
     */
    default Map<String, String> builtInMods() {
        return Map.of();
    }

    /**
     * Built-in IDs of the real loader that Poly Mod Loader does not provide, with the reason, such as Fabric's
     * {@code mixinextras}. A mod requiring one is blocked with that reason instead of "not installed".
     */
    default Map<String, String> unavailableBuiltInMods() {
        return Map.of();
    }

    /**
     * Class name prefixes (internal form, {@code net/fabricmc/}) that belong to this loader's runtime. A
     * bridged mod that references a class under one of them which is neither in {@link #providedPackages()}
     * nor in an installed mod is blocked before it runs.
     */
    default List<String> runtimePackages() {
        return List.of();
    }

    /** Prefixes inside {@link #runtimePackages()} that Poly Mod Loader does provide, such as {@code net/fabricmc/api/}. */
    default List<String> providedPackages() {
        return List.of();
    }

    /**
     * Members inside {@link #providedPackages()} that Poly Mod Loader does not implement, as {@code owner.name} in
     * internal form ({@code net/fabricmc/loader/api/FabricLoader.getGameInstance}). A bridged mod that calls
     * or reads one is blocked before it runs, instead of failing when it gets there.
     */
    default List<String> unsupportedMembers() {
        return List.of();
    }

    /**
     * Called once on a game a host loader patched (ADR-0021 G8, ADR-0030), after the bridges assessed the mods and
     * before the runtime decides which of them load: the mods among {@code mods} whose code cannot work as written
     * against the host's patched classes (Fabric's mixins), each with why. The runtime does not load them, nor the mods
     * that need them, and tells the player that Poly Mod Loader, not the mod, has to change ({@code PLEXUS-COMPAT-224}).
     * A bridge never runs such a mod with the part that does not fit left out. The default finds none.
     *
     * @param mods every other loader's mod that was assessed as loadable so far, of any bridge (a bridge whose loader
     *     shares its class transformation with another's, such as Quilt's with Fabric's, leaves them to that one)
     * @param patched the classes of the host's patched game and its libraries, read from their jars
     * @return per mod jar, the reasons, one line each; mods not in it have none
     */
    default Map<java.nio.file.Path, List<String>> hostIncompatibilities(
            GameSide side, HostedLaunch host, List<BridgeLaunchContext.LoadedMod> mods, GameClassBytes patched) {
        return Map.of();
    }

    /**
     * Called once, after Poly Mod Loader has decided which mods load and created the game class loader, and before
     * any entrypoint runs. A bridge that provides its loader's runtime API sets it up here.
     */
    default void prepareLaunch(BridgeLaunchContext context) {}

    /**
     * Called once, right after {@link #prepareLaunch}, still before any game or mod class is defined. A bridge
     * whose loader transforms classes itself (Fabric's Mixin) sets that up here and returns it; the game class
     * loader then runs it on every class it defines, after Poly Mod Loader's own transformer.
     *
     * @param classes read access to the classes the game class loader can see, for the whole launch
     * @return the transformation stage, or empty when this bridge has none for this launch
     * @throws RuntimeException when the stage cannot be set up; Poly Mod Loader stops the launch and reports it
     */
    default Optional<BridgeClassTransformer> classTransformer(BridgeLaunchContext context, GameClassBytes classes) {
        return Optional.empty();
    }

    /**
     * Called once, after every bridge's {@link #classTransformer} is installed and before Poly Mod Loader defines any
     * game or mod class itself (systems, entrypoints, the game). Fabric runs its {@code preLaunch} entrypoints here,
     * as Fabric Loader runs them after Mixin is ready and before the game starts.
     *
     * @throws Exception whatever a mod's code throws; Poly Mod Loader stops the launch and reports it with the loader
     */
    default void beforeGame(BridgeLaunchContext context) throws Exception {}

    /**
     * Package prefixes (binary form, {@code com.llamalad7.mixinextras.}) of libraries this bridge puts on
     * Poly Mod Loader's class path for its mods. The game class loader always takes these from Poly Mod Loader's class
     * path, even when a mod jar carries its own copy, so that there is one copy of their static state.
     */
    default List<String> sharedLibraryPackages() {
        return List.of();
    }

    /**
     * Orders two version texts of this loader's mods, for choosing one of several copies of the same nested
     * mod (the newest is used). The default compares the texts.
     */
    default int compareVersions(String first, String second) {
        return first.compareTo(second);
    }

    /**
     * For a loader whose own code loads its mods and starts the game (MinecraftForge, ADR-0021): the host that does it
     * on Poly Mod Loader's game class loader. The runtime asks only when this loader's mods were found (or the player asked
     * for the loader with {@code -Dpoly.forge=always}), and then does not load those mods through {@link #assess} and
     * {@link #invokeEntrypoint}; the host's loader finds and loads them itself.
     *
     * @param modJars the jars of this loader's mods that discovery found
     * @return the host, or empty when this bridge only diagnoses its loader's mods for this game
     * @throws java.io.IOException when the loader's files cannot be prepared (downloaded, checked, patched); the runtime
     *     then reports it and leaves this loader's mods out
     */
    default Optional<GameHost> gameHost(GameHost.Request request, List<java.nio.file.Path> modJars)
            throws java.io.IOException {
        return Optional.empty();
    }
}
