package com.example.compat.dual;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The entrypoint {@code fabric.mod.json} names: the one that runs, since Poly Mod Loader runs a jar with both files as
 * a Fabric mod (ADR-0027 decision 2). As a Fabric mod it also looks at the Quilt mod {@code quilt_hello}.
 */
public final class DualFabric implements ModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("fabric_quilt_dual");

    @Override
    public void onInitialize() {
        FabricLoader loader = FabricLoader.getInstance();
        LOGGER.info(
                "[fabric_quilt_dual] Fabric entrypoint ran; this mod's type is {}",
                loader.getModContainer("fabric_quilt_dual")
                        .orElseThrow()
                        .getMetadata()
                        .getType());
        LOGGER.info(
                "[fabric_quilt_dual] FabricLoader sees quilt_hello: {}",
                loader.getModContainer("quilt_hello")
                        .map(mod -> mod.getMetadata().getType() + " "
                                + mod.getMetadata().getVersion().getFriendlyString() + " "
                                + mod.getMetadata().getName() + ", authors "
                                + mod.getMetadata().getAuthors().stream()
                                        .map(person -> person.getName())
                                        .toList())
                        .orElse("not loaded"));
    }
}
