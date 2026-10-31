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
// fabric-object-builder-api-v1/src/main/java/net/fabricmc/fabric/mixin/object/builder/EntityDataSerializersMixin.java
// (Fabric API's
// file as Forgified Fabric API has it).
// Merged with Fabric API 0.165.0+26.3's version of the file (FabricMC/fabric, tag 0.165.0+26.3).
// Plexus Mod Loader (ADR-0031): reformatted; no other change.
package net.fabricmc.fabric.mixin.object.builder;

import net.fabricmc.fabric.impl.object.builder.FabricEntityDataRegistryImpl;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.EntityDataSerializers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityDataSerializers.class)
abstract class EntityDataSerializersMixin {
    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void storeVanillaHandlers(CallbackInfo ci) {
        FabricEntityDataRegistryImpl.storeVanillaHandlers();
    }

    @Inject(method = "registerSerializer(Lnet/minecraft/network/syncher/EntityDataSerializer;)V", at = @At("HEAD"))
    private static void onHeadRegister(EntityDataSerializer<?> handler, CallbackInfo ci) {
        if (FabricEntityDataRegistryImpl.hasStoredVanillaHandlers()
                && FabricLoader.getInstance().isDevelopmentEnvironment()) {
            throw new IllegalStateException(
                    "Tried to register entity data serializer " + handler
                            + " using registerSerializer.registerSerializer. This is not allowed as it can lead to desynchronization issues; use FabricEntityDataRegistry.register instead.");
        }
    }
}
