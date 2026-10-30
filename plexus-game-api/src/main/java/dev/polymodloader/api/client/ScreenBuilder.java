package dev.polymodloader.api.client;

import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;

/** Places widgets on a {@link ModScreen}, in GUI-scaled pixels from the top-left corner. */
@ApiStability(StabilityLevel.PREVIEW)
public interface ScreenBuilder {

    /** Screen width. */
    int width();

    /** Screen height. */
    int height();

    /** Width of {@code text} in the game font. */
    int textWidth(String text);

    /** Text drawn every frame. */
    void label(String text, int x, int y, int argb);

    /** A clickable button; {@code onPress} runs on the client thread. */
    void button(String label, int x, int y, int width, int height, Runnable onPress);

    /** A single-line text input, 20 pixels high. */
    TextInput textInput(int x, int y, int width, String initialValue);

    /** Closes this screen. */
    void close();
}
