package dev.polymodloader.runtime;

import dev.polymodloader.api.runtime.GameSide;
import dev.polymodloader.api.system.SystemLookup;
import dev.polymodloader.api.system.brand.GameBrandSystem;

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
