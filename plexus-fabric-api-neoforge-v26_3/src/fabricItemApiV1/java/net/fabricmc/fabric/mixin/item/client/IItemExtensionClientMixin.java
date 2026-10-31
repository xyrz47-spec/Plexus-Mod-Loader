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
// fabric-item-api-v1/src/client/java/net/fabricmc/fabric/mixin/item/client/IItemExtensionClientMixin.java (written in
// Forgified Fabric API).
// Merged with Fabric API 0.165.0+26.3's version of the file (FabricMC/fabric, tag 0.165.0+26.3).
// Plexus Mod Loader (ADR-0031): reformatted; no other change.
package net.fabricmc.fabric.mixin.item.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.fabricmc.fabric.api.item.v1.FabricItem;
import net.fabricmc.fabric.impl.item.RecursivityHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.extensions.IItemExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(IItemExtension.class)
public interface IItemExtensionClientMixin {

    @ModifyReturnValue(method = "shouldCauseReequipAnimation", at = @At("RETURN"))
    default boolean shouldCauseReequipAnimation(
            boolean result, ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        if (result) {
            Player player = Minecraft.getInstance().player;
            InteractionHand hand =
                    oldStack == player.getMainHandItem() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
            return RecursivityHelper.nonRecursiveApiCall(
                    () -> ((FabricItem) this).allowComponentsUpdateAnimation(player, hand, oldStack, newStack));
        }
        return false;
    }

    @Inject(
            method = "shouldCauseBlockBreakReset",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;isDamageableItem()Z"),
            cancellable = true)
    default void shouldCauseBlockBreakReset(
            ItemStack oldStack, ItemStack newStack, CallbackInfoReturnable<Boolean> cir) {
        if (!ItemStack.isSameItemSameComponents(newStack, oldStack)
                && oldStack.getItem()
                        .allowContinuingBlockBreaking(Minecraft.getInstance().player, oldStack, newStack)) {
            cir.setReturnValue(false);
        }
    }
}
