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
// fabric-item-api-v1/src/main/java/net/fabricmc/fabric/impl/item/ItemComponentTooltipProviderRegistryImpl.java (Fabric
// API's file as Forgified Fabric API changed it for NeoForge).
// Poly Mod Loader (ADR-0031): reformatted; no other change.

package net.fabricmc.fabric.impl.item;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.component.TooltipProvider;
import net.neoforged.neoforge.common.tooltip.TooltipAppender;
import net.neoforged.neoforge.event.RegisterTooltipAppendersEvent;

public final class ItemComponentTooltipProviderRegistryImpl {
    private static final List<DataComponentType<? extends TooltipProvider>> first = new ArrayList<>();
    private static final List<DataComponentType<? extends TooltipProvider>> last = new ArrayList<>();
    private static final Map<DataComponentType<?>, List<DataComponentType<? extends TooltipProvider>>> before =
            new IdentityHashMap<>();
    private static final Map<DataComponentType<?>, List<DataComponentType<? extends TooltipProvider>>> after =
            new IdentityHashMap<>();

    public static void register(RegisterTooltipAppendersEvent event) {
        for (DataComponentType<? extends TooltipProvider> type : first) {
            event.registerComponentAppenderBeforeAll(type, TooltipAppender.createComponentAppender(type));
        }
        for (DataComponentType<? extends TooltipProvider> type : last) {
            event.registerComponentAppenderAfterAll(type, TooltipAppender.createComponentAppender(type));
        }
        before.forEach((type, others) -> others.forEach(other ->
                event.registerComponentAppenderBefore(other, type, TooltipAppender.createComponentAppender(other))));
        after.forEach((type, others) -> others.forEach(other ->
                event.registerComponentAppenderAfter(other, type, TooltipAppender.createComponentAppender(other))));
    }

    public static void addFirst(DataComponentType<? extends TooltipProvider> componentType) {
        first.add(componentType);
    }

    public static void addLast(DataComponentType<? extends TooltipProvider> componentType) {
        last.add(componentType);
    }

    public static void addBefore(
            DataComponentType<?> anchor, DataComponentType<? extends TooltipProvider> componentType) {
        before.computeIfAbsent(anchor, k -> new ArrayList<>()).add(componentType);
    }

    public static void addAfter(
            DataComponentType<?> anchor, DataComponentType<? extends TooltipProvider> componentType) {
        after.computeIfAbsent(anchor, k -> new ArrayList<>()).add(componentType);
    }
}
