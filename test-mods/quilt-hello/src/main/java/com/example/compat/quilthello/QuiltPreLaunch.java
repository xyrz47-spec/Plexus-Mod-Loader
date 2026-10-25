package com.example.compat.quilthello;

import org.quiltmc.loader.api.ModContainer;
import org.quiltmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Quilt's {@code pre_launch}: Quilt Loader runs it before Fabric's {@code preLaunch}, with the mod's container. */
public final class QuiltPreLaunch implements PreLaunchEntrypoint {

    private static final Logger LOGGER = LoggerFactory.getLogger("quilt_hello");

    @Override
    public void onPreLaunch(ModContainer mod) {
        Stages.ran("pre_launch");
        LOGGER.info(
                "[quilt_hello] Quilt pre_launch ran for {} {}",
                mod.metadata().id(),
                mod.metadata().version().raw());
    }
}
