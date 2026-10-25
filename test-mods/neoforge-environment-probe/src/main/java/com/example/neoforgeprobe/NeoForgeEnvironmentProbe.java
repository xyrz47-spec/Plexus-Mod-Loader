package com.example.neoforgeprobe;

import java.lang.reflect.Method;
import java.util.Optional;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reports what multi-loader libraries see under NeoForge, in a headless run. Yumi (yumi_mc_core, nested in Lambda
 * Dynamic Lights) picks its runtime by which loader API classes exist ({@code net.fabricmc.loader.api.FabricLoader}
 * first), and Lambda Dynamic Lights finds its default configuration through Yumi's {@code ModContainer.findPath}, on
 * the client only. This mod makes the same lookup on any side, when Lambda Dynamic Lights is installed.
 */
@Mod(NeoForgeEnvironmentProbe.MOD_ID)
public final class NeoForgeEnvironmentProbe {

    static final String MOD_ID = "neoforge_environment_probe";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public NeoForgeEnvironmentProbe(IEventBus modBus) {
        modBus.addListener(FMLCommonSetupEvent.class, event -> report());
    }

    private static void report() {
        LOGGER.info(
                "[{}] Fabric Loader API visible: {}, Quilt Loader API visible: {}",
                MOD_ID,
                classExists("net.fabricmc.loader.api.FabricLoader"),
                classExists("org.quiltmc.loader.api.QuiltLoader"));
        if (!ModList.get().isLoaded("yumi_mc_core") || !ModList.get().isLoaded("lambdynlights")) {
            return;
        }
        try {
            Class<?> yumiMods = Class.forName("dev.yumi.mc.core.api.YumiMods");
            Object mods = yumiMods.getMethod("get").invoke(null);
            Optional<?> container =
                    (Optional<?>) yumiMods.getMethod("getMod", String.class).invoke(mods, "lambdynlights");
            if (container.isEmpty()) {
                LOGGER.info("[{}] Yumi has no mod lambdynlights", MOD_ID);
                return;
            }
            Object mod = container.get();
            Method findPath = Class.forName("dev.yumi.mc.core.api.ModContainer")
                    .getMethod("findPath", String.class, String[].class);
            Optional<?> path = (Optional<?>) findPath.invoke(mod, "lambdynlights.toml", new String[0]);
            LOGGER.info(
                    "[{}] Yumi's {} for lambdynlights finds lambdynlights.toml: {}",
                    MOD_ID,
                    mod.getClass().getSimpleName(),
                    path.isPresent());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Yumi's API is not what this probe expects", e);
        }
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
