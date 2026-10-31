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
// fabric-object-builder-api-v1/src/main/java/net/fabricmc/fabric/impl/object/builder/FabricDefaultAttributeRegistryImpl.java (Fabric API's
// file as Forgified Fabric API has it).
// Merged with Fabric API 0.165.0+26.3's version of the file (FabricMC/fabric, tag 0.165.0+26.3).
// Plexus Mod Loader (ADR-0031): reformatted; invokeModify runs from GameDataMixin, and a modification goes to the map
// DefaultAttributes.getSupplier reads first (NeoForge's attributes, then the vanilla ones).
package net.fabricmc.fabric.impl.object.builder;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.mixin.object.builder.CommonHooksAccessor;
import net.fabricmc.fabric.mixin.object.builder.DefaultAttributesAccessor;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;

public final class FabricDefaultAttributeRegistryImpl {
    // a single bulk modification is applied when the registries are frozen since at that point
    // everything should be registered (next to NeoForge: GameDataMixin, after NeoForge's registration and Fabric's mod
    // initialisation)
    public static void invokeModify() {
        FabricDefaultAttributeRegistry.MODIFY.invoker().modify(new ModifyContextImpl());
    }

    static class ModifyContextImpl implements FabricDefaultAttributeRegistry.ModifyContext {
        @Override
        public void modify(
                Predicate<EntityType<? extends LivingEntity>> entityTypePredicate,
                FabricDefaultAttributeRegistry.ModifyConsumer consumer) {
            // DefaultAttributes.getSupplier reads NeoForge's map first: a type there gets its modified supplier there.
            Map<EntityType<? extends LivingEntity>, AttributeSupplier> vanilla =
                    DefaultAttributesAccessor.getRegistry();
            Map<EntityType<? extends LivingEntity>, AttributeSupplier> neoForge =
                    CommonHooksAccessor.getForgeAttributes();
            Set<EntityType<? extends LivingEntity>> types = new LinkedHashSet<>(vanilla.keySet());
            types.addAll(neoForge.keySet());
            for (EntityType<? extends LivingEntity> type : types) {
                if (entityTypePredicate.test(type)) {
                    Map<EntityType<? extends LivingEntity>, AttributeSupplier> owner =
                            neoForge.containsKey(type) ? neoForge : vanilla;
                    AttributeSupplier.Builder builder = new AttributeSupplier.Builder(owner.get(type));
                    consumer.accept(type, builder);
                    owner.put(type, builder.build());
                }
            }
        }
    }

    private FabricDefaultAttributeRegistryImpl() {}
}
