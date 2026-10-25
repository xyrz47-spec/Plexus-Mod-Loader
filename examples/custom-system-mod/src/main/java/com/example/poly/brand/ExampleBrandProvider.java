package com.example.poly.brand;

import dev.polymodloader.api.system.SystemContext;
import dev.polymodloader.api.system.SystemProvider;
import dev.polymodloader.api.system.brand.GameBrandSystem;

/**
 * Replaces Poly Mod Loader's {@code poly:game_brand} system. Minecraft then reports {@code poly/example} as its
 * brand, without this mod referencing any Minecraft class.
 */
public final class ExampleBrandProvider implements SystemProvider<GameBrandSystem> {

    @Override
    public GameBrandSystem create(SystemContext context) {
        return side -> "poly/example";
    }
}
