package dev.plexusmodloader.runtime.system;

import dev.plexusmodloader.api.system.SystemContext;
import dev.plexusmodloader.api.system.SystemProvider;
import dev.plexusmodloader.api.system.brand.GameBrandSystem;

/** Plexus Mod Loader's default {@code plexus:game_brand} provider: every side reports {@code plexus}. */
public final class PlexusGameBrandProvider implements SystemProvider<GameBrandSystem> {

    /** The brand Plexus Mod Loader reports when no mod replaces the system. */
    public static final String DEFAULT_BRAND = "plexus";

    @Override
    public GameBrandSystem create(SystemContext context) {
        return side -> DEFAULT_BRAND;
    }
}
