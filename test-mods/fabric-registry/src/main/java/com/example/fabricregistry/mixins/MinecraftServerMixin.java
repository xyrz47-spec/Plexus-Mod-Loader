package com.example.fabricregistry.mixins;

import com.example.fabricregistry.FabricRegistry;
import java.util.function.BooleanSupplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * An injection into {@code MinecraftServer.tickServer}, which NeoForge and Forge patch: on the first tick, whether the
 * item registered from {@code onInitialize} is still in the registry after the host loader's own registration and
 * freezing.
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {

    @Unique
    private static volatile boolean fabricRegistry$reported;

    @Inject(method = "tickServer", at = @At("HEAD"))
    private void fabricRegistry$firstTick(BooleanSupplier haveTime, CallbackInfo callback) {
        if (!fabricRegistry$reported) {
            fabricRegistry$reported = true;
            System.out.println("[fabric_registry] first server tick: item " + FabricRegistry.ITEM_KEY.identifier()
                    + " in the registry: " + BuiltInRegistries.ITEM.containsKey(FabricRegistry.ITEM_KEY));
        }
    }
}
