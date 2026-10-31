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
// has none: its onInitialize only gives the built-in registries their attributes); RegistryManagerMixin calls
// injectCallbacks; addRegistry tracks a registry with NeoForge only before its
// NewRegistryEvent (Fabric mods may add registries after it).
package net.fabricmc.fabric.impl.registry.sync;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.fabricmc.fabric.api.event.registry.RegistryAttribute;
import net.fabricmc.fabric.api.event.registry.RegistryAttributeHolder;
import net.fabricmc.fabric.api.event.registry.RegistryEntryAddedCallback;
import net.fabricmc.fabric.api.event.registry.RegistryIdRemapCallback;
import net.fabricmc.fabric.mixin.registry.sync.BaseMappedRegistryAccessor;
import net.fabricmc.fabric.mixin.registry.sync.MappedRegistryAccessor;
import net.fabricmc.fabric.mixin.registry.sync.RegistryManagerAccessor;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.ModifyRegistriesEvent;
import net.neoforged.neoforge.registries.callback.AddCallback;

@SuppressWarnings({"unchecked", "rawtypes"})
public class FabricRegistryInit implements ModInitializer {
    private static final Map<Registry<?>, Event<RegistryEntryAddedCallback>> REGISTRY_ENTRY_ADDED_CALLBACKS =
            new ConcurrentHashMap<>();
    private static final Map<Registry<?>, Event<RegistryIdRemapCallback<?>>> REMAP_CALLBACKS = new HashMap<>();

    // Plexus Mod Loader: the registry attributes Fabric API 0.165.0+26.3 gives the built-in registries (SYNCED and
    // SAVE_DATA_VALIDATED, which the save data check reads); NeoForge syncs registries itself, so the payloads and
    // configuration task of upstream's onInitialize are left out.
    @Override
    public void onInitialize() {
        // Synced in ClientboundSoundPacket.
        RegistryAttributeHolder.get(BuiltInRegistries.SOUND_EVENT).addAttribute(RegistryAttribute.SYNCED);

        // Synced with RegistryTagContainer from RegistryTagManager.
        RegistryAttributeHolder.get(BuiltInRegistries.FLUID)
                .addAttribute(RegistryAttribute.SAVE_DATA_VALIDATED)
                .addAttribute(RegistryAttribute.SYNCED);

        // MobEffectInstance serialises with raw id.
        RegistryAttributeHolder.get(BuiltInRegistries.MOB_EFFECT)
                .addAttribute(RegistryAttribute.SAVE_DATA_VALIDATED)
                .addAttribute(RegistryAttribute.SYNCED);

        // Synced in ClientboundSectionBlocksUpdatePacket among other places, a pallet is used when saving.
        RegistryAttributeHolder.get(BuiltInRegistries.BLOCK)
                .addAttribute(RegistryAttribute.SAVE_DATA_VALIDATED)
                .addAttribute(RegistryAttribute.SYNCED);

        // Synced in ClientboundAddEntityPacket and RegistryTagManager
        RegistryAttributeHolder.get(BuiltInRegistries.ENTITY_TYPE)
                .addAttribute(RegistryAttribute.SAVE_DATA_VALIDATED)
                .addAttribute(RegistryAttribute.SYNCED);

        // Synced in RegistryTagManager
        RegistryAttributeHolder.get(BuiltInRegistries.ITEM)
                .addAttribute(RegistryAttribute.SAVE_DATA_VALIDATED)
                .addAttribute(RegistryAttribute.SYNCED);

        // Synced via ByteBufCodecs.registry
        RegistryAttributeHolder.get(BuiltInRegistries.POTION)
                .addAttribute(RegistryAttribute.SAVE_DATA_VALIDATED)
                .addAttribute(RegistryAttribute.SYNCED);

        // Doesnt seem to be accessed apart from registering?
        RegistryAttributeHolder.get(BuiltInRegistries.CARVER_TYPE);

        // Serialised by string, doesnt seem to be synced
        RegistryAttributeHolder.get(BuiltInRegistries.FEATURE_TYPE);

        // Serialised by string, doesnt seem to be synced
        RegistryAttributeHolder.get(BuiltInRegistries.BLOCK_STATE_PROVIDER_TYPE);

        // Serialised by string, doesnt seem to be synced
        RegistryAttributeHolder.get(BuiltInRegistries.FOLIAGE_PLACER_TYPE);

        // Serialised by string, doesnt seem to be synced
        RegistryAttributeHolder.get(BuiltInRegistries.TRUNK_PLACER_TYPE);

        // Serialised by string, doesnt seem to be synced
        RegistryAttributeHolder.get(BuiltInRegistries.TREE_DECORATOR_TYPE);

        // Serialised by string, doesnt seem to be synced
        RegistryAttributeHolder.get(BuiltInRegistries.FEATURE_SIZE_TYPE);

        // Synced in ClientboundLevelParticlesPacket
        RegistryAttributeHolder.get(BuiltInRegistries.PARTICLE_TYPE).addAttribute(RegistryAttribute.SYNCED);

        // Serialised by string, doesnt seem to be synced
        RegistryAttributeHolder.get(BuiltInRegistries.BIOME_SOURCE);

        // Synced. Vanilla uses raw ids in ClientboundBlockEntityDataPacket, and mods use the Vanilla syncing since 1.18
        RegistryAttributeHolder.get(BuiltInRegistries.BLOCK_ENTITY_TYPE)
                .addAttribute(RegistryAttribute.SAVE_DATA_VALIDATED)
                .addAttribute(RegistryAttribute.SYNCED);

        // Synced via ByteBufCodecs.registry
        RegistryAttributeHolder.get(BuiltInRegistries.CUSTOM_STAT).addAttribute(RegistryAttribute.SYNCED);

        // Serialised by string, doesnt seem to be synced
        RegistryAttributeHolder.get(BuiltInRegistries.CHUNK_STATUS);

        // Serialised by string, doesnt seem to be synced
        RegistryAttributeHolder.get(BuiltInRegistries.STRUCTURE_TYPE);

        // Serialised by string, doesnt seem to be synced
        RegistryAttributeHolder.get(BuiltInRegistries.STRUCTURE_PIECE);

        // Serialised by string, doesnt seem to be synced
        RegistryAttributeHolder.get(BuiltInRegistries.RULE_TEST);

        // Serialised by string, doesnt seem to be synced
        RegistryAttributeHolder.get(BuiltInRegistries.POS_RULE_TEST);

        RegistryAttributeHolder.get(BuiltInRegistries.STRUCTURE_PROCESSOR);

        // Serialised by string, doesnt seem to be synced
        RegistryAttributeHolder.get(BuiltInRegistries.STRUCTURE_POOL_ELEMENT);

        //  Uses the raw ID when syncing the command tree to the client
        RegistryAttributeHolder.get(BuiltInRegistries.COMMAND_ARGUMENT_TYPE).addAttribute(RegistryAttribute.SYNCED);

        // Synced in ClientboundOpenScreenPacket
        RegistryAttributeHolder.get(BuiltInRegistries.MENU).addAttribute(RegistryAttribute.SYNCED);

        // Does not seem to be serialised, only queried by id. Not synced
        RegistryAttributeHolder.get(BuiltInRegistries.RECIPE_TYPE);

        // Synced by rawID in 24w03a+
        RegistryAttributeHolder.get(BuiltInRegistries.ATTRIBUTE)
                .addAttribute(RegistryAttribute.SAVE_DATA_VALIDATED)
                .addAttribute(RegistryAttribute.SYNCED);

        // Synced in ClientboundAwardStatsPacket
        RegistryAttributeHolder.get(BuiltInRegistries.STAT_TYPE).addAttribute(RegistryAttribute.SYNCED);

        // Synced by rawID in EntityDataSerializers.VILLAGER_DATA
        RegistryAttributeHolder.get(BuiltInRegistries.VILLAGER_TYPE)
                .addAttribute(RegistryAttribute.SAVE_DATA_VALIDATED)
                .addAttribute(RegistryAttribute.SYNCED);

        // Synced by rawID in EntityDataSerializers.VILLAGER_DATA
        RegistryAttributeHolder.get(BuiltInRegistries.VILLAGER_PROFESSION)
                .addAttribute(RegistryAttribute.SAVE_DATA_VALIDATED)
                .addAttribute(RegistryAttribute.SYNCED);

        // Serialised by string, doesnt seem to be synced
        RegistryAttributeHolder.get(BuiltInRegistries.POINT_OF_INTEREST_TYPE);

        // Serialised by string, doesnt seem to be synced
        RegistryAttributeHolder.get(BuiltInRegistries.MEMORY_MODULE_TYPE);

        // Doesnt seem to be serialised or synced.
        RegistryAttributeHolder.get(BuiltInRegistries.SENSOR_TYPE);

        // Doesnt seem to be serialised or synced.
        RegistryAttributeHolder.get(BuiltInRegistries.ACTIVITY);

        // Doesnt seem to be serialised or synced.
        RegistryAttributeHolder.get(BuiltInRegistries.LOOT_POOL_ENTRY_TYPE);

        // Doesnt seem to be serialised or synced.
        RegistryAttributeHolder.get(BuiltInRegistries.LOOT_FUNCTION_TYPE);

        // Doesnt seem to be serialised or synced.
        RegistryAttributeHolder.get(BuiltInRegistries.LOOT_CONDITION_TYPE);

        // Synced in TagManager::toPacket/fromPacket -> TagGroup::serialize/deserialize
        RegistryAttributeHolder.get(BuiltInRegistries.GAME_EVENT).addAttribute(RegistryAttribute.SYNCED);

        // Synced by rawID in its serialization code.
        RegistryAttributeHolder.get(BuiltInRegistries.NUMBER_FORMAT_TYPE).addAttribute(RegistryAttribute.SYNCED);

        // Synced by rawID.
        RegistryAttributeHolder.get(BuiltInRegistries.POSITION_SOURCE_TYPE).addAttribute(RegistryAttribute.SYNCED);

        // Synced by rawID.
        RegistryAttributeHolder.get(BuiltInRegistries.DATA_COMPONENT_TYPE)
                .addAttribute(RegistryAttribute.SAVE_DATA_VALIDATED)
                .addAttribute(RegistryAttribute.SYNCED);

        // Synced by rawID.
        RegistryAttributeHolder.get(BuiltInRegistries.DATA_COMPONENT_PREDICATE_TYPE)
                .addAttribute(RegistryAttribute.SYNCED);

        // Synced by rawID.
        RegistryAttributeHolder.get(BuiltInRegistries.MAP_DECORATION_TYPE)
                .addAttribute(RegistryAttribute.SAVE_DATA_VALIDATED)
                .addAttribute(RegistryAttribute.SYNCED);

        // Synced via ByteBufCodecs.registry
        RegistryAttributeHolder.get(BuiltInRegistries.CONSUME_EFFECT_TYPE).addAttribute(RegistryAttribute.SYNCED);

        // Synced via ByteBufCodecs.registryValue
        RegistryAttributeHolder.get(BuiltInRegistries.RECIPE_DISPLAY).addAttribute(RegistryAttribute.SYNCED);

        // Synced via ByteBufCodecs.registryValue
        RegistryAttributeHolder.get(BuiltInRegistries.SLOT_DISPLAY).addAttribute(RegistryAttribute.SYNCED);

        // Synced via ByteBufCodecs.registryValue
        RegistryAttributeHolder.get(BuiltInRegistries.RECIPE_BOOK_CATEGORY).addAttribute(RegistryAttribute.SYNCED);

        // Synced via ByteBufCodecs.registryValue
        RegistryAttributeHolder.get(BuiltInRegistries.POINT_OF_INTEREST_TYPE).addAttribute(RegistryAttribute.SYNCED);

        // Synced via ByteBufCodecs.registryValue
        RegistryAttributeHolder.get(BuiltInRegistries.DEBUG_SUBSCRIPTION).addAttribute(RegistryAttribute.SYNCED);

        // Required for save integrity
        RegistryAttributeHolder.get(Registries.BIOME).addAttribute(RegistryAttribute.SAVE_DATA_VALIDATED);

        // Required for save integrity
        RegistryAttributeHolder.get(Registries.ENCHANTMENT).addAttribute(RegistryAttribute.SAVE_DATA_VALIDATED);
    }

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
