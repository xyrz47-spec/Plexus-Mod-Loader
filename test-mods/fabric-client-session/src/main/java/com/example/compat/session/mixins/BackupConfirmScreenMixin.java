package com.example.compat.session.mixins;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.BackupConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Answers vanilla's prompt to back a world up before loading it as a player who joins without a backup ("I know what
 * I'm doing"), and says so. Opening a world asks this, for example, with "Worlds using Experimental Settings are not
 * supported" when the world's registries hold entries from mod data packs, as with Forge on Minecraft 26.1.
 */
@Mixin(BackupConfirmScreen.class)
public abstract class BackupConfirmScreenMixin extends Screen {

    @Shadow
    @Final
    protected BackupConfirmScreen.Listener onProceed;

    @Unique
    private boolean session$answered;

    private BackupConfirmScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void session$answer(CallbackInfo info) {
        if (session$answered) {
            return;
        }
        session$answered = true;
        System.out.println("[fabric_client_session] the world asks for a backup before loading ("
                + getTitle().getString() + "); joining without one");
        // After this screen is set: proceeding opens the next one.
        Minecraft.getInstance().execute(() -> onProceed.proceed(false, false));
    }
}
