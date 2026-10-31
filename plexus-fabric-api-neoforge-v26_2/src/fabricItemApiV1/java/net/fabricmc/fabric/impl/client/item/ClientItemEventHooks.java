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
// fabric-item-api-v1/src/client/java/net/fabricmc/fabric/impl/client/item/ClientItemEventHooks.java (written in
// Forgified Fabric API).
// Plexus Mod Loader (ADR-0031): reformatted; a Fabric ClientModInitializer that adds the listener to NeoForge.EVENT_BUS
// in place of @EventBusSubscriber.
package net.fabricmc.fabric.impl.client.item;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

public class ClientItemEventHooks implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // FML subscribes only NeoForge mods' @EventBusSubscriber classes; this module runs as a Fabric mod.
        NeoForge.EVENT_BUS.addListener(ClientItemEventHooks::onItemTooltip);
    }

    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemTooltipCallback.EVENT
                .invoker()
                .getTooltip(event.getItemStack(), event.getContext(), event.getFlags(), event.getToolTip());
    }
}
