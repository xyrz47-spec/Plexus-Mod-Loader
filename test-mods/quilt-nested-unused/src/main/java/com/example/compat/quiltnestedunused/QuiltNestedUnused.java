package com.example.compat.quiltnestedunused;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Never runs: Quilt Loader loads a nested if_required mod only when a loaded mod requires it, and none does. */
public final class QuiltNestedUnused implements ModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("quilt_nested_unused");

    @Override
    public void onInitialize() {
        LOGGER.info("[quilt_nested_unused] initialised although no mod requires it");
    }
}
