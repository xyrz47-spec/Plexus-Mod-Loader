package com.example.forgemixin.mixins;

import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Two injections into a class Forge patches and Poly Mod Loader transforms: {@code tickServer} carries Poly Mod Loader's
 * {@code poly:server_tick_events}, and Forge changes {@code getServerModName}.
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {

    @Unique
    private static volatile boolean forgeMixin$reported;

    @Inject(method = "tickServer", at = @At("HEAD"))
    private void forgeMixin$firstTick(BooleanSupplier haveTime, CallbackInfo callback) {
        if (!forgeMixin$reported) {
            forgeMixin$reported = true;
            MinecraftServer server = (MinecraftServer) (Object) this;
            System.out.println(
                    "[forge_mixin] tickServer @Inject ran; getServerModName() = " + server.getServerModName());
        }
    }

    @Inject(method = "getServerModName", at = @At("RETURN"), cancellable = true)
    private void forgeMixin$brand(CallbackInfoReturnable<String> result) {
        result.setReturnValue(result.getReturnValue() + "+forge_mixin");
    }
}
