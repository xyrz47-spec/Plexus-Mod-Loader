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
// fabric-registry-sync-v0/src/main/java/net/fabricmc/fabric/mixin/registry/sync/RegistryManagerAccessor.java (written
// in Forgified Fabric API).
// Plexus Mod Loader (ADR-0031): reformatted; adds the pendingModdedRegistries accessor, for FabricRegistryInit.
package net.fabricmc.fabric.mixin.registry.sync;

import java.util.Set;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.registries.RegistryManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RegistryManager.class)
public interface RegistryManagerAccessor {
    @Invoker
    static void invokeTrackModdedRegistry(Identifier registry) {
        throw new UnsupportedOperationException();
    }

    // null once NeoForge's NewRegistryEvent has been posted.
    @Accessor("pendingModdedRegistries")
    static Set<Identifier> getPendingModdedRegistries() {
        throw new UnsupportedOperationException();
    }
}
