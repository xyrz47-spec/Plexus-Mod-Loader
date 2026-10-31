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
// fabric-item-api-v1/src/main/java/net/fabricmc/fabric/impl/item/ResourceUtil.java.
// Plexus Mod Loader (ADR-0031): reformatted; no other change.
package net.fabricmc.fabric.impl.item;

import net.fabricmc.fabric.api.item.v1.ResourceSource;
import net.fabricmc.fabric.api.resource.v1.FabricResource;
import net.fabricmc.fabric.impl.resource.pack.BuiltinModPackSource;
import net.fabricmc.fabric.impl.resource.pack.ModResourcePackCreator;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.Resource;

public interface ResourceUtil {
    static ResourceSource determineSource(Resource resource) {
        if (resource != null) {
            PackSource packSource = ((FabricResource) resource).getFabricPackSource();

            if (packSource == PackSource.BUILT_IN) {
                return ResourceSource.VANILLA;
            } else if (packSource == ModResourcePackCreator.RESOURCE_PACK_SOURCE
                    || packSource instanceof BuiltinModPackSource
                    // NeoForge's mods' data packs (Forgified Fabric API's condition in EnchantmentUtil).
                    || resource.knownPackInfo().map(p -> !p.isVanilla()).orElse(false)) {
                return ResourceSource.MOD;
            }
        }

        // If not built-in or mod, assume external data pack.
        // It might also be injected via mixin instead of being loaded from a resource,
        // but we can't determine that here.
        return ResourceSource.DATA_PACK;
    }
}
