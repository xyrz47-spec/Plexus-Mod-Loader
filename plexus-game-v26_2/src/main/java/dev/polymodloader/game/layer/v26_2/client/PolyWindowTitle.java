package dev.polymodloader.game.layer.v26_2.client;

import dev.polymodloader.game.layer.v26_2.LayerState;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;

/**
 * The game window's title names Plexus Mod Loader in the place where a loader puts its name: {@code Minecraft* Plexus 26.2}
 * for the game's {@code Minecraft* 26.2}, MinecraftForge's {@code Minecraft* Forge 26.2} and NeoForge's {@code Minecraft
 * NeoForge* 26.2}, whichever loaders' mods run. What follows the version ({@code  - Singleplayer}, {@code  -
 * Multiplayer (LAN)}) and the game's {@code *} for a modified game stay as the game wrote them.
 *
 * <p>Called after {@code Minecraft.createTitle} ({@code poly:client_window_title}). Thread: the render thread.
 */
public final class PolyWindowTitle {

    private PolyWindowTitle() {}

    /** {@code title} with Plexus Mod Loader's name before the game's version; {@code title} itself without the version. */
    public static String onCreateTitleReturn(Minecraft minecraft, String title) {
        LayerState.context().ifPresent(context -> context.gameWindow().opening());
        return named(title, SharedConstants.getCurrentVersion().name());
    }

    /** {@code title} ({@code <prefix> <version>[<rest>]}) as {@code Minecraft[*] Plexus <version>[<rest>]}. */
    static String named(String title, String version) {
        if (title == null || version == null || version.isEmpty()) {
            return title;
        }
        int at = title.indexOf(" " + version);
        if (at < 0) {
            return title;
        }
        boolean modified = title.substring(0, at).contains("*");
        return "Minecraft" + (modified ? "*" : "") + " Plexus " + version + title.substring(at + 1 + version.length());
    }
}
