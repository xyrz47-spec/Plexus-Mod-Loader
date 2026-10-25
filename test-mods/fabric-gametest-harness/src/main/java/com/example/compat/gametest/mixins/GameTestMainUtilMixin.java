package com.example.compat.gametest.mixins;

import java.lang.reflect.InvocationTargetException;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestMainUtil;
import net.minecraft.world.item.CreativeModeTabs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Harness glue for headless corpus runs, not a Poly Mod Loader feature. Fabric API's registry sync keeps Minecraft's
 * bootstrap from freezing the built-in registries and freezes them after mod initialisation with a mixin on
 * {@code net.minecraft.server.Main} ({@code MainMixin.afterModInit}). Mojang's game test server starts from
 * {@code GameTestMainUtil} instead, which Fabric API does not hook, so the registries would stay unfrozen there under
 * Fabric Loader too. This does the same three calls at the same point of the game test start, only when Fabric API's
 * registry sync is loaded.
 */
@Mixin(GameTestMainUtil.class)
public abstract class GameTestMainUtilMixin {

    @Inject(
            method = "runGameTestServer",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Util;startTimerHackThread()V"))
    private static void polyHarness$freezeLikeServerMain(CallbackInfo callback) {
        if (!FabricLoader.getInstance().isModLoaded("fabric-registry-sync-v0")) {
            return;
        }
        BuiltInRegistries.bootStrap();
        try {
            Class.forName(
                            "net.fabricmc.fabric.impl.registry.sync.trackers.vanilla.BlockInitTracker",
                            true,
                            GameTestMainUtilMixin.class.getClassLoader())
                    .getMethod("postFreeze")
                    .invoke(null);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("Fabric API's BlockInitTracker.postFreeze failed", e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Fabric API's registry sync has no BlockInitTracker.postFreeze", e);
        }
        CreativeModeTabs.validate();
        System.out.println("[poly_gametest_harness] froze registries after mod initialisation, as Fabric API does"
                + " on the dedicated server");
    }
}
