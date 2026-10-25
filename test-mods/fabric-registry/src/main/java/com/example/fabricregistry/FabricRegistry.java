package com.example.fabricregistry;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

/**
 * Registers an item straight into the game's registry from {@code onInitialize}, as Fabric mods do. Under NeoForge and
 * Forge the registries are open only while their own registration runs; Poly Mod Loader opens them for Fabric mods'
 * initialization (ADR-0026 31, ADR-0021 G8).
 */
public final class FabricRegistry implements ModInitializer {

    public static final ResourceKey<Item> ITEM_KEY =
            ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("fabric_registry", "fabric_item"));

    @Override
    public void onInitialize() {
        Item item =
                Registry.register(BuiltInRegistries.ITEM, ITEM_KEY, new Item(new Item.Properties().setId(ITEM_KEY)));
        System.out.println("[fabric_registry] onInitialize registered item " + BuiltInRegistries.ITEM.getKey(item)
                + " (raw id assigned: " + (BuiltInRegistries.ITEM.getId(item) >= 0) + ")");
    }
}
