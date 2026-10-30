package dev.polymodloader.api.client;

import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;

/**
 * A full-screen menu made by a mod: labels, buttons, and text inputs placed in {@link #init}, plus
 * free drawing in {@link #render}. Open it with {@link ClientView#openScreen(ModScreen)}. All methods
 * run on the client (render) thread.
 */
@ApiStability(StabilityLevel.PREVIEW)
public interface ModScreen {

    /** Title used for narration and the window title. */
    String title();

    /**
     * Places widgets. Called when the screen opens and again when the window is resized, with a fresh
     * builder each time.
     */
    void init(ScreenBuilder screen);

    /** Draws extra content every frame, above the background and below the widgets. */
    default void render(HudCanvas canvas) {}

    /** Whether the game pauses while the screen is open (single player only). */
    default boolean pausesGame() {
        return false;
    }

    /** The screen was closed, by the player (Esc) or by code. */
    default void onClosed() {}
}
