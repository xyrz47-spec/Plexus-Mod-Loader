package net.fabricmc.fabric.mixin.registry.sync;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.fabricmc.fabric.impl.registry.sync.DynamicRegistriesImpl;
import net.fabricmc.fabric.impl.registry.sync.FabricRegistryInit;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.registries.ModifyRegistriesEvent;
import net.neoforged.neoforge.registries.NewDatapackRegistryEvent;
import net.neoforged.neoforge.registries.RegistryManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Plexus Mod Loader (ADR-0031), in place of Forgified Fabric API's FabricRegistryInit listeners on its NeoForge mod
// bus:
// this module runs as a Fabric mod with no bus, so the two events RegistryManager posts to the NeoForge mods reach
// Fabric API right where NeoForge's mods got them.
@Mixin(RegistryManager.class)
abstract class RegistryManagerMixin {
    // Fabric's pending dynamic registries join NeoForge's data pack registries before NeoForge processes the event.
    @WrapOperation(
            method = "postNewRegistryEvent",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/neoforged/neoforge/registries/NewDatapackRegistryEvent;process()V"))
    private static void addFabricDynamicRegistries(NewDatapackRegistryEvent event, Operation<Void> original) {
        DynamicRegistriesImpl.onNewDatapackRegistries(event);
        original.call(event);
    }

    // Fabric's remap callbacks go on every registry once NeoForge's mods modified them.
    @WrapOperation(
            method = "postNewRegistryEvent",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/neoforged/fml/ModLoader;postEvent(Lnet/neoforged/bus/api/Event;)V"))
    private static void addFabricRemapCallbacks(Event event, Operation<Void> original) {
        original.call(event);
        if (event instanceof ModifyRegistriesEvent modify) {
            FabricRegistryInit.injectCallbacks(modify);
        }
    }
}
