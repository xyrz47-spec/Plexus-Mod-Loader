/*
 * Copyright (c) 2016, 2017, 2018, 2019 FabricMC
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
// fabric-item-api-v1/src/main/java/net/fabricmc/fabric/mixin/item/AbstractContainerMenuMixin.java (Fabric API's file,
// unchanged in Forgified Fabric API).
// Poly Mod Loader (ADR-0031): reformatted; no other change.

package net.fabricmc.fabric.mixin.item;

import net.fabricmc.fabric.api.item.v1.ItemClickBehaviorCallback;
import net.fabricmc.fabric.api.util.EventResult;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerMenu.class)
abstract class AbstractContainerMenuMixin {
    @Inject(method = "tryItemClickBehaviourOverride", at = @At("HEAD"), cancellable = true)
    private void overrideContainerMenuItemClickBehaviour(
            Player player,
            ClickAction clickAction,
            Slot slot,
            ItemStack clicked,
            ItemStack carried,
            CallbackInfoReturnable<Boolean> callback) {
        EventResult result = ItemClickBehaviorCallback.EVENT
                .invoker()
                .onItemClickBehavior(clicked, slot, carried, this.createCarriedSlotAccess(), clickAction, player);
        if (result != EventResult.PASS) {
            callback.setReturnValue(!result.allowAction());
        }
    }

    @Shadow
    private SlotAccess createCarriedSlotAccess() {
        throw new RuntimeException();
    }
}
