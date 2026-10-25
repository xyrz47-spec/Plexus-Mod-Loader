package com.example.compat.launcheruser;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.game.GameProvider;
import net.fabricmc.loader.impl.launch.FabricLauncher;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Unpacks the jar it carries and adds it to the game class path with Fabric Loader's internal launcher, then uses a
 * class from it, as CustomSkinLoader does with its common jar; reads the game version through {@code FabricLoaderImpl},
 * as veinminer does.
 */
public final class LauncherUser implements ModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("fabric_launcher_user");

    @Override
    public void onInitialize() {
        Path jar = FabricLoader.getInstance().getGameDir().resolve("fabric_launcher_user/extra-lib.jar");
        try (InputStream in = LauncherUser.class.getResourceAsStream("/launcher/extra-lib.jar")) {
            if (in == null) {
                throw new IllegalStateException("launcher/extra-lib.jar is missing from the mod");
            }
            Files.createDirectories(jar.getParent());
            Files.copy(in, jar, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        FabricLauncher launcher = FabricLauncherBase.getLauncher();
        launcher.addToClassPath(jar, "com.example.compat.launcherextra.");
        try {
            Class<?> extra =
                    Class.forName("com.example.compat.launcherextra.Extra", true, launcher.getTargetClassLoader());
            LOGGER.info(
                    "[fabric_launcher_user] extra-lib says {} (loaded by {}), environment {}, development {}",
                    extra.getMethod("greeting").invoke(null),
                    extra.getClassLoader().getName(),
                    launcher.getEnvironmentType(),
                    launcher.isDevelopment());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("extra-lib.jar was not usable after addToClassPath", e);
        }
        // As veinminer reads the game version.
        GameProvider game = ((FabricLoaderImpl) FabricLoader.getInstance()).getGameProvider();
        LOGGER.info(
                "[fabric_launcher_user] FabricLoaderImpl game provider: {} {}",
                game.getGameId(),
                game.getRawGameVersion());
    }
}
