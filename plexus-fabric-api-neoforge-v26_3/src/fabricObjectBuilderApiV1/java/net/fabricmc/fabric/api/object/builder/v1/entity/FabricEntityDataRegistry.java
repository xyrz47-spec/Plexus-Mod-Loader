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
// fabric-object-builder-api-v1/src/main/java/net/fabricmc/fabric/api/object/builder/v1/entity/FabricEntityDataRegistry.java (Fabric API's
// file as Forgified Fabric API has it).
// Merged with Fabric API 0.165.0+26.3's version of the file (FabricMC/fabric, tag 0.165.0+26.3).
// Plexus Mod Loader (ADR-0031): reformatted; no other change.
package net.fabricmc.fabric.api.object.builder.v1.entity;

import net.fabricmc.fabric.impl.object.builder.FabricEntityDataRegistryImpl;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Allows registering custom {@link EntityDataSerializer}s in a reliable way.
 */
public final class FabricEntityDataRegistry {
    private FabricEntityDataRegistry() {}

    /**
     * Registers a {@link EntityDataSerializer} using the given ID. Use this instead of
     * {@link EntityDataSerializers#registerSerializer(EntityDataSerializer)} as the vanilla method exclusively uses integer
     * IDs, which can result in desyncs and errors with custom handlers. This method is guaranteed to work reliably.
     *
     * <p>Handlers registered with this method will have an associated integer ID as well, which can be used with
     * {@link EntityDataSerializers#getSerializer(int)} and {@link EntityDataSerializers#getSerializedId(EntityDataSerializer)}.
     * However, the integer ID of a given custom handler registered through this method may change on registry sync.
     * The integer IDs of vanilla handlers are guaranteed to remain constant.
     */
    public static void register(Identifier id, EntityDataSerializer<?> handler) {
        FabricEntityDataRegistryImpl.register(id, handler);
    }

    /**
     * Retrieves the handler for the given ID, or {@code null} if it does not exist.
     */
    @Nullable
    public static EntityDataSerializer<?> get(Identifier id) {
        return FabricEntityDataRegistryImpl.get(id);
    }

    /**
     * Retrieves the ID for the given handler, or {@code null} if the handler was not registered with
     * {@link #register(Identifier, EntityDataSerializer)}.
     */
    @Nullable
    public static Identifier getId(EntityDataSerializer<?> handler) {
        return FabricEntityDataRegistryImpl.getId(handler);
    }
}
