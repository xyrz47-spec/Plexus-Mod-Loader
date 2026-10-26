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
                // lambda$startSleepInBed$0.
                // Fabric API's ServerPlayerMixin wraps getValue, setRespawnPosition and isEmpty there.
                new InjectionPointReplacement(
                        "net/minecraft/server/level/ServerPlayer",
                        "startSleepInBed",
                        "Lnet/minecraft/world/level/block/state/BlockState;getValue(Lnet/minecraft/world/level/block/state/properties/Property;)Ljava/lang/Comparable;",
                        "lambda$startSleepInBed$0(Lnet/minecraft/core/BlockPos;)Lcom/mojang/datafixers/util/Either;",
                        InjectionPointReplacement.Placement.IN_METHOD),
                new InjectionPointReplacement(
                        "net/minecraft/server/level/ServerPlayer",
                        "startSleepInBed",
                        "Lnet/minecraft/server/level/ServerPlayer;setRespawnPosition(Lnet/minecraft/server/level/ServerPlayer$RespawnConfig;Z)V",
                        "lambda$startSleepInBed$0(Lnet/minecraft/core/BlockPos;)Lcom/mojang/datafixers/util/Either;",
                        InjectionPointReplacement.Placement.IN_METHOD),
                new InjectionPointReplacement(
                        "net/minecraft/server/level/ServerPlayer",
                        "startSleepInBed",
                        "Ljava/util/List;isEmpty()Z",
                        "lambda$startSleepInBed$0(Lnet/minecraft/core/BlockPos;)Lcom/mojang/datafixers/util/Either;",
                        InjectionPointReplacement.Placement.IN_METHOD),
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
                        "(Lnet/minecraft/core/particles/ParticleType;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/particles/BlockParticleOption;"));
    }
}
