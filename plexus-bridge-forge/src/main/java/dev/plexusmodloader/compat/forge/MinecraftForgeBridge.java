package dev.plexusmodloader.compat.forge;

import dev.plexusmodloader.compat.bridge.GameHost;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** MinecraftForge mods ({@code META-INF/mods.toml}): diagnosed, and run by FML through {@link #gameHost}. */
public final class MinecraftForgeBridge extends FmlModsTomlBridge {

    @Override
    public String loaderName() {
        return "MinecraftForge";
    }

    @Override
    public String metadataPath() {
        return "META-INF/mods.toml";
    }

    /** FML loads Forge mods itself on Plexus Mod Loader's game class loader (ADR-0021). */
    @Override
    public Optional<GameHost> gameHost(GameHost.Request request, List<Path> modJars) throws IOException {
        return Optional.of(ForgeGameHost.create(request, modJars));
    }
}
