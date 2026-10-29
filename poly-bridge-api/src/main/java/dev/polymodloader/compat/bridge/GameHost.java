package dev.polymodloader.compat.bridge;

import dev.polymodloader.api.runtime.GameSide;
import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;
import dev.polymodloader.internal.transform.TransformRuleDefinition;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A foreign loader that runs its own loading code and starts the game itself, on Poly Mod Loader's game class loader
 * (MinecraftForge: FML discovers and loads Forge mods, and Forge's launch handler starts Minecraft; ADR-0021). The
 * runtime still owns the game class loader and its transformations; the host decides what that loader holds.
 *
 * <p>Order: the runtime asks the bridge for a host ({@link ModLoaderBridge#gameHost}) after discovery, uses {@link
 * #gameJar()} instead of the vanilla jar for transformation planning, creates the game class loader with {@link
 * #resourceJars()}, calls {@link #prepare} before any game class is defined, installs {@link #classTransformer()}, and
 * finally calls {@link #launch()} instead of the game's main class.
 */
@ApiStability(StabilityLevel.INTERNAL)
public interface GameHost {

    /**
     * What the runtime needs to set up a host.
     *
     * @param launchTarget Poly Mod Loader's launch target ID: {@code client}, {@code dedicated_server} or {@code
     *     game_test_server}
     * @param cacheDirectory where the host may keep files it downloads or builds
     * @param sharedMixin whether another loader's mods run in this launch too, so one Mixin serves every loader: the
     *     host leaves its own Mixin out and reports its mods' configurations in {@link #sharedMixinConfigs()}
     * @param systemClassPath the JVM's class path, when Poly Mod Loader's launcher can append to it (started with its
     *     Java agent, ADR-0024); for jars Java only finds there, such as file systems
     * @param jdkModuleAccess opens JDK packages to the host's modules, when Poly Mod Loader's launcher was started with
     *     its Java agent (ADR-0026)
     * @param polyEarlyWindow whether Poly Mod Loader shows its own early loading window over this launch: the host's
     *     loader then opens none (FML's early window stays closed), so that every loader starts with the same window
     */
    record Request(
            GameSide side,
            String launchTarget,
            String gameVersion,
            Path vanillaGameJar,
            Path gameDirectory,
            Path cacheDirectory,
            List<String> gameArguments,
            Consumer<String> log,
            boolean sharedMixin,
            Optional<SystemClassPath> systemClassPath,
            Optional<JdkModuleAccess> jdkModuleAccess,
            boolean polyEarlyWindow) {
        public Request {
            gameArguments = List.copyOf(gameArguments);
            Objects.requireNonNull(systemClassPath, "systemClassPath");
            Objects.requireNonNull(jdkModuleAccess, "jdkModuleAccess");
        }

        /** A launch without Poly Mod Loader's early loading window: the host's loader opens its own. */
        public Request(
                GameSide side,
                String launchTarget,
                String gameVersion,
                Path vanillaGameJar,
                Path gameDirectory,
                Path cacheDirectory,
                List<String> gameArguments,
                Consumer<String> log,
                boolean sharedMixin,
                Optional<SystemClassPath> systemClassPath,
                Optional<JdkModuleAccess> jdkModuleAccess) {
            this(
                    side,
                    launchTarget,
                    gameVersion,
                    vanillaGameJar,
                    gameDirectory,
                    cacheDirectory,
                    gameArguments,
                    log,
                    sharedMixin,
                    systemClassPath,
                    jdkModuleAccess,
                    false);
        }

        /** A launch whose JDK modules cannot be opened to the host's modules. */
        public Request(
                GameSide side,
                String launchTarget,
                String gameVersion,
                Path vanillaGameJar,
                Path gameDirectory,
                Path cacheDirectory,
                List<String> gameArguments,
                Consumer<String> log,
                boolean sharedMixin,
                Optional<SystemClassPath> systemClassPath) {
            this(
                    side,
                    launchTarget,
                    gameVersion,
                    vanillaGameJar,
                    gameDirectory,
                    cacheDirectory,
                    gameArguments,
                    log,
                    sharedMixin,
                    systemClassPath,
                    Optional.empty());
        }

        /** A launch whose JVM class path cannot be appended to. */
        public Request(
                GameSide side,
                String launchTarget,
                String gameVersion,
                Path vanillaGameJar,
                Path gameDirectory,
                Path cacheDirectory,
                List<String> gameArguments,
                Consumer<String> log,
                boolean sharedMixin) {
            this(
                    side,
                    launchTarget,
                    gameVersion,
                    vanillaGameJar,
                    gameDirectory,
                    cacheDirectory,
                    gameArguments,
                    log,
                    sharedMixin,
                    Optional.empty());
        }

        /** A launch where only this host's loader runs mods besides Poly mods. */
        public Request(
                GameSide side,
                String launchTarget,
                String gameVersion,
                Path vanillaGameJar,
                Path gameDirectory,
                Path cacheDirectory,
                List<String> gameArguments,
                Consumer<String> log) {
            this(
                    side,
                    launchTarget,
                    gameVersion,
                    vanillaGameJar,
                    gameDirectory,
                    cacheDirectory,
                    gameArguments,
                    log,
                    false);
        }
    }

    /** For logs, such as {@code MinecraftForge 26.2-65.1.5}. */
    String name();

    /** The game jar to run instead of the vanilla one, such as Forge's patched jar. */
    Path gameJar();

    /**
     * Jars the game class loader exposes as resources without defining classes from them, because the loader scans
     * them and defines them as modules later (Forge's mod files).
     */
    List<Path> resourceJars();

    /**
     * Jars among those the host was given that it will not run, known before {@link #prepare}: a jar built for several
     * loaders whose host metadata fails the host's requirements (NeoForge's {@code POLY-COMPAT-217}). The runtime lets
     * another bridge run such a jar by its other metadata, as that loader would run it without the host; only when the
     * host shares that bridge's Mixin, so that no second Mixin starts. Empty by default.
     */
    default List<Path> declinedJars() {
        return List.of();
    }

    /**
     * Jars whose classes the host's loader defines as modules only while it starts ({@link #prepare}), and which the game
     * class loader shows neither as resources nor defines from (NeoForge's patched game and universal jar, which FML
     * finds in its own library folder). Until the host defines them, the game class loader reads their class bytes from
     * these jars ({@link GameClassBytes}), so that a bridge preparing before the host (Fabric's Mixin and its planning
     * against the host's patch) sees the host's classes, not the vanilla game on Poly Mod Loader's class path. Empty by
     * default.
     */
    default List<Path> classBytesJars() {
        return List.of();
    }

    /**
     * Jars of the host loader's own classes that it defines as modules of the game class loader (NeoForge's FML and
     * other loader libraries, and its universal jar), known before {@link #prepare}. A module owns its packages: a class
     * another loader's mod carries in one of them is never defined from the mod's jar, the host's class of that name is.
     * The runtime's link check reads them, so that a mod built against another release of these classes (Forge Config
     * API Port's Fabric build carries a copy of FML's config classes) is left out before it runs ({@code
     * POLY-COMPAT-141}). Empty by default.
     */
    default List<Path> loaderJars() {
        return List.of();
    }

    /**
     * Runs the loader's own setup and discovery and defines its module layers, before any game or mod class is defined.
     *
     * @param layers defines module layers in the game class loader
     * @param classes read access to the game class loader's classes
     * @throws Exception whatever the loader's code throws; the runtime stops the launch and reports it
     */
    void prepare(GameModuleLayers layers, GameClassBytes classes) throws Exception;

    /**
     * With {@link Request#sharedMixin()}, the Mixin configurations of the host's mods, for the bridge whose Mixin serves
     * every loader ({@link BridgeLaunchContext#sharedMixinConfigs()}). The runtime reads them before {@link #prepare}
     * and again after it; a host whose loader finds them while it starts ({@link #sharesMixin()}) has them only then.
     * Empty by default and when the host runs its own Mixin.
     */
    default List<SharedMixinConfig> sharedMixinConfigs() {
        return List.of();
    }

    /**
     * Whether the host's mods use the Mixin of the bridge that runs one ({@link Request#sharedMixin()}), even when
     * {@link #sharedMixinConfigs()} is still empty before {@link #prepare}: a host whose loader finds its mods'
     * configurations while it starts (NeoForge's FML reads them from the mods it discovers) reports them after {@link
     * #prepare}, and the runtime hands them to the bridges before {@link ModLoaderBridge#beforeGame}. By default, whether
     * {@link #sharedMixinConfigs()} has any.
     */
    default boolean sharesMixin() {
        return !sharedMixinConfigs().isEmpty();
    }

    /**
     * The IDs of the mods the host loads itself (for Forge, the mod IDs in its mods' {@code mods.toml}, nested jars
     * included), known before {@link #prepare}. A mod of another loader with one of these IDs is the same mod built for
     * that loader; the runtime does not load it a second time. Empty by default.
     */
    default Set<String> modIds() {
        return Set.of();
    }

    /**
     * The mods the host runs itself (for Forge, its mods' {@code mods.toml} entries, nested jars included), known
     * before {@link #prepare}, each with {@link BridgeLaunchContext.LoadedMod#runByHost()}. Other loaders' runtime APIs
     * show them in their mod lists, so their mods' presence queries see them (Fabric Loader's {@code isModLoaded},
     * ADR-0025). Empty by default.
     */
    default List<BridgeLaunchContext.LoadedMod> mods() {
        return List.of();
    }

    /**
     * Tells the host every mod Poly Mod Loader runs besides it in this launch (Poly Mods and other loaders' mods),
     * once, before {@link #prepare}. The host may answer its mods' presence queries for them (Forge's {@code
     * ModList.getModFileById}, ADR-0025), but must not run them, construct them or send them its lifecycle events.
     * Ignored by default.
     *
     * @param mods the mods in entrypoint order; none of them runs by the host
     */
    default void otherMods(List<BridgeLaunchContext.LoadedMod> mods) {}

    /**
     * Runs {@code initialization}, the entrypoints of other loaders' mods at Minecraft's mod initialization point, in
     * the state those mods expect from their own loader. Forge opens its registries for them, as Fabric mods register
     * into the game's registries there. The default runs it as it is.
     *
     * @throws RuntimeException whatever {@code initialization} throws, or the host's own failure to set up that state
     */
    default void runForeignModInitialization(Runnable initialization) {
        initialization.run();
    }

    /**
     * Injection points the host's patch moved (see {@link InjectionPointReplacement}), for the bridge whose Mixin applies
     * other loaders' mixins to the host's patched game. Empty by default.
     */
    default List<InjectionPointReplacement> injectionPointReplacements() {
        return List.of();
    }

    /**
     * Poly Mod Loader's own rules for the host's patched game, where the patch replaced code a built-in rule of the
     * version adapter changes (Forge draws the title screen's branding itself). The runtime plans them with the
     * version adapter's rules against {@link #gameJar()}; their owner is {@code poly}. Known before {@link #prepare}.
     * Empty by default.
     */
    default List<TransformRuleDefinition> transformRules() {
        return List.of();
    }

    /**
     * {@link #transformRules()}, for rules whose hooks are in the version's game layer: {@code gameLayerPackage} is its
     * package as an internal name ({@code dev/polymodloader/game/layer/v26_2}), or empty when the version has none
     * (ADR-0026 40). By default {@link #transformRules()}.
     */
    default List<TransformRuleDefinition> transformRules(String gameLayerPackage) {
        return transformRules();
    }

    /**
     * Jars besides {@link #gameJar()} whose classes {@link #transformRules()} target: the host's own classes that the
     * game class loader defines, such as NeoForge's universal jar. The runtime plans the rules against the game jar
     * first and then these, in order. Known before {@link #prepare}. Empty by default.
     */
    default List<Path> transformTargetJars() {
        return List.of();
    }

    /**
     * Hands the host the lines Poly Mod Loader adds to the game's branding text at the bottom left of the title screen
     * (how many mods each loader loaded, in the game's language, top to bottom), for hooks of {@link
     * #transformRules()} that draw that text where the host draws its own. Called once, after {@link #prepare} and
     * before {@link #launch()}; {@code lines} is called on the render thread. The default ignores them.
     */
    default void addBrandingLines(Supplier<List<String>> lines) {}

    /** The loader's transformation stage for game and mod classes, installed after {@link #prepare}. */
    Optional<BridgeClassTransformer> classTransformer();

    /**
     * Starts the game through the loader's launch path; returns when the game's main method returns.
     *
     * @throws Throwable whatever the game or the loader throws
     */
    void launch() throws Throwable;
}
