package dev.plexusmodloader.game.version.v1_21_11;

import dev.plexusmodloader.api.runtime.GameSide;
import dev.plexusmodloader.api.system.SystemLookup;
import dev.plexusmodloader.game.version.EventPublisher;
import dev.plexusmodloader.game.version.GameLaunchTarget;
import dev.plexusmodloader.game.version.GameVersionAdapter;
import dev.plexusmodloader.internal.transform.HeadHookResult;
import dev.plexusmodloader.internal.transform.InjectStaticCallAtHead;
import dev.plexusmodloader.internal.transform.InvokeHookAtCall;
import dev.plexusmodloader.internal.transform.InvokeHookAtHead;
import dev.plexusmodloader.internal.transform.InvokeHookBeforeReturn;
import dev.plexusmodloader.internal.transform.MethodTarget;
import dev.plexusmodloader.internal.transform.MethodTransformAction;
import dev.plexusmodloader.internal.transform.RedirectCall;
import dev.plexusmodloader.internal.transform.ReplaceConstantReturnWithStaticCall;
import dev.plexusmodloader.internal.transform.TransformFailurePolicy;
import dev.plexusmodloader.internal.transform.TransformRuleDefinition;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Adapter for Minecraft: Java Edition 1.21.11, an <b>experimental</b> version (ADR-0019): obfuscated, so it runs on
 * the game jar remapped to Mojang names with Mojang's mappings, on Java 25 (its own version JSON asks for Java 21).
 * Copied from the 26.1 adapter. Rule targets are checked against the remapped 1.21.11 client jar (original SHA-1
 * {@code ba2df812c2d12e0219c489c4cd9a5e1f0760f5bd}) by the game layer's {@code BuiltInRulesTest}; the ones that
 * differ from 26.1 were looked up with {@code javap} there.
 */
public final class Minecraft1_21_11Adapter implements GameVersionAdapter {

    private volatile boolean client;

    static final String GAME_VERSION = "1.21.11";
    private static final String HOOKS = "dev/plexusmodloader/game/version/v1_21_11/Minecraft1_21_11Hooks";
    private static final String GAME_HOOKS = "dev/plexusmodloader/game/layer/v1_21_11/GameHooks";
    private static final String CLIENT_HOOKS = "dev/plexusmodloader/game/layer/v1_21_11/client/ClientHooks";
    private static final String INTERACTION_HOOKS = "dev/plexusmodloader/game/layer/v1_21_11/InteractionHooks";
    private static final String LIFECYCLE_HOOKS = "dev/plexusmodloader/game/layer/v1_21_11/LifecycleHooks";
    private static final String CREATIVE_PAGING = "dev/plexusmodloader/game/layer/v1_21_11/client/CreativeTabPaging";
    private static final String CREATIVE_SCREEN =
            "net/minecraft/client/gui/screens/inventory/CreativeModeInventoryScreen";
    private static final Set<GameSide> ALL_SIDES = EnumSet.allOf(GameSide.class);
    private static final Set<GameSide> CLIENT_ONLY = EnumSet.of(GameSide.CLIENT);

    @Override
    public String adapterId() {
        return "minecraft-" + GAME_VERSION;
    }

    @Override
    public String gameVersion() {
        return GAME_VERSION;
    }

    /** 1.21.11 only; 1.21.10 is compared separately before it is added (docs/plans/MINECRAFT_1_21.md). */
    @Override
    public Set<String> gameVersions() {
        return Set.of(GAME_VERSION);
    }

    @Override
    public String mainClass(GameLaunchTarget target) {
        return switch (target) {
            case CLIENT -> "net.minecraft.client.main.Main";
            case DEDICATED_SERVER -> "net.minecraft.server.Main";
            // Mojang's headless GameTest runner; ships in the server jar (verified with javap).
            case GAME_TEST_SERVER -> "net.minecraft.gametest.Main";
        };
    }

    /**
     * Routes the game's mod brand to the {@code poly:game_brand} system. Vanilla returns
     * {@code "vanilla"}; any other brand makes Minecraft report itself as modded (window title,
     * crash reports, server brand). Both rules are optional features, so a mismatch disables the
     * rule instead of stopping the launch.
     */
    @Override
    public void bindSystems(SystemLookup systems) {
        Minecraft1_21_11Hooks.bind(systems);
    }

    @Override
    public void bindEvents(EventPublisher events) {
        Minecraft1_21_11Hooks.bindEvents(events);
    }

    /** Every launch target has a mod initialization point (see {@link #modInitializationRules()}). */
    @Override
    public boolean bindModInitialization(GameLaunchTarget target, Runnable initialization) {
        Minecraft1_21_11Hooks.bindModInitialization(initialization);
        client = target == GameLaunchTarget.CLIENT;
        return true;
    }

    /**
     * The client's {@code Minecraft} object (assigned at the start of its constructor, before mods initialise), or the
     * server whose {@code runServer} started (dedicated or game test server); an integrated server is never returned.
     */
    @Override
    public Optional<Object> gameInstance() {
        if (!client) {
            return Optional.ofNullable(Minecraft1_21_11Hooks.runningServer());
        }
        try {
            Class<?> minecraft = Class.forName(
                    "net.minecraft.client.Minecraft",
                    false,
                    Thread.currentThread().getContextClassLoader());
            return Optional.ofNullable(minecraft.getMethod("getInstance").invoke(null));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Minecraft.getInstance() is not available in this game", e);
        }
    }

    @Override
    public Optional<String> gameLayerClass() {
        return Optional.of("dev.plexusmodloader.game.layer.v1_21_11.GameLayer1_21_11");
    }

    @Override
    public List<TransformRuleDefinition> builtInTransformRules() {
        List<TransformRuleDefinition> rules = new ArrayList<>(brandAndServerRules());
        rules.addAll(nativeApiRules());
        rules.addAll(modInitializationRules());
        rules.add(titleScreenModCountsRule());
        return List.copyOf(rules);
    }

    /**
     * The title screen's mod counts per loader. {@code TitleScreen.render} draws the version text ({@code "Minecraft
     * " + version}, the demo suffix and {@code menu.modded}) with its only call to {@code GuiGraphics.drawString(Font,
     * String, int, int, int)} (javap on the remapped 1.21.11 jar, offset 310); the hook draws that line and the counts
     * above it. Cosmetic, so a mismatch turns only this rule off. MinecraftForge's patch replaces that call with its own
     * branding, which {@code plexus-bridge-forge}'s rule {@code poly:client_title_mod_counts_forge} extends instead.
     */
    private static TransformRuleDefinition titleScreenModCountsRule() {
        return new TransformRuleDefinition(
                "poly:client_title_mod_counts",
                "poly",
                GAME_VERSION,
                CLIENT_ONLY,
                new MethodTarget(
                        "net/minecraft/client/gui/screens/TitleScreen",
                        "render",
                        "(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"),
                new RedirectCall(
                        new MethodTarget(
                                "net/minecraft/client/gui/GuiGraphics",
                                "drawString",
                                "(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V"),
                        0,
                        "dev/plexusmodloader/game/layer/v1_21_11/client/TitleScreenModCounts",
                        "onVersionText"),
                TransformFailurePolicy.DISABLE_RULE);
    }

    /**
     * Rules that connect the Native API game layer (ADR-0010). Every target was checked with
     * {@code javap} against the 1.21.11 client jar; the hook parameter lists in {@code GameHooks} and
     * {@code ClientHooks} mirror them. All are required features, so a mismatch stops the launch.
     */
    private static List<TransformRuleDefinition> nativeApiRules() {
        return List.of(
                rule(
                        "poly:content_registration",
                        ALL_SIDES,
                        "net/minecraft/core/registries/BuiltInRegistries",
                        "freeze",
                        "()V",
                        new InvokeHookAtHead(GAME_HOOKS, "beforeRegistriesFreeze", HeadHookResult.CONTINUE)),
                rule(
                        "poly:mod_resource_packs",
                        ALL_SIDES,
                        "net/minecraft/server/packs/repository/BuiltInPackSource",
                        "loadPacks",
                        "(Ljava/util/function/Consumer;)V",
                        new InvokeHookAtHead(GAME_HOOKS, "onLoadPacks", HeadHookResult.CONTINUE)),
                rule(
                        "poly:commands",
                        ALL_SIDES,
                        "net/minecraft/commands/Commands",
                        "<init>",
                        "(Lnet/minecraft/commands/Commands$CommandSelection;Lnet/minecraft/commands/CommandBuildContext;)V",
                        new InvokeHookBeforeReturn(GAME_HOOKS, "onCommandsCreated")),
                rule(
                        "poly:payload_codecs",
                        ALL_SIDES,
                        "net/minecraft/network/protocol/common/custom/CustomPacketPayload",
                        "codec",
                        "(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload$FallbackProvider;"
                                + "Ljava/util/List;)Lnet/minecraft/network/codec/StreamCodec;",
                        new InvokeHookAtHead(GAME_HOOKS, "onPayloadCodec", HeadHookResult.RETURN_IF_NON_NULL)),
                rule(
                        "poly:server_payloads",
                        ALL_SIDES,
                        "net/minecraft/server/network/ServerGamePacketListenerImpl",
                        "handleCustomPayload",
                        "(Lnet/minecraft/network/protocol/common/ServerboundCustomPayloadPacket;)V",
                        new InvokeHookAtHead(GAME_HOOKS, "onServerPayload", HeadHookResult.CANCEL_IF_TRUE)),
                rule(
                        "poly:configuration_tasks",
                        ALL_SIDES,
                        "net/minecraft/server/network/ServerConfigurationPacketListenerImpl",
                        "addOptionalTasks",
                        "()V",
                        new InvokeHookAtHead(GAME_HOOKS, "onAddConfigurationTasks", HeadHookResult.CONTINUE)),
                rule(
                        "poly:configuration_payloads",
                        ALL_SIDES,
                        "net/minecraft/server/network/ServerCommonPacketListenerImpl",
                        "handleCustomPayload",
                        "(Lnet/minecraft/network/protocol/common/ServerboundCustomPayloadPacket;)V",
                        new InvokeHookAtHead(GAME_HOOKS, "onCommonPayload", HeadHookResult.CANCEL_IF_TRUE)),
                rule(
                        "poly:client_configuration_payloads",
                        CLIENT_ONLY,
                        "net/minecraft/client/multiplayer/ClientConfigurationPacketListenerImpl",
                        "handleCustomPayload",
                        "(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V",
                        new InvokeHookAtHead(CLIENT_HOOKS, "onConfigurationPayload", HeadHookResult.CANCEL_IF_TRUE)),
                rule(
                        "poly:player_joined",
                        ALL_SIDES,
                        "net/minecraft/server/players/PlayerList",
                        "placeNewPlayer",
                        "(Lnet/minecraft/network/Connection;Lnet/minecraft/server/level/ServerPlayer;"
                                + "Lnet/minecraft/server/network/CommonListenerCookie;)V",
                        new InvokeHookBeforeReturn(GAME_HOOKS, "onPlayerJoined")),
                rule(
                        "poly:player_leaving",
                        ALL_SIDES,
                        "net/minecraft/server/players/PlayerList",
                        "remove",
                        "(Lnet/minecraft/server/level/ServerPlayer;)V",
                        new InvokeHookAtHead(GAME_HOOKS, "onPlayerLeaving", HeadHookResult.CONTINUE)),
                rule(
                        "poly:block_break_capture",
                        ALL_SIDES,
                        "net/minecraft/world/level/block/Block",
                        "playerWillDestroy",
                        "(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;"
                                + "Lnet/minecraft/world/level/block/state/BlockState;"
                                + "Lnet/minecraft/world/entity/player/Player;)"
                                + "Lnet/minecraft/world/level/block/state/BlockState;",
                        new InvokeHookAtHead(GAME_HOOKS, "onPlayerWillDestroy", HeadHookResult.CONTINUE)),
                // The head hook may refuse the break; the return hook reports breaks that happened.
                rule(
                        "poly:block_break",
                        ALL_SIDES,
                        "net/minecraft/server/level/ServerPlayerGameMode",
                        "destroyBlock",
                        "(Lnet/minecraft/core/BlockPos;)Z",
                        new InvokeHookAtHead(INTERACTION_HOOKS, "onDestroyBlock", HeadHookResult.RETURN_IF_NON_NULL)),
                rule(
                        "poly:block_broken",
                        ALL_SIDES,
                        "net/minecraft/server/level/ServerPlayerGameMode",
                        "destroyBlock",
                        "(Lnet/minecraft/core/BlockPos;)Z",
                        new InvokeHookBeforeReturn(GAME_HOOKS, "onDestroyBlockReturn"),
                        "poly:block_break"),
                rule(
                        "poly:use_block",
                        ALL_SIDES,
                        "net/minecraft/server/level/ServerPlayerGameMode",
                        "useItemOn",
                        "(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/Level;"
                                + "Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;"
                                + "Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;",
                        new InvokeHookAtHead(INTERACTION_HOOKS, "onUseItemOn", HeadHookResult.RETURN_IF_NON_NULL)),
                rule(
                        "poly:use_item",
                        ALL_SIDES,
                        "net/minecraft/server/level/ServerPlayerGameMode",
                        "useItem",
                        "(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/Level;"
                                + "Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;)"
                                + "Lnet/minecraft/world/InteractionResult;",
                        new InvokeHookAtHead(INTERACTION_HOOKS, "onUseItem", HeadHookResult.RETURN_IF_NON_NULL)),
                // Declared on Player and not overridden by ServerPlayer; the hooks skip the client's copy.
                rule(
                        "poly:use_entity",
                        ALL_SIDES,
                        "net/minecraft/world/entity/player/Player",
                        "interactOn",
                        // 1.21.11's interactOn has no click location (26.x adds a Vec3); javap.
                        "(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/InteractionHand;)"
                                + "Lnet/minecraft/world/InteractionResult;",
                        new InvokeHookAtHead(INTERACTION_HOOKS, "onInteractOn", HeadHookResult.RETURN_IF_NON_NULL)),
                rule(
                        "poly:attack_entity",
                        ALL_SIDES,
                        "net/minecraft/world/entity/player/Player",
                        "attack",
                        "(Lnet/minecraft/world/entity/Entity;)V",
                        new InvokeHookAtHead(INTERACTION_HOOKS, "onAttack", HeadHookResult.CANCEL_IF_TRUE)),
                // BlockItem.place calls placeBlock once (javap); BedItem and DoubleHighBlockItem override
                // placeBlock, so the hook calls it virtually. place never writes its parameter slot.
                rule(
                        "poly:block_place",
                        ALL_SIDES,
                        "net/minecraft/world/item/BlockItem",
                        "place",
                        "(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/InteractionResult;",
                        new RedirectCall(
                                new MethodTarget(
                                        "net/minecraft/world/item/BlockItem",
                                        "placeBlock",
                                        "(Lnet/minecraft/world/item/context/BlockPlaceContext;"
                                                + "Lnet/minecraft/world/level/block/state/BlockState;)Z"),
                                0,
                                INTERACTION_HOOKS,
                                "onPlaceBlock")),
                rule(
                        "poly:block_placed",
                        ALL_SIDES,
                        "net/minecraft/world/item/BlockItem",
                        "place",
                        "(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/InteractionResult;",
                        new InvokeHookBeforeReturn(INTERACTION_HOOKS, "onPlaceReturn"),
                        "poly:block_place"),
                rule(
                        "poly:level_tick",
                        ALL_SIDES,
                        "net/minecraft/server/level/ServerLevel",
                        "tick",
                        "(Ljava/util/function/BooleanSupplier;)V",
                        new InvokeHookAtHead(GAME_HOOKS, "onLevelTick", HeadHookResult.CONTINUE)),
                rule(
                        "poly:entity_attributes",
                        ALL_SIDES,
                        "net/minecraft/world/entity/ai/attributes/DefaultAttributes",
                        "getSupplier",
                        "(Lnet/minecraft/world/entity/EntityType;)Lnet/minecraft/world/entity/ai/attributes/AttributeSupplier;",
                        new InvokeHookAtHead(GAME_HOOKS, "onGetAttributes", HeadHookResult.RETURN_IF_NON_NULL)),
                rule(
                        "poly:entity_has_attributes",
                        ALL_SIDES,
                        "net/minecraft/world/entity/ai/attributes/DefaultAttributes",
                        "hasSupplier",
                        "(Lnet/minecraft/world/entity/EntityType;)Z",
                        new InvokeHookAtHead(GAME_HOOKS, "onHasAttributes", HeadHookResult.RETURN_IF_NON_NULL)),
                rule(
                        "poly:entity_damage",
                        ALL_SIDES,
                        "net/minecraft/world/entity/LivingEntity",
                        "hurtServer",
                        "(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z",
                        new InvokeHookAtHead(GAME_HOOKS, "onHurtServer", HeadHookResult.RETURN_IF_NON_NULL)),
                rule(
                        "poly:entity_death",
                        ALL_SIDES,
                        "net/minecraft/world/entity/LivingEntity",
                        "die",
                        "(Lnet/minecraft/world/damagesource/DamageSource;)V",
                        new InvokeHookAtHead(GAME_HOOKS, "onDie", HeadHookResult.CONTINUE)),
                // ServerPlayer.die overrides LivingEntity.die without calling it (javap), so server
                // players need their own death hook.
                rule(
                        "poly:player_death",
                        ALL_SIDES,
                        "net/minecraft/server/level/ServerPlayer",
                        "die",
                        "(Lnet/minecraft/world/damagesource/DamageSource;)V",
                        new InvokeHookAtHead(GAME_HOOKS, "onPlayerDie", HeadHookResult.CONTINUE)),
                rule(
                        "poly:client_entity_renderers",
                        CLIENT_ONLY,
                        "net/minecraft/client/renderer/entity/EntityRenderers",
                        "createEntityRenderers",
                        "(Lnet/minecraft/client/renderer/entity/EntityRendererProvider$Context;)Ljava/util/Map;",
                        new InvokeHookBeforeReturn(CLIENT_HOOKS, "onCreateEntityRenderers")),
                rule(
                        "poly:chunk_loaded",
                        ALL_SIDES,
                        "net/minecraft/world/level/chunk/LevelChunk",
                        "registerAllBlockEntitiesAfterLevelLoad",
                        "()V",
                        new InvokeHookAtHead(GAME_HOOKS, "onChunkLoaded", HeadHookResult.CONTINUE)),
                rule(
                        "poly:chunk_unloaded",
                        ALL_SIDES,
                        "net/minecraft/server/level/ServerLevel",
                        "unload",
                        "(Lnet/minecraft/world/level/chunk/LevelChunk;)V",
                        new InvokeHookAtHead(GAME_HOOKS, "onChunkUnloaded", HeadHookResult.CONTINUE)),
                rule(
                        "poly:server_reload_listeners",
                        ALL_SIDES,
                        "net/minecraft/server/ReloadableServerResources",
                        "listeners",
                        "()Ljava/util/List;",
                        new InvokeHookBeforeReturn(GAME_HOOKS, "onServerReloadListeners")),
                rule(
                        "poly:entity_data_save",
                        ALL_SIDES,
                        "net/minecraft/world/entity/Entity",
                        "saveWithoutId",
                        "(Lnet/minecraft/world/level/storage/ValueOutput;)V",
                        new InvokeHookBeforeReturn(GAME_HOOKS, "onEntitySaved")),
                rule(
                        "poly:entity_data_load",
                        ALL_SIDES,
                        "net/minecraft/world/entity/Entity",
                        "load",
                        "(Lnet/minecraft/world/level/storage/ValueInput;)V",
                        new InvokeHookBeforeReturn(GAME_HOOKS, "onEntityLoaded")),
                rule(
                        "poly:player_respawn_data",
                        ALL_SIDES,
                        "net/minecraft/server/level/ServerPlayer",
                        "restoreFrom",
                        "(Lnet/minecraft/server/level/ServerPlayer;Z)V",
                        new InvokeHookBeforeReturn(GAME_HOOKS, "onPlayerRestored")),
                rule(
                        "poly:entity_tracking_data",
                        ALL_SIDES,
                        "net/minecraft/server/level/ServerEntity",
                        "addPairing",
                        "(Lnet/minecraft/server/level/ServerPlayer;)V",
                        new InvokeHookBeforeReturn(GAME_HOOKS, "onStartTracking")),
                rule(
                        "poly:login_check",
                        ALL_SIDES,
                        "net/minecraft/server/players/PlayerList",
                        "canPlayerLogin",
                        "(Ljava/net/SocketAddress;Lnet/minecraft/server/players/NameAndId;)Lnet/minecraft/network/chat/Component;",
                        new InvokeHookAtHead(GAME_HOOKS, "onCanPlayerLogin", HeadHookResult.RETURN_IF_NON_NULL)),
                rule(
                        "poly:worldgen_features",
                        ALL_SIDES,
                        "net/minecraft/server/MinecraftServer",
                        "createLevels",
                        "()V",
                        new InvokeHookAtHead(GAME_HOOKS, "onCreateLevels", HeadHookResult.CONTINUE)),
                // createLevels has one return and no parameters (javap).
                rule(
                        "poly:world_loaded",
                        ALL_SIDES,
                        "net/minecraft/server/MinecraftServer",
                        "createLevels",
                        "()V",
                        new InvokeHookBeforeReturn(LIFECYCLE_HOOKS, "onLevelsCreated"),
                        "poly:worldgen_features"),
                rule(
                        "poly:world_saving",
                        ALL_SIDES,
                        "net/minecraft/server/level/ServerLevel",
                        "save",
                        "(Lnet/minecraft/util/ProgressListener;ZZ)V",
                        new InvokeHookAtHead(LIFECYCLE_HOOKS, "onLevelSave", HeadHookResult.CONTINUE)),
                // stopServer saves every world, then closes each with ServerLevel.close() in a loop, and
                // returns once at the end (javap). poly:server_stopping_event is its head hook.
                rule(
                        "poly:world_unloaded",
                        ALL_SIDES,
                        "net/minecraft/server/MinecraftServer",
                        "stopServer",
                        "()V",
                        // Observed, not redirected: the call stays, so Fabric mods' Mixin injections at it still
                        // find it (fabric-lifecycle-events-v1 injects before it, ADR-0020).
                        new InvokeHookAtCall(
                                new MethodTarget("net/minecraft/server/level/ServerLevel", "close", "()V"),
                                InvokeHookAtCall.ALL,
                                InvokeHookAtCall.Timing.BEFORE,
                                LIFECYCLE_HOOKS,
                                "onCloseLevel"),
                        "poly:server_stopping_event"),
                rule(
                        "poly:server_stopped",
                        ALL_SIDES,
                        "net/minecraft/server/MinecraftServer",
                        "stopServer",
                        "()V",
                        new InvokeHookBeforeReturn(LIFECYCLE_HOOKS, "onServerStopped"),
                        "poly:world_unloaded"),
                // respawn returns once and never writes its parameter slots (javap).
                rule(
                        "poly:player_respawned",
                        ALL_SIDES,
                        "net/minecraft/server/players/PlayerList",
                        "respawn",
                        "(Lnet/minecraft/server/level/ServerPlayer;ZLnet/minecraft/world/entity/Entity$RemovalReason;)"
                                + "Lnet/minecraft/server/level/ServerPlayer;",
                        new InvokeHookBeforeReturn(LIFECYCLE_HOOKS, "onPlayerRespawned")),
                // EntityCallbacks is package-private, so its first call (ServerChunkCache.addEntity) is
                // observed instead of hooking the method itself.
                rule(
                        "poly:entity_joined_world",
                        ALL_SIDES,
                        "net/minecraft/server/level/ServerLevel$EntityCallbacks",
                        "onTrackingStart",
                        "(Lnet/minecraft/world/entity/Entity;)V",
                        new InvokeHookAtCall(
                                new MethodTarget(
                                        "net/minecraft/server/level/ServerChunkCache",
                                        "addEntity",
                                        "(Lnet/minecraft/world/entity/Entity;)V"),
                                0,
                                InvokeHookAtCall.Timing.AFTER,
                                LIFECYCLE_HOOKS,
                                "onEntityTracked")),
                rule(
                        "poly:mod_dimension_stability",
                        ALL_SIDES,
                        "net/minecraft/world/level/levelgen/WorldDimensions",
                        "checkStability",
                        "(Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/world/level/dimension/LevelStem;)"
                                + "Lcom/mojang/serialization/Lifecycle;",
                        new InvokeHookAtHead(
                                GAME_HOOKS, "onCheckDimensionStability", HeadHookResult.RETURN_IF_NON_NULL)),
                rule(
                        "poly:creative_tabs",
                        ALL_SIDES,
                        "net/minecraft/world/item/CreativeModeTab",
                        "buildContents",
                        "(Lnet/minecraft/world/item/CreativeModeTab$ItemDisplayParameters;)V",
                        new InvokeHookBeforeReturn(GAME_HOOKS, "onCreativeTabBuilt")),
                // Bootstrap calls validate() after the registries froze; it rejects two tabs at one
                // position, which mod tabs on extra pages are by design. The hook checks per page.
                rule(
                        "poly:creative_tab_pages",
                        ALL_SIDES,
                        "net/minecraft/world/item/CreativeModeTabs",
                        "validate",
                        "()V",
                        new InvokeHookAtHead(GAME_HOOKS, "onValidateCreativeTabs", HeadHookResult.CANCEL_IF_TRUE)),
                // The creative screen loops over CreativeModeTabs.tabs() once in each of these four
                // methods (javap); the redirect hands them only the current page's tabs.
                creativeTabsRedirect(
                        "poly:client_creative_tabs_clicked",
                        "mouseClicked",
                        "(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z"),
                creativeTabsRedirect(
                        "poly:client_creative_tabs_released",
                        "mouseReleased",
                        "(Lnet/minecraft/client/input/MouseButtonEvent;)Z"),
                // 1.21.11 draws screens with GuiGraphics in render and renderBg (float first), each
                // calling CreativeModeTabs.tabs() once (javap).
                creativeTabsRedirect(
                        "poly:client_creative_tabs_hovered", "render", "(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"),
                creativeTabsRedirect(
                        "poly:client_creative_tabs_drawn", "renderBg", "(Lnet/minecraft/client/gui/GuiGraphics;FII)V"),
                // getTooltipLines returns twice (javap): the hidden-tooltip list and the built list; the
                // hook skips the first by the same condition. It never writes its parameter slots.
                rule(
                        "poly:client_item_tooltip",
                        CLIENT_ONLY,
                        "net/minecraft/world/item/ItemStack",
                        "getTooltipLines",
                        "(Lnet/minecraft/world/item/Item$TooltipContext;Lnet/minecraft/world/entity/player/Player;"
                                + "Lnet/minecraft/world/item/TooltipFlag;)Ljava/util/List;",
                        new InvokeHookBeforeReturn(
                                "dev/plexusmodloader/game/layer/v1_21_11/client/ItemTooltips", "onTooltipLines")),
                rule(
                        "poly:client_creative_page_buttons",
                        CLIENT_ONLY,
                        CREATIVE_SCREEN,
                        "init",
                        "()V",
                        new InvokeHookBeforeReturn(CREATIVE_PAGING, "onScreenInit")),
                rule(
                        "poly:client_key_bindings",
                        CLIENT_ONLY,
                        "net/minecraft/client/Options",
                        "load",
                        "()V",
                        new InvokeHookAtHead(CLIENT_HOOKS, "onOptionsLoad", HeadHookResult.CONTINUE)),
                rule(
                        "poly:client_tick",
                        CLIENT_ONLY,
                        "net/minecraft/client/Minecraft",
                        "tick",
                        "()V",
                        new InvokeHookBeforeReturn(CLIENT_HOOKS, "onClientTick")),
                rule(
                        "poly:client_hud",
                        CLIENT_ONLY,
                        // 1.21.11 has no Hud class; Gui.render draws the HUD (javap).
                        "net/minecraft/client/gui/Gui",
                        "render",
                        "(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V",
                        new InvokeHookBeforeReturn(CLIENT_HOOKS, "onHudRendered")),
                rule(
                        "poly:client_payloads",
                        CLIENT_ONLY,
                        "net/minecraft/client/multiplayer/ClientPacketListener",
                        "handleCustomPayload",
                        "(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V",
                        new InvokeHookAtHead(CLIENT_HOOKS, "onClientPayload", HeadHookResult.CANCEL_IF_TRUE)),
                rule(
                        "poly:client_particle_providers",
                        CLIENT_ONLY,
                        "net/minecraft/client/particle/ParticleResources",
                        "registerProviders",
                        "()V",
                        new InvokeHookBeforeReturn(CLIENT_HOOKS, "onRegisterParticleProviders")),
                rule(
                        "poly:client_block_entity_renderers",
                        CLIENT_ONLY,
                        "net/minecraft/client/renderer/blockentity/BlockEntityRenderers",
                        "createEntityRenderers",
                        "(Lnet/minecraft/client/renderer/blockentity/BlockEntityRendererProvider$Context;)Ljava/util/Map;",
                        new InvokeHookBeforeReturn(CLIENT_HOOKS, "onCreateBlockEntityRenderers")),
                rule(
                        "poly:client_world_painters",
                        CLIENT_ONLY,
                        "net/minecraft/client/renderer/LevelRenderer",
                        // 1.21.11 has no submitFeatures; submitEntities is its first step there (javap).
                        "submitEntities",
                        "(Lcom/mojang/blaze3d/vertex/PoseStack;"
                                + "Lnet/minecraft/client/renderer/state/LevelRenderState;"
                                + "Lnet/minecraft/client/renderer/SubmitNodeCollector;)V",
                        new InvokeHookAtHead(CLIENT_HOOKS, "onSubmitWorldFeatures", HeadHookResult.CONTINUE)),
                // RenderPass is an interface in 1.21.11; GlRenderPass is its only implementation (javap).
                // setPipeline never writes its parameter slot, as InvokeHookBeforeReturn requires.
                rule(
                        "poly:client_shader_uniforms",
                        CLIENT_ONLY,
                        "com/mojang/blaze3d/opengl/GlRenderPass",
                        "setPipeline",
                        "(Lcom/mojang/blaze3d/pipeline/RenderPipeline;)V",
                        new InvokeHookBeforeReturn(CLIENT_HOOKS, "onSetPipeline")),
                // 1.21.11 has no Camera.calculateFov; GameRenderer.getFov returns the frame's field of view
                // and never writes its parameter slots (javap).
                rule(
                        "poly:client_fov",
                        CLIENT_ONLY,
                        "net/minecraft/client/renderer/GameRenderer",
                        "getFov",
                        "(Lnet/minecraft/client/Camera;FZ)F",
                        new InvokeHookBeforeReturn(CLIENT_HOOKS, "onGetFov")),
                rule(
                        "poly:client_asset_reload",
                        CLIENT_ONLY,
                        "net/minecraft/server/packs/resources/ReloadableResourceManager",
                        "<init>",
                        "(Lnet/minecraft/server/packs/PackType;)V",
                        new InvokeHookBeforeReturn(CLIENT_HOOKS, "onResourceManagerCreated")),
                rule(
                        "poly:client_joined_world",
                        CLIENT_ONLY,
                        "net/minecraft/client/multiplayer/ClientPacketListener",
                        "handleLogin",
                        "(Lnet/minecraft/network/protocol/game/ClientboundLoginPacket;)V",
                        new InvokeHookBeforeReturn(CLIENT_HOOKS, "onClientLogin")),
                rule(
                        "poly:client_left_world",
                        CLIENT_ONLY,
                        "net/minecraft/client/Minecraft",
                        "disconnect",
                        "(Lnet/minecraft/client/gui/screens/Screen;ZZ)V",
                        new InvokeHookAtHead(CLIENT_HOOKS, "onClientDisconnect", HeadHookResult.CONTINUE)),
                rule(
                        "poly:client_menu_screens",
                        CLIENT_ONLY,
                        "net/minecraft/client/gui/screens/MenuScreens",
                        "create",
                        "(Lnet/minecraft/world/inventory/MenuType;Lnet/minecraft/client/Minecraft;I"
                                + "Lnet/minecraft/network/chat/Component;)V",
                        new InvokeHookAtHead(CLIENT_HOOKS, "onCreateMenuScreen", HeadHookResult.CANCEL_IF_TRUE)));
    }

    private static TransformRuleDefinition creativeTabsRedirect(String id, String method, String descriptor) {
        return rule(
                id,
                CLIENT_ONLY,
                CREATIVE_SCREEN,
                method,
                descriptor,
                new RedirectCall(
                        new MethodTarget("net/minecraft/world/item/CreativeModeTabs", "tabs", "()Ljava/util/List;"),
                        0,
                        CREATIVE_PAGING,
                        "onListTabs"));
    }

    private static TransformRuleDefinition rule(
            String id,
            Set<GameSide> sides,
            String owner,
            String method,
            String descriptor,
            MethodTransformAction action,
            String... after) {
        return new TransformRuleDefinition(
                id,
                "poly",
                GAME_VERSION,
                sides,
                new MethodTarget(owner, method, descriptor),
                action,
                TransformFailurePolicy.ABORT_STARTUP,
                List.of(after));
    }

    /**
     * Where foreign mods are initialised: the places Fabric Loader 0.19.5's {@code EntrypointPatch} chooses (ADR-0020).
     * On a dedicated server, in {@code Main.main} right before the static call that precedes reading
     * {@code server.properties}, which is {@code Util.startTimerHackThread()} after {@code Bootstrap.bootStrap()}; the
     * game test server has the same sequence in {@code GameTestMainUtil.runGameTestServer}. On a client, in
     * {@code Minecraft}'s constructor right before its one {@code Thread.currentThread()} call. Checked with
     * {@code javap -c}; each method makes that call once. The calls stay ({@code InvokeHookAtCall}).
     */
    private static List<TransformRuleDefinition> modInitializationRules() {
        MethodTarget timerHack = new MethodTarget("net/minecraft/util/Util", "startTimerHackThread", "()V");
        return List.of(
                rule(
                        "poly:mod_initialization_server",
                        ALL_SIDES,
                        "net/minecraft/server/Main",
                        "main",
                        "([Ljava/lang/String;)V",
                        new InvokeHookAtCall(
                                timerHack, 0, InvokeHookAtCall.Timing.BEFORE, HOOKS, "onModInitialization")),
                rule(
                        "poly:mod_initialization_game_test",
                        ALL_SIDES,
                        "net/minecraft/gametest/framework/GameTestMainUtil",
                        "runGameTestServer",
                        "([Ljava/lang/String;Ljava/util/function/Consumer;)V",
                        new InvokeHookAtCall(
                                timerHack, 0, InvokeHookAtCall.Timing.BEFORE, HOOKS, "onModInitialization")),
                rule(
                        "poly:mod_initialization_client",
                        CLIENT_ONLY,
                        "net/minecraft/client/Minecraft",
                        "<init>",
                        "(Lnet/minecraft/client/main/GameConfig;)V",
                        new InvokeHookAtCall(
                                new MethodTarget("java/lang/Thread", "currentThread", "()Ljava/lang/Thread;"),
                                0,
                                InvokeHookAtCall.Timing.BEFORE,
                                HOOKS,
                                "onModInitialization")));
    }

    private static List<TransformRuleDefinition> brandAndServerRules() {
        return List.of(
                new TransformRuleDefinition(
                        "poly:client_brand",
                        "poly",
                        GAME_VERSION,
                        Set.of(GameSide.CLIENT),
                        new MethodTarget(
                                "net/minecraft/client/ClientBrandRetriever",
                                "getClientModName",
                                "()Ljava/lang/String;"),
                        new ReplaceConstantReturnWithStaticCall("vanilla", HOOKS, "clientModName"),
                        TransformFailurePolicy.DISABLE_RULE),
                new TransformRuleDefinition(
                        "poly:server_brand",
                        "poly",
                        GAME_VERSION,
                        EnumSet.allOf(GameSide.class),
                        new MethodTarget(
                                "net/minecraft/server/MinecraftServer", "getServerModName", "()Ljava/lang/String;"),
                        new ReplaceConstantReturnWithStaticCall("vanilla", HOOKS, "serverModName"),
                        TransformFailurePolicy.DISABLE_RULE),
                // Server lifecycle events. tickServer and stopServer are declared on MinecraftServer and
                // called by every server kind (dedicated, integrated, game test); verified with javap.
                new TransformRuleDefinition(
                        "poly:server_tick_events",
                        "poly",
                        GAME_VERSION,
                        EnumSet.allOf(GameSide.class),
                        new MethodTarget(
                                "net/minecraft/server/MinecraftServer",
                                "tickServer",
                                "(Ljava/util/function/BooleanSupplier;)V"),
                        new InjectStaticCallAtHead(HOOKS, "onServerTick"),
                        TransformFailurePolicy.DISABLE_RULE),
                // The running server, for Fabric Loader's getGameInstance (Fabric records it when the dedicated
                // server is built). runServer starts on the server thread before initServer; verified with javap.
                new TransformRuleDefinition(
                        "poly:server_running",
                        "poly",
                        GAME_VERSION,
                        EnumSet.allOf(GameSide.class),
                        new MethodTarget("net/minecraft/server/MinecraftServer", "runServer", "()V"),
                        new InjectStaticCallAtHead(HOOKS, "onServerRunning"),
                        TransformFailurePolicy.DISABLE_RULE),
                new TransformRuleDefinition(
                        "poly:server_stopping_event",
                        "poly",
                        GAME_VERSION,
                        EnumSet.allOf(GameSide.class),
                        new MethodTarget("net/minecraft/server/MinecraftServer", "stopServer", "()V"),
                        new InjectStaticCallAtHead(HOOKS, "onServerStopping"),
                        TransformFailurePolicy.DISABLE_RULE));
    }
}
