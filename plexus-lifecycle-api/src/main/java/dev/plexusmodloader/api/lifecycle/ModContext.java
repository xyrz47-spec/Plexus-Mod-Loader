package dev.plexusmodloader.api.lifecycle;

import dev.plexusmodloader.api.client.ClientRegistrar;
import dev.plexusmodloader.api.command.CommandRegistrar;
import dev.plexusmodloader.api.content.ContentRegistrar;
import dev.plexusmodloader.api.devtools.gametest.GameTestRegistrar;
import dev.plexusmodloader.api.event.EventRegistry;
import dev.plexusmodloader.api.network.NetworkRegistrar;
import dev.plexusmodloader.api.runtime.GameSide;
import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;
import dev.plexusmodloader.api.system.SystemLookup;
import dev.plexusmodloader.api.worldgen.WorldgenRegistrar;

/**
 * Information Plexus Mod Loader hands to a mod entrypoint about the mod being initialised and the process it runs
 * in. Implementations are provided by Plexus Mod Loader; mods do not implement this interface.
 */
@ApiStability(StabilityLevel.PREVIEW)
public interface ModContext {
    /** The mod ID declared in {@code plexus.mod.json}, for example {@code minimal_native_mod}. */
    String modId();

    /** The mod version declared in {@code plexus.mod.json}. */
    String modVersion();

    /** Whether this process is a client or a dedicated server. */
    GameSide side();

    /** The mods loaded in this game, of every loader (Plexus Mods, Fabric mods, Forge mods). */
    LoadedMods mods();

    /** The active implementation of each Plexus Mod Loader system, after replacements were applied. */
    SystemLookup systems();

    /** Registers event listeners owned by this mod. */
    EventRegistry events();

    /**
     * Registers work to run when the game process exits.
     *
     * <p><b>Order:</b> tasks run once, in reverse registration order across all mods, so mods are
     * cleaned up before the mods they depend on.
     *
     * <p><b>Thread:</b> tasks run on Plexus Mod Loader's shutdown thread while the JVM exits. They must be quick,
     * must not start new threads, and must not touch game state, which may already be gone.
     *
     * <p><b>Failure:</b> an exception is logged with the mod ID ({@code PLEXUS-SHUTDOWN-001}); the
     * remaining tasks still run.
     *
     * @param description what the task cleans up, shown in diagnostics
     * @param task the work
     * @throws IllegalStateException when called after shutdown has started
     */
    void onShutdown(String description, Runnable task);

    /**
     * This mod's configuration directory, {@code config/<mod id>} in the game directory; created on
     * first call. Configuration file formats are up to the mod.
     */
    java.nio.file.Path configDirectory();

    /** Declares this mod's blocks and items (common entrypoints only). */
    ContentRegistrar content();

    /** Registers this mod's server commands. */
    CommandRegistrar commands();

    /** Declares this mod's network channels. */
    NetworkRegistrar network();

    /** Attaches this mod's placed features to biomes. */
    WorldgenRegistrar worldgen();

    /** Registers this mod's in-game tests. */
    GameTestRegistrar gameTests();

    /**
     * Client-only registrations: key bindings and HUD layers.
     *
     * @throws IllegalStateException on a dedicated server ({@code PLEXUS-CLIENT-001})
     */
    ClientRegistrar client();

    /** Reload listeners for the mod's own data and asset files. */
    dev.plexusmodloader.api.resource.ResourceRegistrar resources();

    /** The mod's permissions and permission providers. */
    dev.plexusmodloader.api.permission.PermissionRegistrar permissions();
}
