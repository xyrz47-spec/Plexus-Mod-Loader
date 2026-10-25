package com.example.compat.networkprobe.mixins;

import com.example.compat.networkprobe.NetworkProbe;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.gametest.framework.MultipleTestTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keeps the game test server running until the probe has reported, after the tests are done. */
@Mixin(GameTestServer.class)
public abstract class GameTestServerProbeMixin {

    @Redirect(
            method = "tickServer",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/gametest/framework/MultipleTestTracker;isDone()Z"))
    private boolean fabricNetworkProbe$doneAfterTheProbe(MultipleTestTracker tracker) {
        return tracker.isDone() && NetworkProbe.reported();
    }
}
