package com.example.compat.quilthello.mixins;

import java.util.function.BooleanSupplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A Quilt mod's {@code mixin} configuration, applied by the same Mixin as Fabric mods' (ADR-0027 decision 9). */
@Mixin(targets = "net.minecraft.server.MinecraftServer")
public abstract class MinecraftServerMixin {

    @Unique
    private static volatile boolean quiltHello$reported;

    @Inject(method = "tickServer", at = @At("HEAD"))
    private void quiltHello$firstTick(BooleanSupplier haveTime, CallbackInfo callback) {
        if (!quiltHello$reported) {
            quiltHello$reported = true;
            System.out.println("[quilt_hello] Quilt mod's Mixin: tickServer @Inject ran");
        }
    }
}
