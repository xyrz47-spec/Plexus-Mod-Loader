package dev.plexusmodloader.compat.fabric.loader;

import java.nio.file.Path;
import net.fabricmc.loader.impl.game.GameProvider;

/**
 * Fabric Loader's {@code GameProvider} for the game Poly Mod Loader started: Minecraft, its version and its directory.
 *
 * @param rawGameVersion the Minecraft version as the game names it
 * @param launchDirectory the game directory
 */
public record PlexusGameProvider(String rawGameVersion, Path launchDirectory) implements GameProvider {

    @Override
    public String getGameId() {
        return "minecraft";
    }

    @Override
    public String getGameName() {
        return "Minecraft";
    }

    @Override
    public String getRawGameVersion() {
        return rawGameVersion;
    }

    /** The release versions Poly Mod Loader supports are already in the normalized form. */
    @Override
    public String getNormalizedGameVersion() {
        return rawGameVersion;
    }

    @Override
    public Path getLaunchDirectory() {
        return launchDirectory;
    }

    @Override
    public boolean requiresUrlClassLoader() {
        return false;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
