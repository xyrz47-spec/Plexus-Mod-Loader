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
// fabric-registry-sync-v0/src/main/java/net/fabricmc/fabric/impl/registry/sync/DynamicRegistriesImpl.java (Fabric API's
// file as Forgified Fabric API has it), with Fabric API 0.165.0+26.3's methods (FabricMC/fabric, tag 0.165.0+26.3).
// Plexus Mod Loader (ADR-0031): reformatted; onNewDatapackRegistries is public, for RegistryManagerMixin; NeoForge
// 26.3's NewDatapackRegistryEvent takes world and reloadable registries (registerReloadable); the registry lists are
// the game's, which NeoForge's patch fills with every loader's registries (DataPackRegistriesHooks.capture*).
package net.fabricmc.fabric.impl.registry.sync;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.event.registry.DynamicRegistries;
import net.fabricmc.fabric.mixin.registry.sync.NewDatapackRegistryEventAccessor;
import net.minecraft.core.Registry;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.DataPackRegistriesHooks;
import net.neoforged.neoforge.registries.NewDatapackRegistryEvent;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.Nullable;

public final class DynamicRegistriesImpl {
    private static final List<PendingRegistry<?>> PENDING_REGISTRIES = new ArrayList<>();
    public static final Set<ResourceKey<?>> FABRIC_DYNAMIC_REGISTRY_KEYS = new HashSet<>();

    private static boolean initialized;

    private DynamicRegistriesImpl() {}

    public static @Unmodifiable List<RegistryDataLoader.RegistryData<?>> getAllDynamicRegistries() {
        return Stream.concat(
                        DataPackRegistriesHooks.getWorldRegistriesWithDimensions(),
                        RegistryDataLoader.RELOADABLE_REGISTRIES.stream())
                .toList();
    }

    public static @Unmodifiable List<RegistryDataLoader.RegistryData<?>> getWorldRegistries() {
        return List.copyOf(RegistryDataLoader.WORLD_REGISTRIES);
    }

    public static @Unmodifiable List<RegistryDataLoader.RegistryData<?>> getReloadableRegistries() {
        return List.copyOf(RegistryDataLoader.RELOADABLE_REGISTRIES);
    }

    public static <T> void register(ResourceKey<? extends Registry<T>> key, Codec<T> serverCodec) {
        register(Kind.WORLD, key, serverCodec, null);
    }

    public static <T> void registerSynced(
            ResourceKey<? extends Registry<T>> key,
            Codec<T> serverCodec,
            Codec<T> clientCodec,
            DynamicRegistries.SyncOption... options) {
        Objects.requireNonNull(clientCodec, "Client codec cannot be null");
        Objects.requireNonNull(options, "Options cannot be null");

        register(Kind.WORLD, key, serverCodec, clientCodec);
    }

    public static <T> void registerReloadable(ResourceKey<? extends Registry<T>> key, Codec<T> serverCodec) {
        register(Kind.RELOADABLE, key, serverCodec, null);
    }

    private static synchronized <T> void register(
            Kind kind, ResourceKey<? extends Registry<T>> key, Codec<T> serverCodec, @Nullable Codec<T> networkCodec) {
        Objects.requireNonNull(key, "Registry key cannot be null");
        Objects.requireNonNull(serverCodec, "Server codec cannot be null");

        if (getAllDynamicRegistries().stream().anyMatch(d -> d.key().equals(key))
                || FABRIC_DYNAMIC_REGISTRY_KEYS.contains(key)) {
            throw new IllegalArgumentException("Dynamic registry " + key + " has already been registered!");
        }

        FABRIC_DYNAMIC_REGISTRY_KEYS.add(key);
        PendingRegistry<T> registry = new PendingRegistry<>(kind, key, serverCodec, networkCodec);

        if (initialized) {
            NewDatapackRegistryEvent event = NewDatapackRegistryEventAccessor.create();
            registry.register(event);
            ((NewDatapackRegistryEventAccessor) (Object) event).invokeProcess();
        } else {
            PENDING_REGISTRIES.add(registry);
        }
    }

    public static synchronized void onNewDatapackRegistries(NewDatapackRegistryEvent event) {
        for (PendingRegistry<?> registry : PENDING_REGISTRIES) {
            registry.register(event);
        }

        PENDING_REGISTRIES.clear();
        initialized = true;
    }

    private enum Kind {
        WORLD,
        RELOADABLE
    }

    private record PendingRegistry<T>(
            Kind kind,
            ResourceKey<? extends Registry<T>> key,
            Codec<T> serverCodec,
            @Nullable Codec<T> networkCodec) {
        @SuppressWarnings("unchecked")
        void register(NewDatapackRegistryEvent event) {
            ResourceKey<Registry<T>> registryKey = (ResourceKey<Registry<T>>) this.key;
            if (this.kind == Kind.RELOADABLE) {
                event.reloadableRegistry(registryKey, this.serverCodec);
            } else if (this.networkCodec == null) {
                event.worldRegistry(registryKey, this.serverCodec);
            } else {
                event.worldRegistry(registryKey, this.serverCodec, this.networkCodec);
            }
        }
    }
}
