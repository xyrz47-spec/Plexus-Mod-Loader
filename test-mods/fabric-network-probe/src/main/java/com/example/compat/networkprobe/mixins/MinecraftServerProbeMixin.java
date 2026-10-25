package com.example.compat.networkprobe.mixins;

import com.example.compat.networkprobe.NetworkProbe;
import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Starts the probe on the server's first tick and ticks it after that. */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerProbeMixin {

    @Inject(method = "tickServer", at = @At("HEAD"))
    private void fabricNetworkProbe$tick(BooleanSupplier haveTime, CallbackInfo callback) {
        NetworkProbe.tick((MinecraftServer) (Object) this);
    }
}
