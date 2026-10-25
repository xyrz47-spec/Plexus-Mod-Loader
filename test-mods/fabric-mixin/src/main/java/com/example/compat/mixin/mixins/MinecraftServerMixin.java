package com.example.compat.mixin.mixins;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Two injections into methods Poly Mod Loader itself already transforms ({@code poly:server_tick_events} on
 * {@code tickServer}, {@code poly:server_brand} on {@code getServerModName}): Mixin runs after Poly Mod Loader's
 * transformer, as it runs after Fabric Loader's class tweaker.
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {

    @Unique
    private static volatile boolean fabricMixin$reported;

    @Inject(method = "tickServer", at = @At("HEAD"))
    private void fabricMixin$firstTick(BooleanSupplier haveTime, CallbackInfo callback) {
        if (!fabricMixin$reported) {
            fabricMixin$reported = true;
            MinecraftServer server = (MinecraftServer) (Object) this;
            System.out.println(
                    "[fabric_mixin] tickServer @Inject ran; getServerModName() = " + server.getServerModName());
            @SuppressWarnings("deprecation")
            Object game = net.fabricmc.loader.api.FabricLoader.getInstance().getGameInstance();
            System.out.println("[fabric_mixin] getGameInstance() is this server: " + (game == server));
            // Called on MinecraftServer itself, as a Loom-compiled mod calls injected methods; they resolve to the
            // default methods of the interfaces the class tweaker injected.
            String generic = Arrays.stream(MinecraftServer.class.getGenericInterfaces())
                    .map(Type::getTypeName)
                    .filter(name -> name.contains("Tagged"))
                    .findFirst()
                    .orElse("missing");
            System.out.println("[fabric_mixin] inject-interface: " + server.fabricMixin$greeting() + ", "
                    + server.fabricMixin$tag() + ", generic " + generic);
        }
    }

    @Inject(method = "getServerModName", at = @At("RETURN"), cancellable = true)
    private void fabricMixin$brand(CallbackInfoReturnable<String> result) {
        result.setReturnValue(result.getReturnValue() + "+fabric_mixin");
    }
}
