package org.quiltmc.loader.impl.game;

/**
 * Plexus Mod Loader's stand-in for Quilt Loader's internal {@code GameProvider} interface, which Quilt Loader's public
 * API calls for the game version ({@code QuiltLoader.getRawGameVersion()}, {@code getNormalizedGameVersion()}, through
 * {@code QuiltLoaderImpl.getGameProvider()}). Only those two methods are here, with Quilt Loader 0.31.0-beta.4's
 * signatures; mods that use the others (locating and launching the game) are blocked by the link check, since Plexus Mod
 * Loader starts the game itself (ADR-0027 decision 4).
 */
public interface GameProvider {

    /** The Minecraft version as the game names it. */
    String getRawGameVersion();

    /** The version in semantic form; Plexus Mod Loader's supported release versions are already in it. */
    String getNormalizedGameVersion();
}
