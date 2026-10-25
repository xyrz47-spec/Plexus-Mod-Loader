package com.example.neoforgemixin.mixins;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Two injections into a class NeoForge patches and Poly Mod Loader transforms: {@code tickServer} carries Poly Mod
 * Loader's {@code poly:server_tick_events}; {@code getServerVersion} is changed with MixinExtras.
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {

    @Unique
    private static volatile boolean neoforgeMixin$reported;

    @Inject(method = "tickServer", at = @At("HEAD"))
    private void neoforgeMixin$firstTick(BooleanSupplier haveTime, CallbackInfo callback) {
        if (!neoforgeMixin$reported) {
            neoforgeMixin$reported = true;
            MinecraftServer server = (MinecraftServer) (Object) this;
            System.out.println(
                    "[neoforge_mixin] tickServer @Inject ran; getServerModName() = " + server.getServerModName()
                            + "; MixinExtras @ModifyReturnValue: getServerVersion() = " + server.getServerVersion());
        }
    }

    @ModifyReturnValue(method = "getServerVersion", at = @At("RETURN"))
    private String neoforgeMixin$version(String version) {
        return version + "+neoforge_mixin";
    }
}
