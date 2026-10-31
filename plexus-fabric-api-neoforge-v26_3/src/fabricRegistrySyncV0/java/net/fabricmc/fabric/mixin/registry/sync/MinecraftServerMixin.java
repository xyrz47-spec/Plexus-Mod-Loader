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
// Source: Fabric API (FabricMC/fabric), tag 0.165.0+26.3,
// fabric-registry-sync-v0/src/main/java/net/fabricmc/fabric/mixin/registry/sync/MinecraftServerMixin.java.
// Plexus Mod Loader (ADR-0031): reformatted; no other change.
package net.fabricmc.fabric.mixin.registry.sync;

import com.mojang.datafixers.DataFixer;
import java.net.Proxy;
import java.util.Optional;
import net.fabricmc.fabric.impl.registry.sync.validate.RegistryCustomContentState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.Services;
import net.minecraft.server.WorldStem;
import net.minecraft.server.level.progress.LevelLoadListener;
import net.minecraft.server.notifications.NotificationManager;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
class MinecraftServerMixin {
    @Inject(
            method = "<init>",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/level/storage/SavedDataStorage;set(Lnet/minecraft/world/level/saveddata/SavedDataType;Lnet/minecraft/world/level/saveddata/SavedData;)V",
                            ordinal = 0))
    private void saveRegistryEntryInfo(
            Thread serverThread,
            LevelStorageSource.LevelStorageAccess storageSource,
            PackRepository packRepository,
            WorldStem worldStem,
            Optional gameRules,
            Proxy proxy,
            DataFixer fixerUpper,
            Services services,
            LevelLoadListener levelLoadListener,
            boolean propagatesCrashes,
            NotificationManager notificationManager,
            CallbackInfo ci) {
        RegistryCustomContentState.writeIfNeeded(
                storageSource, worldStem.registries().compositeAccess());
    }
}
