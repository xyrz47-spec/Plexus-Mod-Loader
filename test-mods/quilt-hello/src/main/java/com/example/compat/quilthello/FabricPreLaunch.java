package com.example.compat.quilthello;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Fabric's {@code preLaunch} declared in {@code quilt.mod.json}: Quilt Loader runs it after Quilt's {@code pre_launch}. */
public final class FabricPreLaunch implements PreLaunchEntrypoint {

    private static final Logger LOGGER = LoggerFactory.getLogger("quilt_hello");

    @Override
    public void onPreLaunch() {
        Stages.ran("preLaunch");
        LOGGER.info("[quilt_hello] Fabric preLaunch ran");
    }
}
