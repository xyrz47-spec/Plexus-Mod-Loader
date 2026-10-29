package dev.polymodloader.compat.forge;

import dev.polymodloader.compat.bridge.InjectionPointReplacement;
import java.util.List;

/**
 * Where MinecraftForge's patch moved vanilla calls that other loaders' Mixins inject at (ADR-0021 G8), for {@link
 * ForgeGameHost#injectionPointReplacements()}. Each was checked with {@code javap} against the vanilla and the patched
 * classes of the pinned Forge releases 26.1-62.0.9 and 26.2-65.1.5, and {@code ForgeInjectionPointsTest} checks them
 * against a patched client jar. The bridge that uses them checks the patched classes again before it moves anything.
 */
final class ForgeInjectionPoints {

    private static final String GAME_MODE = "net/minecraft/server/level/ServerPlayerGameMode";
    private static final String WORLD_REGISTRIES =
            "Lnet/minecraft/resources/RegistryDataLoader;WORLD_REGISTRIES:Ljava/util/List;";
    private static final String WORLD_AND_DIMENSION_REGISTRIES =
            "Lnet/minecraft/resources/RegistryDataLoader;getWorldAndDimensionRegistries()Ljava/util/List;";

    private ForgeInjectionPoints() {}

    static List<InjectionPointReplacement> all() {
        return List.of(
                // Bootstrap.bootStrap calls GameData.vanillaSnapshot() at the end where vanilla calls wrapStreams()
                // (Forge wraps the streams elsewhere): the point at which Fabric API's registry synchronisation starts.
                new InjectionPointReplacement(
                        "net/minecraft/server/Bootstrap",
                        "bootStrap",
                        "Lnet/minecraft/server/Bootstrap;wrapStreams()V",
                        "Lnet/minecraftforge/registries/GameData;vanillaSnapshot()V"),
                // ServerPlayerGameMode.destroyBlock: vanilla calls Block.playerWillDestroy after the permission checks,
                // then removes the block. Forge calls it inside BlockState.onDestroyedByPlayer, from its new
                // removeBlock(BlockPos, boolean). The first call after the same checks, before anything changes (the
                // item, the block), is ServerPlayer.preventsBlockDrops(); the locals there (state, blockEntity) are
                // vanilla's. Fabric API's PlayerBlockBreakEvents.BEFORE (cancellable) runs there.
                new InjectionPointReplacement(
                        GAME_MODE,
                        "destroyBlock",
                        "Lnet/minecraft/world/level/block/Block;playerWillDestroy(Lnet/minecraft/world/level/Level;"
                                + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;"
                                + "Lnet/minecraft/world/entity/player/Player;)"
                                + "Lnet/minecraft/world/level/block/state/BlockState;",
                        "Lnet/minecraft/server/level/ServerPlayer;preventsBlockDrops()Z"),
                // Vanilla calls Block.destroy when removeBlock removed the block. Forge's removeBlock(BlockPos, Z)
                // calls it exactly then and returns whether it removed the block; destroyBlock calls it twice
                // (creative and survival). Fabric API's PlayerBlockBreakEvents.AFTER runs after each call that
                // returned true.
                new InjectionPointReplacement(
                        GAME_MODE,
                        "destroyBlock",
                        "Lnet/minecraft/world/level/block/Block;destroy(Lnet/minecraft/world/level/LevelAccessor;"
                                + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V",
                        "L" + GAME_MODE + ";removeBlock(Lnet/minecraft/core/BlockPos;Z)Z",
                        InjectionPointReplacement.Placement.AFTER_TRUE_RESULT),
                // ItemStack.useOn: vanilla calls Item.useOn(UseOnContext) where Fabric API's ItemStackMixin wraps the
                // call for ItemEvents.USE_ON. Forge moves the vanilla call into lambda$useOn$0.
                new InjectionPointReplacement(
                        "net/minecraft/world/item/ItemStack",
                        "useOn",
                        "Lnet/minecraft/world/item/Item;useOn(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;",
                        "lambda$useOn$0(Lnet/minecraft/world/item/context/UseOnContext;Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;",
                        InjectionPointReplacement.Placement.IN_METHOD),
                // Language.loadDefault: vanilla calls Map.copyOf(map) after loading the default translations, where
                // Fabric API's LanguageMixin.create loads Fabric mods' server translations into map. Forge replaces
                // Map.copyOf(map) with LanguageHook.captureLanguageMap(map) and keeps map mutable.
                new InjectionPointReplacement(
                        "net/minecraft/locale/Language",
                        "loadDefault",
                        "Ljava/util/Map;copyOf(Ljava/util/Map;)Ljava/util/Map;",
                        "Lnet/minecraftforge/server/LanguageHook;captureLanguageMap(Ljava/util/Map;)V"),
                // LivingEntity.getBedOrientation: vanilla calls BedBlock.getBedOrientation(BlockGetter, BlockPos).
                // Forge replaces it with BlockState.getBedDirection(LevelReader, BlockPos) for custom beds.
                new InjectionPointReplacement(
                        "net/minecraft/world/entity/LivingEntity",
                        "getBedOrientation",
                        "Lnet/minecraft/world/level/block/BedBlock;getBedOrientation(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/Direction;",
                        "Lnet/minecraft/world/level/block/state/BlockState;getBedDirection(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/Direction;"),
                // LivingEntity.lambda$stopSleeping$0: vanilla calls Level.setBlock to clear OCCUPIED.
                // Forge replaces it with BlockState.setBedOccupied for custom beds.
                new InjectionPointReplacement(
                        "net/minecraft/world/entity/LivingEntity",
                        "lambda$stopSleeping$0",
                        "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z",
                        "Lnet/minecraft/world/level/block/state/BlockState;setBedOccupied(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/LivingEntity;Z)V"),
                // The same on 26.3, where vanilla's lambda$stopSleeping$0 calls Level.setBlockAndUpdate (checked with
                // javap on the 26.3 client and Forge 26.3-66.1.0).
                new InjectionPointReplacement(
                        "net/minecraft/world/entity/LivingEntity",
                        "lambda$stopSleeping$0",
                        "Lnet/minecraft/world/level/Level;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z",
                        "Lnet/minecraft/world/level/block/state/BlockState;setBedOccupied(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/LivingEntity;Z)V"),
                // TagsProvider.lambda$run$5: vanilla saves stable JSON via DataProvider.saveStable.
                // Forge moves this tag-saving lambda to lambda$run$6.
                new InjectionPointReplacement(
                        "net/minecraft/data/tags/TagsProvider",
                        "lambda$run$5",
                        "Lnet/minecraft/data/DataProvider;saveStable(Lnet/minecraft/data/CachedOutput;Lnet/minecraft/core/HolderLookup$Provider;Lcom/mojang/serialization/Codec;Ljava/lang/Object;Ljava/nio/file/Path;)Ljava/util/concurrent/CompletableFuture;",
                        "lambda$run$6",
                        InjectionPointReplacement.Placement.IN_METHOD),
                // TagsProvider.lambda$run$5: vanilla creates TagFile with (List, boolean).
                // Forge moves this to lambda$run$6 and widens TagFile.<init> to (List, boolean, List).
                new InjectionPointReplacement(
                        "net/minecraft/data/tags/TagsProvider",
                        "lambda$run$5",
                        "Lnet/minecraft/tags/TagFile;<init>(Ljava/util/List;Z)V",
                        "lambda$run$6:Lnet/minecraft/tags/TagFile;<init>(Ljava/util/List;ZLjava/util/List;)V",
                        InjectionPointReplacement.Placement.IN_METHOD),
                // Entity.updateSwimming: vanilla checks Entity.isInWater(). Forge widens to isInWaterOrSwimmable().
                new InjectionPointReplacement(
                        "net/minecraft/world/entity/Entity",
                        "updateSwimming",
                        "Lnet/minecraft/world/entity/Entity;isInWater()Z",
                        "Lnet/minecraft/world/entity/Entity;isInWaterOrSwimmable()Z"),
                // Entity.isVisuallyCrawling: vanilla checks Entity.isInWater(). Forge widens to isInWaterOrSwimmable().
                new InjectionPointReplacement(
                        "net/minecraft/world/entity/Entity",
                        "isVisuallyCrawling",
                        "Lnet/minecraft/world/entity/Entity;isInWater()Z",
                        "Lnet/minecraft/world/entity/Entity;isInWaterOrSwimmable()Z"),
                // LivingEntity.travelInFluid: vanilla calls travelInLava directly. Forge moves it to
                // travelInFluid(Vec3, FluidState).
                new InjectionPointReplacement(
                        "net/minecraft/world/entity/LivingEntity",
                        "travelInFluid",
                        "Lnet/minecraft/world/entity/LivingEntity;travelInLava(Lnet/minecraft/world/phys/Vec3;DZD)V",
                        "travelInFluid(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/level/material/FluidState;)V",
                        InjectionPointReplacement.Placement.IN_METHOD),
                // AbstractBoat.checkInWater & isUnderwater: vanilla checks FluidState.is(TagKey). Forge calls
                // canBoatInFluid(FluidState).
                new InjectionPointReplacement(
                        "net/minecraft/world/entity/vehicle/boat/AbstractBoat",
                        "checkInWater",
                        "Lnet/minecraft/world/level/material/FluidState;is(Lnet/minecraft/tags/TagKey;)Z",
                        "Lnet/minecraft/world/entity/vehicle/boat/AbstractBoat;canBoatInFluid(Lnet/minecraft/world/level/material/FluidState;)Z"),
                new InjectionPointReplacement(
                        "net/minecraft/world/entity/vehicle/boat/AbstractBoat",
                        "isUnderwater",
                        "Lnet/minecraft/world/level/material/FluidState;is(Lnet/minecraft/tags/TagKey;)Z",
                        "Lnet/minecraft/world/entity/vehicle/boat/AbstractBoat;canBoatInFluid(Lnet/minecraft/world/level/material/FluidState;)Z"),
                // Entity.updateSwimming: vanilla checks standing FluidState.is(TagKey). Forge calls canStartSwimming().
                new InjectionPointReplacement(
                        "net/minecraft/world/entity/Entity",
                        "updateSwimming",
                        "Lnet/minecraft/world/level/material/FluidState;is(Lnet/minecraft/tags/TagKey;)Z",
                        "Lnet/minecraft/world/entity/Entity;canStartSwimming()Z"),
                // LivingEntity.baseTick: vanilla checks LivingEntity.isEyeInFluid(TagKey). Forge calls
                // ForgeHooks.onLivingBreathe.
                new InjectionPointReplacement(
                        "net/minecraft/world/entity/LivingEntity",
                        "baseTick",
                        "Lnet/minecraft/world/entity/LivingEntity;isEyeInFluid(Lnet/minecraft/tags/TagKey;)Z",
                        "Lnet/minecraftforge/common/ForgeHooks;onLivingBreathe(Lnet/minecraft/world/entity/LivingEntity;II)V"),
                // LivingEntity.aiStep: vanilla calls LivingEntity.jumpInLiquid(TagKey). Forge calls
                // jumpInFluid(FluidType).
                new InjectionPointReplacement(
                        "net/minecraft/world/entity/LivingEntity",
                        "aiStep",
                        "Lnet/minecraft/world/entity/LivingEntity;jumpInLiquid(Lnet/minecraft/tags/TagKey;)V",
                        "Lnet/minecraft/world/entity/LivingEntity;jumpInFluid(Lnet/minecraftforge/fluids/FluidType;)V"),
                // AbstractFurnaceBlockEntity.consumeFuel: vanilla calls Item.getCraftingRemainder(). Forge calls
                // ItemStack.getCraftingRemainder().
                new InjectionPointReplacement(
                        "net/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity",
                        "consumeFuel",
                        "Lnet/minecraft/world/item/Item;getCraftingRemainder()Lnet/minecraft/world/item/ItemStackTemplate;",
                        "Lnet/minecraft/world/item/ItemStack;getCraftingRemainder()Lnet/minecraft/world/item/ItemStackTemplate;"),
                // BrewingStandBlockEntity.doBrew: vanilla calls Item.getCraftingRemainder(). Forge calls
                // ItemStack.getCraftingRemainder().
                new InjectionPointReplacement(
                        "net/minecraft/world/level/block/entity/BrewingStandBlockEntity",
                        "doBrew",
                        "Lnet/minecraft/world/item/Item;getCraftingRemainder()Lnet/minecraft/world/item/ItemStackTemplate;",
                        "Lnet/minecraft/world/item/ItemStack;getCraftingRemainder()Lnet/minecraft/world/item/ItemStackTemplate;"),
                // CraftingRecipe.defaultCraftingReminder: vanilla calls ItemStack.getItem() and
                // Item.getCraftingRemainder(). Forge calls ItemStack.getCraftingRemainder().
                new InjectionPointReplacement(
                        "net/minecraft/world/item/crafting/CraftingRecipe",
                        "defaultCraftingReminder",
                        "Lnet/minecraft/world/item/ItemStack;getItem()Lnet/minecraft/world/item/Item;",
                        "Lnet/minecraft/world/item/ItemStack;getCraftingRemainder()Lnet/minecraft/world/item/ItemStackTemplate;"),
                new InjectionPointReplacement(
                        "net/minecraft/world/item/crafting/CraftingRecipe",
                        "defaultCraftingReminder",
                        "Lnet/minecraft/world/item/Item;getCraftingRemainder()Lnet/minecraft/world/item/ItemStackTemplate;",
                        "Lnet/minecraft/world/item/ItemStack;getCraftingRemainder()Lnet/minecraft/world/item/ItemStackTemplate;"),
                // EnchantmentHelper.lambda$getAvailableEnchantmentResults$0: vanilla calls
                // Enchantment.isPrimaryItem(ItemStack). Forge calls ItemStack.canApplyAtEnchantingTable(Holder).
                new InjectionPointReplacement(
                        "net/minecraft/world/item/enchantment/EnchantmentHelper",
                        "lambda$getAvailableEnchantmentResults$0",
                        "Lnet/minecraft/world/item/enchantment/Enchantment;isPrimaryItem(Lnet/minecraft/world/item/ItemStack;)Z",
                        "Lnet/minecraft/world/item/ItemStack;canApplyAtEnchantingTable(Lnet/minecraft/core/Holder;)Z"),
                // ServerPlayer.drop(boolean): vanilla drops the stack it removed from the selected slot with
                // drop(ItemStack, boolean, boolean). Forge calls ForgeHooks.onPlayerTossEvent(player, removed, true)
                // there, which makes that call itself (through Player.drop) and then fires ItemTossEvent; what comes
                // before (removing the stack, stopping item use) is vanilla's. Puzzles Lib's item toss event
                // (cancellable) runs before it. Forge also keeps the selected stack in a new local of the same type;
                // HostRenamedLocals makes an implicit @Local there capture vanilla's `removed` by name. This and the
                // next point were checked against 26.2-65.1.5 only; the bridge checks the patched class anyway.
                new InjectionPointReplacement(
                        "net/minecraft/server/level/ServerPlayer",
                        "drop",
                        "Lnet/minecraft/server/level/ServerPlayer;drop(Lnet/minecraft/world/item/ItemStack;ZZ)"
                                + "Lnet/minecraft/world/entity/item/ItemEntity;",
                        "Lnet/minecraftforge/common/ForgeHooks;onPlayerTossEvent(Lnet/minecraft/world/entity/player/"
                                + "Player;Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/entity/item/"
                                + "ItemEntity;"),
                // PersistentEntitySectionManager.addEntity(EntityAccess, boolean): Forge fires EntityJoinLevelEvent
                // and moves the vanilla body unchanged into addEntityWithoutEvent of the same descriptor (also called
                // by Forge's addNewEntityWithoutEvent, for ServerLevel.addPlayer). Architectury's entity add event
                // (cancellable, returning false) runs there before SectionPos.asLong.
                new InjectionPointReplacement(
                        "net/minecraft/world/level/entity/PersistentEntitySectionManager",
                        "addEntity",
                        "Lnet/minecraft/core/SectionPos;asLong(Lnet/minecraft/core/BlockPos;)J",
                        "addEntityWithoutEvent(Lnet/minecraft/world/level/entity/EntityAccess;Z)Z",
                        InjectionPointReplacement.Placement.IN_METHOD),
                // RegistryPatchGenerator.lambda$createLookup$0: vanilla reads RegistryDataLoader.WORLDGEN_REGISTRIES.
                // Forge calls RegistryDataLoader.getWorldGenAndDimensionStream().
                new InjectionPointReplacement(
                        "net/minecraft/data/registries/RegistryPatchGenerator",
                        "lambda$createLookup$0",
                        "Lnet/minecraft/resources/RegistryDataLoader;WORLDGEN_REGISTRIES:Ljava/util/List;",
                        "Lnet/minecraft/resources/RegistryDataLoader;getWorldGenAndDimensionStream()Ljava/util/stream/Stream;"),
                // FishingHook.shouldStopFishing (Forge 26.2-65.1.5, 26.3-66.1.0): vanilla keeps the hook while either
                // hand holds
                // ItemStack.is(Items.FISHING_ROD); Forge asks ItemStack.canPerformAction(ToolActions.FISHING_ROD_CAST)
                // at the same two places. Fabric API's allowModdedRods wraps the vanilla check.
                new InjectionPointReplacement(
                        "net/minecraft/world/entity/projectile/FishingHook",
                        "shouldStopFishing",
                        "Lnet/minecraft/world/item/ItemStack;is(Ljava/lang/Object;)Z",
                        "Lnet/minecraft/world/item/ItemStack;canPerformAction(Lnet/minecraftforge/common/ToolAction;)Z"),
                // Mob.setTarget (checked on Forge 26.2-65.1.5 and 26.3-66.1.0): vanilla assigns target =
                // asValidTarget(target); Forge assigns
                // ForgeEventFactory.onLivingChangeTargetMob(this, target).getNewTarget() when the event is not
                // cancelled, and does not call asValidTarget there. The value assigned is the event's new target, so a
                // @ModifyExpressionValue of the vanilla call (Puzzles Lib 26.3's change-target event) applies to it.
                new InjectionPointReplacement(
                        "net/minecraft/world/entity/Mob",
                        "setTarget",
                        "Lnet/minecraft/world/entity/Mob;asValidTarget(Lnet/minecraft/world/entity/LivingEntity;)"
                                + "Lnet/minecraft/world/entity/LivingEntity;",
                        "Lnet/minecraftforge/event/entity/living/LivingChangeTargetEvent;getNewTarget()"
                                + "Lnet/minecraft/world/entity/LivingEntity;"),
                // Forge 26.3-66.1.0 only: RegistriesDatapackGenerator.forWorldLayer and
                // RegistryPatchGenerator.lambda$createWorldLookup$0 read RegistryDataLoader.WORLD_REGISTRIES in
                // vanilla;
                // Forge calls getWorldAndDimensionRegistries(), which returns WORLD_REGISTRIES followed by
                // DIMENSION_REGISTRIES. Fabric API's registry sync redirects both reads to its own world registries.
                new InjectionPointReplacement(
                        "net/minecraft/data/registries/RegistriesDatapackGenerator",
                        "forWorldLayer",
                        WORLD_REGISTRIES,
                        WORLD_AND_DIMENSION_REGISTRIES,
                        InjectionPointReplacement.Placement.FIELD_THEN_HOST_ENTRIES),
                new InjectionPointReplacement(
                        "net/minecraft/data/registries/RegistryPatchGenerator",
                        "lambda$createWorldLookup$0",
                        WORLD_REGISTRIES,
                        WORLD_AND_DIMENSION_REGISTRIES,
                        InjectionPointReplacement.Placement.FIELD_THEN_HOST_ENTRIES));
    }
}
