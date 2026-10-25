package com.example.neoforgeearly;

import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test mod laid out as Sodium's NeoForge build: the jar players install is an FML library with an early service
 * ({@code GraphicsBootstrapper}), and this mod is the jar nested in it. NeoForge's FML loads the outer jar as an early
 * service and the mod from its JarJar; FML must not see the outer jar as a second, library-only copy of the mod.
 */
@Mod(NeoForgeEarlyService.MOD_ID)
public final class NeoForgeEarlyService {

    static final String MOD_ID = "neoforge_early_service";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public NeoForgeEarlyService() {
        LOGGER.info("[{}] Constructed by FML from the jar nested in the early service jar", MOD_ID);
    }
}
