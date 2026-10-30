package dev.plexusmodloader.compat.forge.modlauncher;

import java.nio.file.Path;
import java.util.List;
import net.minecraftforge.fml.loading.targets.CommonLaunchHandler;

/**
 * Mojang's game test server for an installed (production) Forge, as Poly Mod Loader's launch target {@code
 * game_test_server}. Forge runs game tests only outside production: {@code ForgeGameTestHooks.isGametestEnabled()}
 * is false when {@code FMLLoader.isProduction()}, and Forge's patched server then never ticks the tests. Forge's own
 * {@code server_gametest} handlers are development ones that look for Minecraft on a development class path. This
 * handler is that launch type ({@code net.minecraft.gametest.Main}, dedicated server, not production) with the
 * production server's way of finding Minecraft: the patched jar that holds {@code MinecraftServer}.
 */
public final class GameTestLaunchHandler extends CommonLaunchHandler {

    public GameTestLaunchHandler() {
        super(SERVER_GAMETEST, "polymodloader_");
    }

    @Override
    public String getNaming() {
        return "mcp";
    }

    @Override
    public List<Path> getMinecraftPaths() {
        return List.of(getPathFromResource("net/minecraft/server/MinecraftServer.class"));
    }
}
