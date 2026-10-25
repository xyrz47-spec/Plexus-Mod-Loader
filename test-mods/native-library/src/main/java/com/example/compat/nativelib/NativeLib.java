package com.example.compat.nativelib;

import dev.polymodloader.api.lifecycle.CommonModInitializer;
import dev.polymodloader.api.lifecycle.ModContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Sets its value in its entrypoint, so a dependent that reads it too early sees 0. */
public final class NativeLib implements CommonModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("native_lib");
    private static volatile int value;

    /** 42 once this mod's entrypoint has run, 0 before. */
    public static int value() {
        return value;
    }

    @Override
    public void onInitializeCommon(ModContext context) {
        value = 42;
        LOGGER.info("[native_lib] initialised");
        // Mods of other loaders in the same game, as a Poly Mod sees them (ModContext.mods()).
        for (String id : new String[] {"fabric_lib", "forge_hello", "neoforge_hello", "quilt_hello"}) {
            LOGGER.info(
                    "[native_lib] sees {}: {}",
                    id,
                    context.mods()
                            .find(id)
                            .map(mod -> mod.loader() + " " + mod.version())
                            .orElse("not loaded"));
        }
    }
}
