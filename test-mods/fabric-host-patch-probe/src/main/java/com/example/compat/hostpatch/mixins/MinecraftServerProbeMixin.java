package com.example.compat.hostpatch.mixins;

import com.example.compat.hostpatch.HostPatchProbe;
import java.util.function.BooleanSupplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Runs {@link HostPatchProbe} on the server's first tick, when data packs and tags are loaded. */
@Mixin(targets = "net.minecraft.server.MinecraftServer")
public abstract class MinecraftServerProbeMixin {

    @Unique
    private static volatile boolean hostPatchProbe$ran;

    @Inject(method = "tickServer", at = @At("HEAD"))
    private void hostPatchProbe$firstTick(BooleanSupplier haveTime, CallbackInfo callback) {
        if (!hostPatchProbe$ran) {
            hostPatchProbe$ran = true;
            HostPatchProbe.run(this);
        }
    }
}
