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
// Plexus Mod Loader (ADR-0031): reformatted; Fabric API 0.165.0+26.3's methods take a TooltipProvider.Getter with the
// component type, which NeoForge 26.3's TooltipAppender.createComponentAppender(type, getter) takes too.

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
    private static final List<TooltipPair<?>> first = new ArrayList<>();
    private static final List<TooltipPair<?>> last = new ArrayList<>();
    private static final Map<DataComponentType<?>, List<TooltipPair<?>>> before = new IdentityHashMap<>();
    private static final Map<DataComponentType<?>, List<TooltipPair<?>>> after = new IdentityHashMap<>();

    private ItemComponentTooltipProviderRegistryImpl() {}

    public static void register(RegisterTooltipAppendersEvent event) {
        for (TooltipPair<?> pair : first) {
            event.registerComponentAppenderBeforeAll(pair.type(), pair.appender());
        }
        for (TooltipPair<?> pair : last) {
            event.registerComponentAppenderAfterAll(pair.type(), pair.appender());
        }
        before.forEach((anchor, pairs) ->
                pairs.forEach(pair -> event.registerComponentAppenderBefore(pair.type(), anchor, pair.appender())));
        after.forEach((anchor, pairs) ->
                pairs.forEach(pair -> event.registerComponentAppenderAfter(pair.type(), anchor, pair.appender())));
    }

    public static <T> void addFirst(DataComponentType<T> componentType, TooltipProvider.Getter<T> getter) {
        first.add(new TooltipPair<>(componentType, getter));
    }

    public static <T> void addLast(DataComponentType<T> componentType, TooltipProvider.Getter<T> getter) {
        last.add(new TooltipPair<>(componentType, getter));
    }

    public static <T> void addBefore(
            DataComponentType<?> anchor, DataComponentType<T> componentType, TooltipProvider.Getter<T> getter) {
        before.computeIfAbsent(anchor, k -> new ArrayList<>()).add(new TooltipPair<>(componentType, getter));
    }

    public static <T> void addAfter(
            DataComponentType<?> anchor, DataComponentType<T> componentType, TooltipProvider.Getter<T> getter) {
        after.computeIfAbsent(anchor, k -> new ArrayList<>()).add(new TooltipPair<>(componentType, getter));
    }

    private record TooltipPair<T>(DataComponentType<T> type, TooltipProvider.Getter<T> getter) {
        TooltipAppender appender() {
            return TooltipAppender.createComponentAppender(this.type, this.getter);
        }
    }
}
