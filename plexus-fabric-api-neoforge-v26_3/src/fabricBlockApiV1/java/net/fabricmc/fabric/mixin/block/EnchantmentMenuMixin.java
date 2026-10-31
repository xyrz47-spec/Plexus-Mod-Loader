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
// fabric-block-api-v1/src/main/java/net/fabricmc/fabric/mixin/block/EnchantmentMenuMixin.java (Fabric API's file as
// Forgified Fabric API changed it for NeoForge).
// Merged with Fabric API 0.165.0+26.3's version of the file (FabricMC/fabric, tag 0.165.0+26.3).
// Plexus Mod Loader (ADR-0031): reformatted; no other change.

package net.fabricmc.fabric.mixin.block;

import net.minecraft.world.inventory.EnchantmentMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(EnchantmentMenu.class)
public class EnchantmentMenuMixin {
    @ModifyArg(
            method = "lambda$slotsChanged$0",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;getEnchantmentCost(Lnet/minecraft/util/RandomSource;IILnet/minecraft/world/item/ItemStack;)I"),
            index = 2)
    private int clampEnchantingPower(int power) {
        return Math.max(power, 0);
    }

    @ModifyArg(
            method = "lambda$slotsChanged$0",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/neoforged/neoforge/event/EventHooks;onEnchantmentLevelSet(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;IILnet/minecraft/world/item/ItemStack;I)I"),
            index = 3)
    private int clampEventEnchantingPower(int power) {
        return Math.max(power, 0);
    }
}
