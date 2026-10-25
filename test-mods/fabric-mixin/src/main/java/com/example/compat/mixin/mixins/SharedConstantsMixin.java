package com.example.compat.mixin.mixins;

import net.minecraft.SharedConstants;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** {@code @Inject} at the head of a private static 26.2 method, cancelling it for one argument. */
@Mixin(SharedConstants.class)
public abstract class SharedConstantsMixin {

    @Inject(method = "prefixDebugFlagName", at = @At("HEAD"), cancellable = true)
    private static void fabricMixin$prefix(String name, CallbackInfoReturnable<String> result) {
        if (name.equals("POLY_MIXIN")) {
            result.setReturnValue("mixin-was-here");
        }
    }
}
