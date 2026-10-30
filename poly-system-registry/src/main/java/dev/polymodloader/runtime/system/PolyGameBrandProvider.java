package dev.polymodloader.runtime.system;

import dev.polymodloader.api.system.SystemContext;
import dev.polymodloader.api.system.SystemProvider;
import dev.polymodloader.api.system.brand.GameBrandSystem;

/** Plexus Mod Loader's default {@code plexus:game_brand} provider: every side reports {@code plexus}. */
public final class PolyGameBrandProvider implements SystemProvider<GameBrandSystem> {

    /** The brand Plexus Mod Loader reports when no mod replaces the system. */
    public static final String DEFAULT_BRAND = "plexus";

    @Override
    public GameBrandSystem create(SystemContext context) {
        return side -> DEFAULT_BRAND;
    }
}
