package com.example.compat.bootstrapprobe.mixins;

import java.nio.file.Path;
import net.minecraft.CrashReport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Under NeoForge, a failure while the client's Main loads the mods goes to {@code ClientModLoader.reportFatalError},
 * which shows FML's error dialog (Swing or a native message box) and waits for a click (FML 11 {@code
 * FatalErrorReporting}, {@code javap}). With {@code -Dfabric_client_bootstrap_probe.stop=true} the client prints the
 * failure and exits instead, so a headless check never opens that dialog. Absent without NeoForge ({@code @Pseudo}).
 */
@Pseudo
@Mixin(targets = "net.neoforged.neoforge.client.loading.ClientModLoader")
public abstract class NeoForgeFatalErrorProbeMixin {

    @Unique
    private static final boolean STOP = Boolean.getBoolean("fabric_client_bootstrap_probe.stop");

    @Inject(method = "reportFatalError", at = @At("HEAD"), require = 0)
    private static void fabric_client_bootstrap_probe$exitInsteadOfDialog(
            Throwable error, Path gameDirectory, CrashReport report, CallbackInfo info) {
        if (!STOP) {
            return;
        }
        System.out.println(
                "[fabric_client_bootstrap_probe] NeoForge reports a fatal error; exiting without its dialog");
        error.printStackTrace(System.out);
        System.out.flush();
        Runtime.getRuntime().halt(1);
    }
}
