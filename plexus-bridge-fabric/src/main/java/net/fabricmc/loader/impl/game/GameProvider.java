package net.fabricmc.loader.impl.game;

import java.nio.file.Path;

/**
 * Poly Mod Loader's stand-in for Fabric Loader's internal {@code GameProvider}, which some mods read through {@code
 * FabricLoaderImpl.getGameProvider()} (veinminer reads the raw game version). Only the methods that describe the
 * running game are here, with Fabric Loader 0.19.5's signatures; mods that use the others (locating and launching the
 * game, built-in mods and transforms) are blocked by the link check, since Poly Mod Loader starts the game itself.
 */
public interface GameProvider {

    String getGameId();

    String getGameName();

    String getRawGameVersion();

    String getNormalizedGameVersion();

    Path getLaunchDirectory();

    boolean requiresUrlClassLoader();

    boolean isEnabled();
}
