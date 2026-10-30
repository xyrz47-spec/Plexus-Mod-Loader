package dev.plexusmodloader.compat.forge;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;
import dev.plexusmodloader.compat.bridge.BridgeClassTransformer;
import dev.plexusmodloader.compat.bridge.GameClassBytes;
import dev.plexusmodloader.compat.bridge.GameModuleLayers;
import dev.plexusmodloader.compat.bridge.SharedMixinConfig;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * FML 11 started by Poly Mod Loader (ADR-0026), ready to start the game. FML's own {@code FMLLoader.create} runs inside
 * the layer of NeoForge's loader libraries, with Poly Mod Loader's host ({@code PlexusFml}, built from the {@code
 * neoForgeHost} source set) in place of the two calls {@link NeoForgeLoaderPatch} replaces; this side and that one meet
 * only through this interface, JDK types and the bridge API, which both see from Poly Mod Loader's class path.
 */
@ApiStability(StabilityLevel.INTERNAL)
public interface NeoForgeLaunch {

    /**
     * What {@code PlexusFml.start} needs.
     *
     * @param dist FML's {@code Dist} name: {@code CLIENT} or {@code DEDICATED_SERVER}
     * @param headless whether FML runs without its early window (servers)
     * @param programArguments the game's arguments and NeoForge's {@code --fml.*} arguments, which FML reads and
     *     removes
     * @param libraryDirectory the folder FML's production game locator finds the patched game and NeoForge's {@code
     *     universal.jar} in
     * @param claimedFiles files FML must not pick up from the class path (the vanilla game jar)
     * @param modJars the NeoForge mods Poly Mod Loader found and FML will load, in place of FML's own scan of the mods
     *     folder
     * @param libraryLayer the layer with NeoForge's loader libraries, the host among them; the game layer's parent
     * @param layers defines the game layer in the game class loader
     * @param classes the game class loader's classes, for FML's class processors that read other classes
     * @param gameClassLoader the game class loader, to which FML's class loader hands every class and resource
     * @param openJdk opens the JDK packages NeoForge's launcher profile opens ({@code java.lang.invoke}) to modules;
     *     for the game layer's modules, once defined
     * @param otherMods the mods Poly Mod Loader runs beside NeoForge, which FML's {@code ModList} answers for when Poly
     *     Mod Loader's patch of it applied (ADR-0025)
     * @param sharedMixin whether Fabric's Mixin serves NeoForge's mods too ({@code NeoForgeSharedMixinPatch}): FML does
     *     not start its own, and the game layer leaves out the MixinExtras that Poly Mod Loader's class path has
     */
    record Request(
            String dist,
            boolean headless,
            Path gameDirectory,
            List<String> programArguments,
            Path libraryDirectory,
            Set<Path> claimedFiles,
            List<Path> modJars,
            ModuleLayer libraryLayer,
            GameModuleLayers layers,
            GameClassBytes classes,
            ClassLoader gameClassLoader,
            Consumer<Set<Module>> openJdk,
            ForeignModIndex otherMods,
            boolean sharedMixin,
            Consumer<String> log) {
        public Request {
            programArguments = List.copyOf(programArguments);
            claimedFiles = Set.copyOf(claimedFiles);
            modJars = List.copyOf(modJars);
            Objects.requireNonNull(libraryLayer, "libraryLayer");
            Objects.requireNonNull(gameClassLoader, "gameClassLoader");
            Objects.requireNonNull(openJdk, "openJdk");
            Objects.requireNonNull(otherMods, "otherMods");
        }
    }

    /**
     * The system property that makes {@link #launch} load every class the NeoForge mods' Mixin configurations target
     * instead of starting the game, and report each that fails ({@code -Dpoly.neoforge.mixinAudit=true}). A diagnosis
     * aid: FML then starts without its early window, so the client's Mixins can be checked without opening one.
     */
    String MIXIN_AUDIT = "poly.neoforge.mixinAudit";

    /** Whether {@link #MIXIN_AUDIT} is set. */
    static boolean mixinAudit() {
        return Boolean.getBoolean(MIXIN_AUDIT);
    }

    /**
     * The system property that starts FML on the client without its early window, as on a server ({@code
     * -Dpoly.neoforge.headless=true}). A diagnosis aid for checks that stop the client before {@code Minecraft.<init>}
     * creates the game window (the test harness {@code fabric-client-bootstrap-probe}, ADR-0026 38); a client that goes
     * on opens its window anyway.
     */
    String HEADLESS = "poly.neoforge.headless";

    /** Whether FML starts without its early window on the client: {@link #HEADLESS} or {@link #MIXIN_AUDIT} is set. */
    static boolean headlessClient() {
        return Boolean.getBoolean(HEADLESS) || mixinAudit();
    }

    /** The game layer FML computed and the game class loader defined: Minecraft, NeoForge and its mods. */
    ModuleLayer gameLayer();

    /** FML's class processors (access transformers, enum extension, Mixin, services) for game and mod classes. */
    BridgeClassTransformer classTransformer();

    /**
     * With {@link Request#sharedMixin()}, the Mixin configurations FML found for its mods, as FML's own {@code
     * addMixins} would have added them (required mods present, a behaviour version FML accepts, the configuration in
     * the jar), and those named in the manifests of FML's plugin and game library jars; empty otherwise.
     */
    List<SharedMixinConfig> sharedMixinConfigs();

    /**
     * Runs {@code mainClass}'s {@code main} with the arguments FML left, from FML's class loader, as FML's own entry
     * points do; returns when it returns.
     *
     * @throws Throwable whatever the game throws
     */
    void launch(String mainClass) throws Throwable;
}
