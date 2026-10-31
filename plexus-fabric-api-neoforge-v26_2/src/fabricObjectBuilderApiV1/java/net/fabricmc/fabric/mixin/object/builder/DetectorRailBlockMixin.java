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
// fabric-object-builder-api-v1/src/main/java/net/fabricmc/fabric/mixin/object/builder/DetectorRailBlockMixin.java
// (Fabric API's
// file as Forgified Fabric API has it).
// Plexus Mod Loader (ADR-0031): reformatted; no other change.
package net.fabricmc.fabric.mixin.object.builder;

import java.util.List;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.object.builder.v1.entity.MinecartComparatorLogicRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DetectorRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DetectorRailBlock.class)
public abstract class DetectorRailBlockMixin {
    @Shadow
    protected abstract <T extends AbstractMinecart> List<T> getInteractingMinecartOfType(
            Level level, BlockPos blockPos, Class<T> class_, Predicate<Entity> predicate);

    @Inject(at = @At("HEAD"), method = "getAnalogOutputSignal", cancellable = true)
    private void getCustomComparatorOutput(
            BlockState state, Level level, BlockPos pos, Direction direction, CallbackInfoReturnable<Integer> cir) {
        if (state.getValue(DetectorRailBlock.POWERED)) {
            List<AbstractMinecart> carts = getInteractingMinecartOfType(
                    level,
                    pos,
                    AbstractMinecart.class,
                    cart -> MinecartComparatorLogicRegistry.getCustomComparatorLogic(cart.getType()) != null);
            for (AbstractMinecart cart : carts) {
                int comparatorValue = MinecartComparatorLogicRegistry.getCustomComparatorLogic(cart.getType())
                        .getComparatorValue(cart, state, pos);
                if (comparatorValue >= 0) {
                    cir.setReturnValue(comparatorValue);
                    break;
                }
            }
        }
    }
}
