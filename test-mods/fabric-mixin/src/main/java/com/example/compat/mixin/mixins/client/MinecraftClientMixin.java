package com.example.compat.mixin.mixins.client;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Client-only configuration: changes the window title. A dedicated server never adds this configuration. */
@Mixin(Minecraft.class)
public abstract class MinecraftClientMixin {

    @Unique
    private static volatile boolean fabricMixin$reported;

    @Inject(method = "createTitle", at = @At("RETURN"), cancellable = true)
    private void fabricMixin$title(CallbackInfoReturnable<String> result) {
        String title = result.getReturnValue() + " [fabric_mixin]";
        result.setReturnValue(title);
        if (!fabricMixin$reported) {
            fabricMixin$reported = true;
            System.out.println("[fabric_mixin] client createTitle @Inject ran: " + title);
            @SuppressWarnings("deprecation")
            Object game = net.fabricmc.loader.api.FabricLoader.getInstance().getGameInstance();
            System.out.println("[fabric_mixin] getGameInstance() is this client: " + (game == (Object) this));
        }
    }
}
