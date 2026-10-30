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
// fabric-item-api-v1/src/main/java/net/fabricmc/fabric/impl/item/FabricItemInternals.java (Fabric API's file, unchanged
// in Forgified Fabric API).
// Poly Mod Loader (ADR-0031): reformatted; no other change.

package net.fabricmc.fabric.impl.item;

import java.util.WeakHashMap;
import net.fabricmc.fabric.api.item.v1.CustomDamageHandler;
import net.fabricmc.fabric.api.item.v1.EquipmentSlotProvider;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;

public final class FabricItemInternals {
    private static final WeakHashMap<Item.Properties, ExtraData> extraData = new WeakHashMap<>();

    private FabricItemInternals() {}

    public static ExtraData computeExtraData(Item.Properties properties) {
        return extraData.computeIfAbsent(properties, s -> new ExtraData());
    }

    public static void onBuild(Item.Properties properties, Item item) {
        ExtraData data = extraData.get(properties);

        if (data != null) {
            ((ItemExtensions) item).fabric_setEquipmentSlotProvider(data.equipmentSlotProvider);
            ((ItemExtensions) item).fabric_setCustomDamageHandler(data.customDamageHandler);
        }
    }

    public static final class ExtraData {
        private @Nullable EquipmentSlotProvider equipmentSlotProvider;
        private @Nullable CustomDamageHandler customDamageHandler;

        public void equipmentSlot(EquipmentSlotProvider equipmentSlotProvider) {
            this.equipmentSlotProvider = equipmentSlotProvider;
        }

        public void customDamage(CustomDamageHandler handler) {
            this.customDamageHandler = handler;
        }
    }
}
