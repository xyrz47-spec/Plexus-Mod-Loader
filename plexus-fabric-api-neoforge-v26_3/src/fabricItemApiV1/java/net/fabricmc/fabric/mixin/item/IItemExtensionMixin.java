/*
 * Copyright (c) 2016, 2017, 2018, 2019 FabricMC, Sinytra
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// Source: Sinytra Forgified Fabric API, branch 26.2.x, commit 40f8ce0,
// fabric-item-api-v1/src/main/java/net/fabricmc/fabric/mixin/item/IItemExtensionMixin.java (written in Forgified Fabric
// API).
// Merged with Fabric API 0.165.0+26.3's version of the file (FabricMC/fabric, tag 0.165.0+26.3).
// Plexus Mod Loader (ADR-0031): reformatted; isPrimaryItemFor honours an explicit ALLOW_ENCHANTING answer (see there).
package net.fabricmc.fabric.mixin.item;

import net.fabricmc.fabric.api.item.v1.EnchantingContext;
import net.fabricmc.fabric.api.item.v1.EnchantmentEvents;
import net.fabricmc.fabric.api.item.v1.EquipmentSlotProvider;
import net.fabricmc.fabric.api.item.v1.FabricItem;
import net.fabricmc.fabric.api.util.TriState;
import net.fabricmc.fabric.impl.item.ItemExtensions;
import net.fabricmc.fabric.impl.item.RecursivityHelper;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.neoforge.common.extensions.IItemExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(IItemExtension.class)
public interface IItemExtensionMixin extends FabricItem {

    @Inject(method = "getCraftingRemainder", at = @At("HEAD"), cancellable = true)
    default void getCraftingRemainder(ItemInstance item, CallbackInfoReturnable<ItemStackTemplate> cir) {
        ItemStack stack = item instanceof ItemStack s ? s : new ItemStack(item.typeHolder(), item.count());
        ItemStackTemplate fabricRemainder =
                RecursivityHelper.nonRecursiveApiCall(() -> this.getCraftingRemainder(stack));
        if (fabricRemainder != null) {
            cir.setReturnValue(fabricRemainder);
        }
    }

    @Inject(method = "getEquipmentSlot", at = @At("HEAD"), cancellable = true)
    default void getEquipmentSlot(ItemStack stack, CallbackInfoReturnable<EquipmentSlot> cir) {
        EquipmentSlotProvider equipmentSlotProvider = ((ItemExtensions) this).fabric_getEquipmentSlotProvider();

        if (equipmentSlotProvider != null) {
            cir.setReturnValue(equipmentSlotProvider.getEquipmentSlotForItem((LivingEntity) this, stack));
        }
    }

    @Inject(method = "isPrimaryItemFor", at = @At("HEAD"), cancellable = true)
    default void isPrimaryItemFor(
            ItemStack stack, Holder<Enchantment> enchantment, CallbackInfoReturnable<Boolean> cir) {
        // As in Fabric API, the event's explicit answer decides first: a listener's FALSE refuses the enchanting table
        // too (Forgified Fabric API only ever turned NeoForge's answer to true here).
        TriState decision = EnchantmentEvents.ALLOW_ENCHANTING
                .invoker()
                .allowEnchanting(enchantment, stack, EnchantingContext.PRIMARY);
        if (decision != TriState.DEFAULT) {
            cir.setReturnValue(decision.get());
            return;
        }
        if (RecursivityHelper.nonRecursiveApiCall(
                () -> stack.canBeEnchantedWith(enchantment, EnchantingContext.PRIMARY))) {
            cir.setReturnValue(true);
        }
    }
}
