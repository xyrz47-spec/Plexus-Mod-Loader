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
// fabric-item-api-v1/src/main/java/net/fabricmc/fabric/mixin/item/ItemPropertiesMixin.java (Fabric API's file,
// unchanged in Forgified Fabric API).
// Plexus Mod Loader (ADR-0031): reformatted; no other change.

package net.fabricmc.fabric.mixin.item;

import net.fabricmc.fabric.api.item.v1.FabricItem;
import net.minecraft.resources.DependantName;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Item.Properties.class)
public class ItemPropertiesMixin implements FabricItem.Properties {
    @Final
    @Shadow
    @Mutable
    private DependantName<Item, Identifier> model;

    @Shadow
    private @Nullable ResourceKey<Item> id;

    @Override
    public Item.Properties modelId(Identifier modelId) {
        this.model = DependantName.fixed(modelId);
        return FabricItem.Properties.super.modelId(modelId);
    }

    @Override
    public @Nullable ResourceKey<Item> itemId() {
        return this.id;
    }
}
