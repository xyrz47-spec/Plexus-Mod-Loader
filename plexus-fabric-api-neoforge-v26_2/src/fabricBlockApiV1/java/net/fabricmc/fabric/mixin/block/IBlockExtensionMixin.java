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
// fabric-block-api-v1/src/main/java/net/fabricmc/fabric/mixin/block/IBlockExtensionMixin.java (written in Forgified
// Fabric API).
// Plexus Mod Loader (ADR-0031): reformatted; no other change.
package net.fabricmc.fabric.mixin.block;

import net.fabricmc.fabric.api.block.v1.FabricBlock;
import net.fabricmc.fabric.impl.block.EnchantmentPowerBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.extensions.IBlockExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(IBlockExtension.class)
public interface IBlockExtensionMixin extends FabricBlock {
    @Inject(method = "getEnchantPowerBonus", at = @At("HEAD"), cancellable = true)
    default void getEnchantPowerBonus(
            BlockState state, BlockGetter level, BlockPos pos, CallbackInfoReturnable<Float> cir) {
        if (EnchantmentPowerBridge.isFabricCall()) {
            cir.setReturnValue(EnchantmentPowerBridge.FABRIC_DEFAULT_POWER);
        } else if (state.is(BlockTags.ENCHANTMENT_POWER_PROVIDER)) {
            cir.setReturnValue(
                    EnchantmentPowerBridge.callFabric(() -> this.getProvidedEnchantmentPower(state, level, pos)));
        }
    }
}
