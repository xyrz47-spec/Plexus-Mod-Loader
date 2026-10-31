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
// fabric-registry-sync-v0/src/main/java/net/fabricmc/fabric/impl/registry/sync/FapiRemapBridge.java (written in
// Forgified Fabric API).
// Merged with Fabric API 0.165.0+26.3's version of the file (FabricMC/fabric, tag 0.165.0+26.3).
// Plexus Mod Loader (ADR-0031): reformatted; no other change.
package net.fabricmc.fabric.impl.registry.sync;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.fabricmc.fabric.api.event.registry.RegistryIdRemapCallback;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.registries.callback.BakeCallback;
import net.neoforged.neoforge.registries.callback.ClearCallback;
import org.jetbrains.annotations.Nullable;

public final class FapiRemapBridge<T> implements ClearCallback<T>, BakeCallback<T> {
    @Nullable
    private Int2ObjectMap<Identifier> oldIdMap;

    @Override
    public void onClear(Registry<T> registry, boolean full) {
        if (full) {
            oldIdMap = null;
            return;
        }
        oldIdMap = new Int2ObjectOpenHashMap<>();
        for (T value : registry) {
            oldIdMap.put(registry.getId(value), registry.getKey(value));
        }
    }

    @Override
    public void onBake(Registry<T> registry) {
        if (oldIdMap == null) {
            return;
        }
        Int2ObjectMap<Identifier> old = oldIdMap;
        oldIdMap = null;

        Object2IntMap<Identifier> newByKey = new Object2IntOpenHashMap<>();
        newByKey.defaultReturnValue(Integer.MIN_VALUE);
        for (T value : registry) {
            newByKey.put(registry.getKey(value), registry.getId(value));
        }

        Int2IntMap rawIdChangeMap = new Int2IntOpenHashMap();
        for (var e : old.int2ObjectEntrySet()) {
            int newId = newByKey.getInt(e.getValue());
            if (newId != Integer.MIN_VALUE) {
                rawIdChangeMap.put(e.getIntKey(), newId);
            }
        }

        RegistryIdRemapCallback.RemapState<T> state = new RemapStateImpl<>(registry, old, rawIdChangeMap);
        RegistryIdRemapCallback.event(registry).invoker().onRemap(state);
    }
}
