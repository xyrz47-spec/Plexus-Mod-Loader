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
// fabric-registry-sync-v0/src/main/java/net/fabricmc/fabric/impl/registry/sync/RegistryAttributeImpl.java (Fabric API's
// file as Forgified Fabric API has it).
// Merged with Fabric API 0.165.0+26.3's version of the file (FabricMC/fabric, tag 0.165.0+26.3).
// Plexus Mod Loader (ADR-0031): reformatted; no other change.
package net.fabricmc.fabric.impl.registry.sync;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.registry.RegistryAttribute;
import net.fabricmc.fabric.api.event.registry.RegistryAttributeHolder;
import net.fabricmc.fabric.mixin.registry.sync.BaseMappedRegistryAccessor;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;

public final class RegistryAttributeImpl implements RegistryAttributeHolder {
    private static final Map<ResourceKey<?>, RegistryAttributeHolder> HOLDER_MAP = new ConcurrentHashMap<>();

    public static RegistryAttributeHolder getHolder(ResourceKey<?> registryKey) {
        return HOLDER_MAP.computeIfAbsent(registryKey, RegistryAttributeImpl::new);
    }

    private final ResourceKey<?> key;

    private RegistryAttributeImpl(ResourceKey<?> key) {
        this.key = key;
    }

    @Override
    public RegistryAttributeHolder addAttribute(RegistryAttribute attribute) {
        if (attribute == RegistryAttribute.SYNCED) {
            Registry<?> registry = BuiltInRegistries.REGISTRY.getValue((ResourceKey) this.key);
            ((BaseMappedRegistryAccessor) registry).invokeSetSync(true);
        }
        return this;
    }

    @Override
    public boolean hasAttribute(RegistryAttribute attribute) {
        if (attribute == RegistryAttribute.SYNCED) {
            return BuiltInRegistries.REGISTRY.getValue((ResourceKey) this.key).doesSync();
        }
        return attribute == RegistryAttribute.MODDED;
    }
}
