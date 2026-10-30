package dev.polymodloader.game.version.layer;

import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;

/**
 * What the game layer tells the runtime about the game's own window, so that Poly Mod Loader's early loading window
 * closes when the game's window takes over. Thread: the game's render thread; implementations return quickly.
 */
@ApiStability(StabilityLevel.INTERNAL)
public interface GameWindowListener {

    /** Tells nothing; for launches without an early window. */
    GameWindowListener NONE = new GameWindowListener() {};

    /** The game is about to open its window (it names it). Called once or more. */
    default void opening() {}

    /** The game's window shows its loading screen (its first frame of it). Called once or more. */
    default void shown() {}
}
