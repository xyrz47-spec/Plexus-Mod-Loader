package dev.polymodloader.api.command;

import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;

/**
 * Registers server commands. Poly Mod Loader adds them every time the server builds its command tree (server
 * start and {@code /reload}), on every server kind, including single player.
 *
 * <p><b>When:</b> inside common entrypoints. Registering a name that another mod or the game already
 * uses merges the trees as the game does; Poly Mod Loader logs {@code POLY-COMMAND-001} when two mods register the
 * same top-level name.
 */
@ApiStability(StabilityLevel.PREVIEW)
public interface CommandRegistrar {

    /**
     * Registers a top-level command. Do not change {@code command} or its children afterwards.
     *
     * @throws IllegalStateException after registration closed ({@code POLY-COMMAND-002})
     */
    void register(LiteralCommand command);
}
