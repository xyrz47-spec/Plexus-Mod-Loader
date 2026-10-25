package dev.polymodloader.runtime.system;

import dev.polymodloader.api.system.SystemContext;
import dev.polymodloader.api.system.SystemProvider;
import dev.polymodloader.api.system.brand.GameBrandSystem;

/** Poly Mod Loader's default {@code poly:game_brand} provider: every side reports {@code poly}. */
public final class PolyGameBrandProvider implements SystemProvider<GameBrandSystem> {

    /** The brand Poly Mod Loader reports when no mod replaces the system. */
    public static final String DEFAULT_BRAND = "poly";

    @Override
    public GameBrandSystem create(SystemContext context) {
        return side -> DEFAULT_BRAND;
    }
}
