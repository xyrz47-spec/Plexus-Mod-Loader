package dev.polymodloader.game.version;

import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;
import dev.polymodloader.api.system.SystemLookup;
import dev.polymodloader.internal.transform.TransformRuleDefinition;
import java.util.List;

/**
 * Everything Poly Mod Loader needs to know about one exact Minecraft version. All knowledge of Minecraft class,
 * method, and descriptor names stays inside adapter implementations so it cannot leak into the
 * general API (design doc §5.5, §7.7 {@code game.version}). Implementations are found with
 * {@link java.util.ServiceLoader}.
 */
@ApiStability(StabilityLevel.INTERNAL)
public interface GameVersionAdapter {

    /** Stable adapter ID for diagnostics, e.g. {@code minecraft-26.2}. */
    String adapterId();

    /**
     * The Minecraft version ID this adapter was written for, as found in the game jar's {@code
     * version.json}. Also the version Poly Mod Loader's build compiles the adapter's game layer against.
     */
    String gameVersion();

    /**
     * Every Minecraft version ID this adapter runs, {@link #gameVersion()} included. Override to add
     * hotfix releases that changed nothing the adapter's rules and game layer touch, for example
     * {@code 26.2.1}; a version with any such change needs its own adapter.
     */
    default java.util.Set<String> gameVersions() {
        return java.util.Set.of(gameVersion());
    }

    /** Fully qualified name of the game class whose {@code main} starts {@code target}. */
    String mainClass(GameLaunchTarget target);

    /** Transformation rules Poly Mod Loader itself applies to this version. Filtered by side during planning. */
    List<TransformRuleDefinition> builtInTransformRules();

    /**
     * Gives the adapter's game hooks access to the active systems. Called once, after systems are
     * created and before any mod entrypoint or game code runs.
     */
    default void bindSystems(SystemLookup systems) {}

    /** Gives the adapter's game hooks a way to fire events. Called once, before any game code runs. */
    default void bindEvents(EventPublisher events) {}

    /**
     * Binds what runs at the point where Fabric Loader initialises mods, inside the game's own start (ADR-0020): after
     * Minecraft's bootstrap on a server (and the game test server), in Minecraft's constructor on a client. The
     * adapter's built-in rules call it once.
     *
     * <p>Called on the launch thread before the game's {@code main}; {@code initialization} then runs on the thread that
     * starts the game.
     *
     * @return {@code true} when this adapter has such a point for {@code target}; otherwise the runtime runs
     *     {@code initialization} itself, before the game's {@code main}
     */
    default boolean bindModInitialization(GameLaunchTarget target, Runnable initialization) {
        return false;
    }

    /**
     * The running game object, for loader APIs that hand it to mods (Fabric Loader's {@code getGameInstance}): the
     * client's {@code Minecraft} object from its constructor on, or the dedicated server once it starts running.
     * Empty before then, and on adapters that do not track it. Called from any thread, after the game started.
     */
    default java.util.Optional<Object> gameInstance() {
        return java.util.Optional.empty();
    }

    /**
     * Fully qualified name of this version's {@link dev.polymodloader.game.version.layer.GameLayer}
     * entry class, or empty when the adapter has no game layer. The class must be in a jar on Poly Mod Loader's
     * class path under {@code dev.polymodloader.game.layer.}; Poly Mod Loader adds that jar to the game class
     * loader so the layer can use game classes directly (ADR-0010).
     */
    default java.util.Optional<String> gameLayerClass() {
        return java.util.Optional.empty();
    }
}
