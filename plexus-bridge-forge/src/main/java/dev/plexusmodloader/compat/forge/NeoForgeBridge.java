package dev.plexusmodloader.compat.forge;

import dev.plexusmodloader.compat.bridge.GameHost;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** NeoForge mods ({@code META-INF/neoforge.mods.toml}): diagnosed, and run by FML through {@link #gameHost}. */
public final class NeoForgeBridge extends FmlModsTomlBridge {

    @Override
    public String loaderName() {
        return "NeoForge";
    }

    @Override
    public String metadataPath() {
        return "META-INF/neoforge.mods.toml";
    }

    /** FML 11 loads NeoForge mods itself, with Poly Mod Loader's game class loader defining its game layer (ADR-0026). */
    @Override
    public Optional<GameHost> gameHost(GameHost.Request request, List<Path> modJars) throws IOException {
        return Optional.of(NeoForgeGameHost.create(request, modJars));
    }
}
