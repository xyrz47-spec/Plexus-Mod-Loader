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
// fabric-registry-sync-v0/src/main/java/net/fabricmc/fabric/impl/registry/sync/FabricRegistryInit.java (Fabric API's
// file as Forgified Fabric API has it).
// Plexus Mod Loader (ADR-0031): reformatted; no longer a ModInitializer listening on a NeoForge mod bus (this module
// has none); RegistryManagerMixin calls injectCallbacks; addRegistry tracks a registry with NeoForge only before its
// NewRegistryEvent (Fabric mods may add registries after it).
package net.fabricmc.fabric.impl.registry.sync;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.fabricmc.fabric.api.event.registry.RegistryEntryAddedCallback;
import net.fabricmc.fabric.api.event.registry.RegistryIdRemapCallback;
import net.fabricmc.fabric.mixin.registry.sync.BaseMappedRegistryAccessor;
import net.fabricmc.fabric.mixin.registry.sync.MappedRegistryAccessor;
import net.fabricmc.fabric.mixin.registry.sync.RegistryManagerAccessor;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.ModifyRegistriesEvent;
import net.neoforged.neoforge.registries.callback.AddCallback;

@SuppressWarnings({"unchecked", "rawtypes"})
public class FabricRegistryInit {
    private static final Map<Registry<?>, Event<RegistryEntryAddedCallback>> REGISTRY_ENTRY_ADDED_CALLBACKS =
            new ConcurrentHashMap<>();
    private static final Map<Registry<?>, Event<RegistryIdRemapCallback<?>>> REMAP_CALLBACKS = new HashMap<>();

    public static void injectCallbacks(ModifyRegistriesEvent event) {
        event.getRegistries().forEach(registry -> {
            getRemapCallbackEvent(registry);
            registry.addCallback(new FapiRemapBridge<>());
        });
    }

    public static <T> Event<RegistryIdRemapCallback<T>> getRemapCallbackEvent(Registry<T> registry) {
        return (Event) REMAP_CALLBACKS.computeIfAbsent(
                registry,
                r -> EventFactory.createArrayBacked(RegistryIdRemapCallback.class, callbacks -> a -> {
                    for (RegistryIdRemapCallback callback : callbacks) {
                        callback.onRemap(a);
                    }
                }));
    }

    public static <T> Event<RegistryEntryAddedCallback<T>> objectAddedEvent(Registry<T> registry) {
        return (Event<RegistryEntryAddedCallback<T>>) (Object) REGISTRY_ENTRY_ADDED_CALLBACKS.computeIfAbsent(
                registry, k -> {
                    Event<RegistryEntryAddedCallback> event = EventFactory.createArrayBacked(
                            RegistryEntryAddedCallback.class, callbacks -> (rawId, id, object) -> {
                                for (RegistryEntryAddedCallback callback : callbacks) {
                                    callback.onEntryAdded(rawId, id, object);
                                }
                            });
                    k.addCallback(
                            AddCallback.class,
                            (reg, id, key, val) -> event.invoker().onEntryAdded(id, key.identifier(), val));
                    return event;
                });
    }

    public static void addRegistry(Registry<?> registry) {
        // NeoForge tracks a modded registry only until its NewRegistryEvent has checked that each one got registered;
        // Fabric mods initialise after that event when NeoForge registers first (ADR-0026 31), and this method
        // registers the registry right away.
        if (RegistryManagerAccessor.getPendingModdedRegistries() != null) {
            RegistryManagerAccessor.invokeTrackModdedRegistry(registry.key().identifier());
        }

        boolean frozen = ((MappedRegistryAccessor) BuiltInRegistries.REGISTRY).getFrozen();
        if (frozen) {
            ((BaseMappedRegistryAccessor) BuiltInRegistries.REGISTRY).invokeUnfreeze(false);
        }

        ((WritableRegistry) BuiltInRegistries.REGISTRY).register(registry.key(), registry, RegistrationInfo.BUILT_IN);

        if (frozen) {
            ((WritableRegistry<?>) BuiltInRegistries.REGISTRY).freeze();
        }
    }
}
