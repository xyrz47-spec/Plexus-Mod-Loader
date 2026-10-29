package dev.polymodloader.compat.forge;

import dev.polymodloader.compat.bridge.InjectionPointReplacement;
import java.util.List;

/**
 * Where NeoForge's patch moved vanilla calls that other loaders' Mixins inject at (ADR-0026, ADR-0030), for {@link
 * NeoForgeGameHost#injectionPointReplacements()}. Each was checked with {@code javap} against the vanilla and the
 * patched classes of the pinned NeoForge releases, and the bridge that uses them checks the patched classes again
 * before it moves anything.
 */
final class NeoForgeInjectionPoints {

    private static final String HOPPER = "net/minecraft/world/level/block/entity/HopperBlockEntity";
    private static final String CONTAINER_OR_HANDLER = "Lnet/neoforged/neoforge/transfer/item/ContainerOrHandler;";
    private static final String SOURCE_LOOKUP = "Lnet/minecraft/world/level/Level;"
            + "Lnet/minecraft/world/level/block/entity/Hopper;Lnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/world/level/block/state/BlockState;";

    /** The lambda that holds startSleepInBed's checks; its descriptor differs between releases. */
    private static final String SLEEP_CHECKS = "lambda$startSleepInBed$0";

    private NeoForgeInjectionPoints() {}

    static List<InjectionPointReplacement> all() {
        return List.of(
                // Language.loadDefault: vanilla calls Map.copyOf(map) after loading the default translations, where
                // Fabric API's LanguageMixin.create loads Fabric mods' server translations into map. NeoForge replaces
                // Map.copyOf(map) with LanguageHook.captureLanguageMap(map, componentMap) and keeps map mutable.
                new InjectionPointReplacement(
                        "net/minecraft/locale/Language",
                        "loadDefault",
                        "Ljava/util/Map;copyOf(Ljava/util/Map;)Ljava/util/Map;",
                        "Lnet/neoforged/neoforge/server/LanguageHook;captureLanguageMap(Ljava/util/Map;Ljava/util/Map;)V"),
                // Language.parseTranslations(BiConsumer, String): NeoForge turns the two-parameter method into a
                // delegate to its three-parameter overload parseTranslations(BiConsumer, BiConsumer, String), which
                // holds the vanilla body (Class.getResourceAsStream) that Fabric API's LanguageMixin redirects.
                new InjectionPointReplacement(
                        "net/minecraft/locale/Language",
                        "parseTranslations",
                        "Ljava/lang/Class;getResourceAsStream(Ljava/lang/String;)Ljava/io/InputStream;",
                        "parseTranslations(Ljava/util/function/BiConsumer;Ljava/util/function/BiConsumer;Ljava/lang/String;)V",
                        InjectionPointReplacement.Placement.IN_METHOD),
                // ServerConfigurationPacketListenerImpl.startConfiguration: NeoForge sends its channel registration
                // payloads in startConfiguration() and moves the vanilla configuration tasks
                // (SynchronizeRegistriesTask)
                // into runConfiguration(), where Fabric API's ServerConfigurationPacketListenerImplMixin filters known
                // packs.
                new InjectionPointReplacement(
                        "net/minecraft/server/network/ServerConfigurationPacketListenerImpl",
                        "startConfiguration",
                        "Lnet/minecraft/server/network/config/SynchronizeRegistriesTask;<init>(Ljava/util/List;Lnet/minecraft/core/LayeredRegistryAccess;)V",
                        "runConfiguration()V",
                        InjectionPointReplacement.Placement.IN_METHOD),
                // ItemStack.useOn: vanilla calls Item.useOn(UseOnContext) where Fabric API's ItemStackMixin wraps the
                // call for ItemEvents.USE_ON. NeoForge moves the vanilla call into lambda$useOn$0.
                new InjectionPointReplacement(
                        "net/minecraft/world/item/ItemStack",
                        "useOn",
                        "Lnet/minecraft/world/item/Item;useOn(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;",
                        "lambda$useOn$0(Lnet/minecraft/world/item/context/UseOnContext;Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;",
                        InjectionPointReplacement.Placement.IN_METHOD),
                // ServerPlayerGameMode.destroyBlock: vanilla calls Block.destroy when removeBlock removed the block.
                // NeoForge's removeBlock(BlockPos, BlockState, boolean, ItemStack) calls it when onDestroyedByPlayer
                // returns true, exactly when Fabric API's PlayerBlockBreakEvents.AFTER runs.
                new InjectionPointReplacement(
                        "net/minecraft/server/level/ServerPlayerGameMode",
                        "destroyBlock",
                        "Lnet/minecraft/world/level/block/Block;destroy(Lnet/minecraft/world/level/LevelAccessor;"
                                + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V",
                        "Lnet/minecraft/server/level/ServerPlayerGameMode;removeBlock(Lnet/minecraft/core/BlockPos;"
                                + "Lnet/minecraft/world/level/block/state/BlockState;ZLnet/minecraft/world/item/ItemStack;)Z",
                        InjectionPointReplacement.Placement.AFTER_TRUE_RESULT),
                // LivingEntity.canGlide: NeoForge moves the EquipmentSlot.VALUES iteration from canGlide() to its
                // overload canGlide(boolean). Fabric API's LivingEntityMixin injects at EquipmentSlot.VALUES.
                new InjectionPointReplacement(
                        "net/minecraft/world/entity/LivingEntity",
                        "canGlide",
                        "Lnet/minecraft/world/entity/EquipmentSlot;VALUES:Ljava/util/List;",
                        "canGlide(Z)Z",
                        InjectionPointReplacement.Placement.IN_METHOD),
                // ServerPlayer.startSleepInBed: NeoForge moves the sleeping precondition checks into
                // lambda$startSleepInBed$0, (BlockPos) on 26.2.0.93 and (BlockState, BedRule, BlockPos, boolean) on
                // 26.3.0.98, so the point names the lambda only.
                // Fabric API's ServerPlayerMixin wraps getValue, setRespawnPosition and isEmpty there.
                new InjectionPointReplacement(
                        "net/minecraft/server/level/ServerPlayer",
                        "startSleepInBed",
                        "Lnet/minecraft/world/level/block/state/BlockState;getValue(Lnet/minecraft/world/level/block/state/properties/Property;)Ljava/lang/Comparable;",
                        SLEEP_CHECKS,
                        InjectionPointReplacement.Placement.IN_METHOD),
                new InjectionPointReplacement(
                        "net/minecraft/server/level/ServerPlayer",
                        "startSleepInBed",
                        "Lnet/minecraft/server/level/ServerPlayer;setRespawnPosition(Lnet/minecraft/server/level/ServerPlayer$RespawnConfig;Z)V",
                        SLEEP_CHECKS,
                        InjectionPointReplacement.Placement.IN_METHOD),
                new InjectionPointReplacement(
                        "net/minecraft/server/level/ServerPlayer",
                        "startSleepInBed",
                        "Ljava/util/List;isEmpty()Z",
                        SLEEP_CHECKS,
                        InjectionPointReplacement.Placement.IN_METHOD),
                // RegistryLoadTask$PendingRegistration.loadFromResource (26.2.0.93): NeoForge parses the same JSON
                // element with its conditional codec (ConditionalOps.createConditionalCodec) where vanilla calls
                // Decoder.parse; an injection before the call sees the same locals.
                new InjectionPointReplacement(
                        "net/minecraft/resources/RegistryLoadTask$PendingRegistration",
                        "loadFromResource",
                        "Lcom/mojang/serialization/Decoder;parse(Lcom/mojang/serialization/DynamicOps;Ljava/lang/Object;)Lcom/mojang/serialization/DataResult;",
                        "Lcom/mojang/serialization/Codec;parse(Lcom/mojang/serialization/DynamicOps;Ljava/lang/Object;)Lcom/mojang/serialization/DataResult;"),
                // LivingEntity.forceAddEffect: NeoForge replaces canBeAffected with CommonHooks.canMobEffectBeApplied.
                new InjectionPointReplacement(
                        "net/minecraft/world/entity/LivingEntity",
                        "forceAddEffect",
                        "Lnet/minecraft/world/entity/LivingEntity;canBeAffected(Lnet/minecraft/world/effect/MobEffectInstance;)Z",
                        "Lnet/neoforged/neoforge/common/CommonHooks;canMobEffectBeApplied(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z"),
                // SignEditScreen and HangingSignEditScreen (<init>): NeoForge replaces Identifier.withDefaultNamespace
                // with Identifier.parse for custom sign textures.
                new InjectionPointReplacement(
                        "net/minecraft/client/gui/screens/inventory/SignEditScreen",
                        "<init>",
                        "Lnet/minecraft/resources/Identifier;withDefaultNamespace(Ljava/lang/String;)Lnet/minecraft/resources/Identifier;",
                        "Lnet/minecraft/resources/Identifier;parse(Ljava/lang/String;)Lnet/minecraft/resources/Identifier;"),
                new InjectionPointReplacement(
                        "net/minecraft/client/gui/screens/inventory/HangingSignEditScreen",
                        "<init>",
                        "Lnet/minecraft/resources/Identifier;withDefaultNamespace(Ljava/lang/String;)Lnet/minecraft/resources/Identifier;",
                        "Lnet/minecraft/resources/Identifier;parse(Ljava/lang/String;)Lnet/minecraft/resources/Identifier;"),
                // Entity.spawnSprintParticle and LivingEntity.checkFallDamage: NeoForge adds BlockPos to
                // BlockParticleOption constructor.
                new InjectionPointReplacement(
                        "net/minecraft/world/entity/Entity",
                        "spawnSprintParticle",
                        "(Lnet/minecraft/core/particles/ParticleType;Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/core/particles/BlockParticleOption;",
                        "(Lnet/minecraft/core/particles/ParticleType;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/particles/BlockParticleOption;"),
                new InjectionPointReplacement(
                        "net/minecraft/world/entity/LivingEntity",
                        "checkFallDamage",
                        "(Lnet/minecraft/core/particles/ParticleType;Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/core/particles/BlockParticleOption;",
                        "(Lnet/minecraft/core/particles/ParticleType;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/particles/BlockParticleOption;"),
                // ServerPlayer.openMenu(MenuProvider): NeoForge turns it into a delegate to openMenu(MenuProvider,
                // Consumer), which holds the vanilla body: closeContainer() and, when the menu has no extra client
                // data, the vanilla ClientboundOpenScreenPacket sent through ServerGamePacketListenerImpl.send(Packet).
                // Fabric API's ServerPlayerMixin (fabric-menu-api-v1) redirects closeContainer and injects at and
                // redirects send there. (The four points 1.2.3 added for Gui.extractRenderState, openMenu,
                // ClientPacketListener.handleCustomPayload and ModelManager.<init> named calls vanilla does not make;
                // NeoForgeInjectionPointsTest found them on 2026-10-27, and they moved nothing.)
                new InjectionPointReplacement(
                        "net/minecraft/server/level/ServerPlayer",
                        "openMenu",
                        "Lnet/minecraft/server/level/ServerPlayer;closeContainer()V",
                        "openMenu(Lnet/minecraft/world/MenuProvider;Ljava/util/function/Consumer;)Ljava/util/OptionalInt;",
                        InjectionPointReplacement.Placement.IN_METHOD),
                new InjectionPointReplacement(
                        "net/minecraft/server/level/ServerPlayer",
                        "openMenu",
                        "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V",
                        "openMenu(Lnet/minecraft/world/MenuProvider;Ljava/util/function/Consumer;)Ljava/util/OptionalInt;",
                        InjectionPointReplacement.Placement.IN_METHOD),
                // Bootstrap.bootStrap: vanilla calls wrapStreams() last, after CreativeModeTabs.validate(), where
                // Fabric API's BootstrapMixin (fabric-registry-sync-v0) injects afterInitialize. NeoForge calls
                // GameData.vanillaSnapshot() in its place and wrapStreams nowhere. afterInitialize only adds
                // listeners and changes no registry, so it runs before the snapshot as it ran before wrapStreams.
                // Only an @Inject moves there (the owner differs).
                new InjectionPointReplacement(
                        "net/minecraft/server/Bootstrap",
                        "bootStrap",
                        "Lnet/minecraft/server/Bootstrap;wrapStreams()V",
                        "Lnet/neoforged/neoforge/registries/GameData;vanillaSnapshot()V"),
                // Checked with javap on NeoForge 26.2.0.93 and 26.3.0.98 against vanilla 26.2 and 26.3:
                // FishingHook.shouldStopFishing asks ItemStack.canPerformAction(ItemAbilities.FISHING_ROD_CAST) where
                // vanilla asks ItemStack.is(Items.FISHING_ROD); Fabric API's allowModdedRods wraps the vanilla check.
                new InjectionPointReplacement(
                        "net/minecraft/world/entity/projectile/FishingHook",
                        "shouldStopFishing",
                        "Lnet/minecraft/world/item/ItemStack;is(Ljava/lang/Object;)Z",
                        "Lnet/minecraft/world/item/ItemStack;canPerformAction(Lnet/neoforged/neoforge/common/ItemAbility;)Z"),
                // ServerGamePacketListenerImpl.handlePickItemFromBlock asks NeoForge's
                // BlockState.getCloneItemStack(BlockPos, LevelReader, boolean, Player) where vanilla asks
                // getCloneItemStack(LevelReader, BlockPos, boolean); Fabric API's onPickItemFromBlock wraps it.
                new InjectionPointReplacement(
                        "net/minecraft/server/network/ServerGamePacketListenerImpl",
                        "handlePickItemFromBlock",
                        "Lnet/minecraft/world/level/block/state/BlockState;getCloneItemStack(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Z)Lnet/minecraft/world/item/ItemStack;",
                        "Lnet/minecraft/world/level/block/state/BlockState;getCloneItemStack(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/LevelReader;ZLnet/minecraft/world/entity/player/Player;)Lnet/minecraft/world/item/ItemStack;"),
                // BaseContainerBlockEntity.setItem(int, ItemStack) and SimpleContainer.setItem(int, ItemStack) call
                // NeoForge's setItem(int, ItemStack, boolean), which holds the vanilla body with its setChanged()
                // (Fabric API's transfer API wraps that call).
                new InjectionPointReplacement(
                        "net/minecraft/world/level/block/entity/BaseContainerBlockEntity",
                        "setItem",
                        "Lnet/minecraft/world/level/block/entity/BaseContainerBlockEntity;setChanged()V",
                        "setItem(ILnet/minecraft/world/item/ItemStack;Z)V",
                        InjectionPointReplacement.Placement.IN_METHOD),
                new InjectionPointReplacement(
                        "net/minecraft/world/SimpleContainer",
                        "setItem",
                        "Lnet/minecraft/world/SimpleContainer;setChanged()V",
                        "setItem(ILnet/minecraft/world/item/ItemStack;Z)V",
                        InjectionPointReplacement.Placement.IN_METHOD),
                // NeoForge 26.2.0.93 only (26.3.0.98 keeps vanilla's calls; javap on both):
                // LivingEntity.getBedOrientation
                // asks BlockState.getBedDirection(LevelReader, BlockPos) where vanilla asks
                // BedBlock.getBedOrientation(BlockGetter, BlockPos), and lambda$stopSleeping$0 calls
                // BlockState.setBedOccupied(Level, BlockPos, LivingEntity, boolean) where vanilla calls Level.setBlock,
                // as MinecraftForge does. Fabric API's sleeping direction and bed occupation events run there.
                new InjectionPointReplacement(
                        "net/minecraft/world/entity/LivingEntity",
                        "getBedOrientation",
                        "Lnet/minecraft/world/level/block/BedBlock;getBedOrientation(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/Direction;",
                        "Lnet/minecraft/world/level/block/state/BlockState;getBedDirection(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/Direction;"),
                new InjectionPointReplacement(
                        "net/minecraft/world/entity/LivingEntity",
                        "lambda$stopSleeping$0",
                        "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z",
                        "Lnet/minecraft/world/level/block/state/BlockState;setBedOccupied(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/LivingEntity;Z)V"),
                // LivingEntity.removeAllEffects (26.2.0.93, 26.3.0.98): after the same early returns, vanilla copies
                // the
                // effects with Maps.newHashMap(activeEffects) and clears the map; NeoForge sizes a new HashMap with
                // activeEffects.size() at that place, collects the effects its MobEffectEvent.Remove lets go and
                // removes them with removed.keySet().forEach(activeEffects::remove). Fabric API's
                // beforeRemoveAllEffects (BEFORE_REMOVE for every active effect) runs at the first, and its
                // allowRemoveAllEffects wrap of the clear runs on the removal (HostEffectRemoval).
                new InjectionPointReplacement(
                        "net/minecraft/world/entity/LivingEntity",
                        "removeAllEffects",
                        "Lcom/google/common/collect/Maps;newHashMap(Ljava/util/Map;)Ljava/util/HashMap;",
                        "Ljava/util/Map;size()I"),
                new InjectionPointReplacement(
                        "net/minecraft/world/entity/LivingEntity",
                        "removeAllEffects",
                        "Ljava/util/Map;clear()V",
                        "Ljava/util/Set;forEach(Ljava/util/function/Consumer;)V"),
                // HopperBlockEntity.ejectItems and suckInItems (26.2.0.93, 26.3.0.98): NeoForge looks up a
                // ContainerOrHandler (the container, or NeoForge's item handler of the block) where vanilla looks up
                // the container. Fabric API's transfer API moves items through a block's Fabric storage after the
                // vanilla lookup found no container (HostHopperTransfer).
                new InjectionPointReplacement(
                        HOPPER,
                        "ejectItems",
                        "L" + HOPPER + ";getAttachedContainer(Lnet/minecraft/world/level/Level;"
                                + "Lnet/minecraft/core/BlockPos;L" + HOPPER + ";)Lnet/minecraft/world/Container;",
                        "L" + HOPPER + ";getContainerOrHandlerAt(Lnet/minecraft/world/level/Level;"
                                + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)"
                                + CONTAINER_OR_HANDLER),
                new InjectionPointReplacement(
                        HOPPER,
                        "suckInItems",
                        "L" + HOPPER + ";getSourceContainer(" + SOURCE_LOOKUP + ")Lnet/minecraft/world/Container;",
                        "L" + HOPPER + ";getSourceContainerOrHandler(" + SOURCE_LOOKUP + ")" + CONTAINER_OR_HANDLER));
    }
}
