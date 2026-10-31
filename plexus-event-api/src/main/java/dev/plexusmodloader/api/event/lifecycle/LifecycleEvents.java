package dev.plexusmodloader.api.event.lifecycle;

import dev.plexusmodloader.api.event.EventKey;
import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/**
 * Mod lifecycle events: points in the game's start that are not tied to a server or a world.
 *
 * <p>Plexus Mods' entrypoints run before Minecraft starts. Mods of other loaders (Fabric) initialise where their own
 * loader runs them, inside Minecraft's start (ADR-0020). {@link #MODS_INITIALIZED} is the point after both, so a Plexus
 * Mod that uses another loader's mod can wait for it there.
 */
@ApiStability(StabilityLevel.PREVIEW)
public final class LifecycleEvents {

    /**
     * Every mod has been initialised: the Plexus Mods' entrypoints and the other loaders' mods' own initialisers
     * (Fabric: {@code main} and the side's entrypoint). Fired once per game start, on the game's main thread, at
     * Minecraft's mod initialisation point: after Minecraft's bootstrap (registries exist and are not frozen yet),
     * before the client window or the server starts. The point is the same whether or not mods of other loaders are
     * installed.
     *
     * <p>Register the listener in an entrypoint; a listener registered after the event fired is never called.
     */
    public static final EventKey<ModsInitializedListener> MODS_INITIALIZED =
            EventKey.of("plexus:lifecycle/mods_initialized", ModsInitializedListener.class);

    private LifecycleEvents() {}
}
