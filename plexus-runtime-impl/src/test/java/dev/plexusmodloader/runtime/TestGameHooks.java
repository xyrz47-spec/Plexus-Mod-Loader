package dev.plexusmodloader.runtime;

import dev.plexusmodloader.api.runtime.GameSide;
import dev.plexusmodloader.api.system.SystemLookup;
import dev.plexusmodloader.api.system.brand.GameBrandSystem;

/** Hooks the synthetic test game calls after transformation; bound by {@link TestGameVersionAdapter}. */
public final class TestGameHooks {

    private static volatile SystemLookup systems;

    private TestGameHooks() {}

    static void bind(SystemLookup lookup) {
        systems = lookup;
    }

    public static String brand() {
        return systems.get(GameBrandSystem.class).brand(GameSide.CLIENT);
    }
}
