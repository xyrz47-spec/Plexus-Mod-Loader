package dev.polymodloader.api.client;

import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;

/** A text input placed by {@link ScreenBuilder#textInput}. */
@ApiStability(StabilityLevel.PREVIEW)
public interface TextInput {

    /** The current text. */
    String value();

    /** Replaces the text. */
    void setValue(String value);
}
