package net.fabricmc.fabric.mixin.item;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.fabricmc.fabric.impl.item.ItemComponentTooltipProviderRegistryImpl;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.common.tooltip.ItemTooltipHandler;
import net.neoforged.neoforge.event.RegisterTooltipAppendersEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Plexus Mod Loader (ADR-0031), in place of Forgified Fabric API's FabricItemImpl (a NeoForge @Mod listening on its mod
// bus): NeoForge posts RegisterTooltipAppendersEvent to the NeoForge mods' buses, and this module runs as a Fabric mod
// with none, before FML's ModList exists; Fabric API's appenders join right after NeoForge's mods got the event.
@Mixin(ItemTooltipHandler.class)
abstract class ItemTooltipHandlerMixin {
    @WrapOperation(
            method = "init",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/neoforged/fml/ModLoader;postEvent(Lnet/neoforged/bus/api/Event;)V"))
    private static void registerFabricAppenders(Event event, Operation<Void> original) {
        original.call(event);
        if (event instanceof RegisterTooltipAppendersEvent appenders) {
            ItemComponentTooltipProviderRegistryImpl.register(appenders);
        }
    }
}
