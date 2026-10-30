package dev.plexusmodloader.api.client;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/**
 * The lines of one item tooltip, first line the item's name. Lines are plain text; section-sign
 * formatting codes (for example {@code "§7"} for grey) are drawn as colours. Lines the game or other
 * mods added keep their formatting unless they are removed. Valid only during the listener call.
 */
@ApiStability(StabilityLevel.PREVIEW)
public interface TooltipLines {

    /** Number of lines, the name included. */
    int size();

    /**
     * The text of line {@code index} without formatting.
     *
     * @throws IndexOutOfBoundsException for an index outside {@code [0, size())}
     */
    String get(int index);

    /** Adds a line at the end. */
    void add(String line);

    /**
     * Inserts a line before line {@code index}; {@code size()} appends.
     *
     * @throws IndexOutOfBoundsException for an index outside {@code [0, size()]}
     */
    void add(int index, String line);

    /**
     * Removes line {@code index}.
     *
     * @throws IndexOutOfBoundsException for an index outside {@code [0, size())}
     */
    void remove(int index);
}
