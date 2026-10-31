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
// fabric-item-api-v1/src/main/java/net/fabricmc/fabric/mixin/item/DataComponentInitializersMixin.java (Fabric API's
// file, unchanged in Forgified Fabric API).
// Merged with Fabric API 0.165.0+26.3's version of the file (FabricMC/fabric, tag 0.165.0+26.3).
// Plexus Mod Loader (ADR-0031): reformatted; no other change.

package net.fabricmc.fabric.mixin.item;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.fabricmc.fabric.impl.item.DefaultItemComponentImpl;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentInitializers;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(DataComponentInitializers.class)
public abstract class DataComponentInitializersMixin {
    @WrapMethod(method = "createInitializerForRegistry")
    private static <T> DataComponentInitializers.PendingComponents<T> captureLookup(
            HolderLookup.Provider context,
            DataComponentInitializers.PendingComponentBuilders<T> elementBuilders,
            Operation<DataComponentInitializers.PendingComponents<T>> original) {
        return ScopedValue.where(DefaultItemComponentImpl.LOOKUP_PROVIDER_SCOPED_VALUE, context)
                .call(() -> original.call(context, elementBuilders));
    }
}
