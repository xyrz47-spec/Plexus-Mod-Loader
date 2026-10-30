package dev.plexusmodloader.game.layer.v1_21_11.client;

import com.mojang.blaze3d.opengl.GlRenderPass;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.plexusmodloader.api.client.CameraPerspective;
import dev.plexusmodloader.api.client.ClientEvents;
import dev.plexusmodloader.api.client.ClientView;
import dev.plexusmodloader.api.client.HudCanvas;
import dev.plexusmodloader.api.client.ModScreen;
import dev.plexusmodloader.api.content.entity.EntityDefinition;
import dev.plexusmodloader.api.content.particle.ParticleDefinition;
import dev.plexusmodloader.api.entity.PlayerView;
import dev.plexusmodloader.api.resource.ResourceId;
import dev.plexusmodloader.api.world.WorldView;
import dev.plexusmodloader.game.layer.v1_21_11.LayerState;
import dev.plexusmodloader.game.layer.v1_21_11.ModReloadListener;
import dev.plexusmodloader.game.layer.v1_21_11.Networking.PolyPayload;
import dev.plexusmodloader.game.layer.v1_21_11.SyncChannel;
import dev.plexusmodloader.game.layer.v1_21_11.Views;
import dev.plexusmodloader.game.layer.v1_21_11.content.Particles;
import dev.plexusmodloader.game.layer.v1_21_11.content.PlexusMenu;
import dev.plexusmodloader.game.layer.v1_21_11.content.PlexusMenus;
import dev.plexusmodloader.game.layer.v1_21_11.entity.EntityInstaller;
import dev.plexusmodloader.game.version.layer.ChannelEndpoint;
import dev.plexusmodloader.game.version.layer.ConfigurationEndpoint;
import dev.plexusmodloader.game.version.layer.GameLayerContext;
import dev.plexusmodloader.game.version.layer.NativeDeclarations;
import dev.plexusmodloader.game.version.layer.NativeDeclarations.BlockEntityRendererDeclaration;
import dev.plexusmodloader.game.version.layer.NativeDeclarations.HudLayerDeclaration;
import dev.plexusmodloader.game.version.layer.NativeDeclarations.KeyBindingDeclaration;
import dev.plexusmodloader.game.version.layer.NativeDeclarations.KeyState;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.particle.ParticleResources;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.state.LevelRenderState;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Client-only hooks; only rules for the client side point here. */
public final class ClientHooks {

    private static final Logger LOGGER = LoggerFactory.getLogger("Poly Mod Loader");
    private static final Map<String, KeyMapping.Category> CATEGORIES = new HashMap<>();
    private static final Set<String> FAILED_LAYERS = ConcurrentHashMap.newKeySet();
    private static volatile boolean keyMappingsAdded;
    private static volatile boolean hudReported;
    private static final Set<MenuType<?>> OPENED_MENUS = ConcurrentHashMap.newKeySet();

    private ClientHooks() {}

    /** Head of {@code Options.load()}: adds mod key bindings before saved bindings are read. */
    public static void onOptionsLoad(Options options) {
        if (keyMappingsAdded) {
            return;
        }
        LayerState.context().ifPresent(context -> {
            List<KeyBindingDeclaration> declarations = context.declarations().keyBindings();
            if (declarations.isEmpty()) {
                return;
            }
            List<KeyMapping> added = new ArrayList<>();
            for (KeyBindingDeclaration declaration : declarations) {
                KeyMapping.Category category = CATEGORIES.computeIfAbsent(
                        declaration.ownerModId(),
                        modId -> KeyMapping.Category.register(Identifier.fromNamespaceAndPath(modId, "keybinds")));
                KeyMapping mapping = new KeyMapping(
                        "key." + declaration.id().namespace() + "."
                                + declaration.id().path(),
                        declaration.defaultKey(),
                        category);
                declaration.bind().accept(new KeyState() {
                    @Override
                    public boolean isDown() {
                        return mapping.isDown();
                    }

                    @Override
                    public boolean consumeClick() {
                        return mapping.consumeClick();
                    }
                });
                added.add(mapping);
            }
            try {
                KeyMapping[] all = Arrays.copyOf(options.keyMappings, options.keyMappings.length + added.size());
                for (int i = 0; i < added.size(); i++) {
                    all[options.keyMappings.length + i] = added.get(i);
                }
                Field field = Options.class.getDeclaredField("keyMappings");
                field.setAccessible(true);
                field.set(options, all);
                keyMappingsAdded = true;
                LOGGER.info("[Poly Mod Loader] Added {} mod key binding(s) to the controls", added.size());
            } catch (ReflectiveOperationException e) {
                LOGGER.error("[Poly Mod Loader] PLEXUS-CLIENT-004 could not add mod key bindings to the controls", e);
            }
        });
    }

    /** Before the return of {@code Minecraft.tick()}. */
    public static void onClientTick(Minecraft minecraft) {
        LayerState.context().ifPresent(context -> {
            if (context.events().hasListeners(ClientEvents.TICK)) {
                ClientView view = new MinecraftView(minecraft);
                context.events().publish(ClientEvents.TICK, listener -> listener.onClientTick(view));
            }
        });
    }

    /** Before the return of {@code Gui.extractRenderState(GuiGraphics, DeltaTracker)}. */
    public static void onHudRendered(Gui gui, GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        // 1.21.11 has no Hud class; the HUD is hidden with F1 through Options.hideGui (javap).
        if (minecraft.level == null || minecraft.options.hideGui) {
            return;
        }
        LayerState.context().ifPresent(context -> {
            List<HudLayerDeclaration> layers = context.declarations().hudLayers();
            if (layers.isEmpty()) {
                return;
            }
            HudCanvas canvas = new Canvas(graphics, minecraft.font);
            for (HudLayerDeclaration layer : layers) {
                String id = layer.id().toString();
                if (FAILED_LAYERS.contains(id)) {
                    continue;
                }
                try {
                    layer.layer().render(canvas);
                } catch (RuntimeException | LinkageError e) {
                    FAILED_LAYERS.add(id);
                    LOGGER.error(
                            "[Poly Mod Loader] PLEXUS-CLIENT-003 HUD layer {} of {} failed and is disabled",
                            id,
                            layer.ownerModId(),
                            e);
                }
            }
            if (!hudReported) {
                hudReported = true;
                LOGGER.info("[Poly Mod Loader] Drawing {} mod HUD layer(s)", layers.size());
            }
        });
    }

    /**
     * Head of {@code ClientConfigurationPacketListenerImpl.handleCustomPayload}: answers mods'
     * configuration exchanges; {@code true} when Poly Mod Loader consumed the payload.
     */
    public static boolean onConfigurationPayload(
            ClientConfigurationPacketListenerImpl listener, CustomPacketPayload payload) {
        if (!(payload instanceof PolyPayload polyPayload)) {
            return false;
        }
        ConfigurationEndpoint exchange = LayerState.configurationExchanges()
                .get(Views.id(polyPayload.type().id()));
        if (exchange == null) {
            return false;
        }
        exchange.answer(polyPayload.data()).ifPresent(answer -> {
            listener.send(new ServerboundCustomPayloadPacket(new PolyPayload(polyPayload.type(), answer)));
            LOGGER.info("[Poly Mod Loader] Answered configuration exchange {}", exchange.id());
        });
        return true;
    }

    /** Head of {@code ClientPacketListener.handleCustomPayload(CustomPacketPayload)}. */
    public static boolean onClientPayload(ClientPacketListener listener, CustomPacketPayload payload) {
        if (!(payload instanceof PolyPayload polyPayload)) {
            return false;
        }
        ChannelEndpoint endpoint =
                LayerState.channels().get(Views.id(polyPayload.type().id()));
        if (endpoint != null) {
            Minecraft.getInstance().execute(() -> endpoint.receiveOnClient(polyPayload.data()));
        }
        return true;
    }

    /**
     * Head of {@code MenuScreens.create}: opens the screen of a mod menu with its own layout; the
     * game's menus continue to the game's code.
     */
    public static boolean onCreateMenuScreen(MenuType<?> type, Minecraft minecraft, int containerId, Component title) {
        if (PlexusMenus.layout(type) == null || minecraft.player == null) {
            return false;
        }
        Inventory inventory = minecraft.player.getInventory();
        PlexusMenu menu = PlexusMenus.createForClient(type, containerId, inventory);
        minecraft.player.containerMenu = menu;
        minecraft.setScreen(new PlexusContainerScreen(menu, inventory, title));
        if (OPENED_MENUS.add(type)) {
            LOGGER.info(
                    "[Poly Mod Loader] Opened mod menu {} with {} slot(s)",
                    BuiltInRegistries.MENU.getKey(type),
                    menu.layout().slots().size());
        }
        return true;
    }

    /**
     * Before the return of {@code ParticleResources.registerProviders()}: adds providers for mod
     * particle types, whose sprites the game loads from {@code particles/<path>.json}. The game keeps
     * the registration method and its factory interface private.
     */
    public static void onRegisterParticleProviders(ParticleResources resources) {
        Map<ParticleType<?>, ParticleDefinition> particles = Particles.definitions();
        if (particles.isEmpty()) {
            return;
        }
        try {
            Class<?> registration = Class.forName(
                    ParticleResources.class.getName() + "$SpriteParticleRegistration",
                    false,
                    ParticleResources.class.getClassLoader());
            Method register = ParticleResources.class.getDeclaredMethod("register", ParticleType.class, registration);
            register.setAccessible(true);
            for (Map.Entry<ParticleType<?>, ParticleDefinition> entry : particles.entrySet()) {
                String name = String.valueOf(BuiltInRegistries.PARTICLE_TYPE.getKey(entry.getKey()));
                Object factory = Proxy.newProxyInstance(
                        ParticleResources.class.getClassLoader(),
                        new Class<?>[] {registration},
                        (proxy, method, arguments) -> switch (method.getName()) {
                            case "create" ->
                                new PlexusParticle.Provider((SpriteSet) arguments[0], entry.getValue(), name);
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == arguments[0];
                            case "toString" -> "PolyParticleRegistration[" + name + "]";
                            default -> throw new UnsupportedOperationException(method.getName());
                        });
                register.invoke(resources, entry.getKey(), factory);
            }
            LOGGER.info("[Poly Mod Loader] Added {} mod particle provider(s)", particles.size());
        } catch (ReflectiveOperationException e) {
            LOGGER.error("[Poly Mod Loader] PLEXUS-CLIENT-005 cannot add mod particle providers", e);
        }
    }

    /** Before the return of {@code BlockEntityRenderers.createEntityRenderers}: adds mod painters. */
    public static Map<BlockEntityType<?>, BlockEntityRenderer<?, ?>> onCreateBlockEntityRenderers(
            BlockEntityRendererProvider.Context context, Map<BlockEntityType<?>, BlockEntityRenderer<?, ?>> renderers) {
        List<BlockEntityRendererDeclaration> declarations = LayerState.context()
                .map(layer -> layer.declarations().blockEntityRenderers())
                .orElse(List.of());
        if (declarations.isEmpty()) {
            return renderers;
        }
        Map<BlockEntityType<?>, BlockEntityRenderer<?, ?>> all = new HashMap<>(renderers);
        for (BlockEntityRendererDeclaration declaration : declarations) {
            BlockEntityType<?> type = BuiltInRegistries.BLOCK_ENTITY_TYPE
                    .getOptional(Views.id(declaration.block()))
                    .orElseThrow(() -> new IllegalStateException(
                            "PLEXUS-CLIENT-002 " + declaration.block() + " has no block entity type"));
            all.put(type, new PlexusBlockEntityRenderer(context, declaration));
        }
        LOGGER.info("[Poly Mod Loader] Added {} mod block entity renderer(s)", declarations.size());
        return Map.copyOf(all);
    }

    /**
     * Head of {@code LevelRenderer.submitEntities}: uploads this frame's mod shader uniforms, then mods'
     * world painters draw this frame's shapes while the game's gizmo collection for the frame is open.
     * 1.21.11 has no {@code submitFeatures}; {@code submitEntities} is the first step 26.2's {@code
     * submitFeatures} runs, called once per frame before {@code finalizeGizmoCollection} (javap).
     */
    public static void onSubmitWorldFeatures(
            LevelRenderer renderer, PoseStack poseStack, LevelRenderState state, SubmitNodeCollector collector) {
        ModShaderPipelines.uploadUniforms();
        LayerState.context().ifPresent(context -> {
            List<NativeDeclarations.WorldPainterDeclaration> painters =
                    context.declarations().worldPainters();
            if (!painters.isEmpty()) {
                WorldPainting.paint(painters, state, collector);
            }
        });
    }

    /**
     * Before the return of {@code GlRenderPass.setPipeline(RenderPipeline)}: binds a mod pipeline's
     * {@code ModUniforms} buffer. Runs for every pipeline switch in the game.
     */
    public static void onSetPipeline(GlRenderPass pass, RenderPipeline pipeline) {
        ModShaderPipelines.bindUniforms(pass, pipeline);
    }

    /** Before the return of {@code GameRenderer.getFov(Camera, float, boolean)}: mods may change the field of view. */
    public static float onGetFov(
            GameRenderer renderer, Camera camera, float partialTicks, boolean useFovSetting, float fov) {
        GameLayerContext context = LayerState.context().orElse(null);
        if (context == null || !context.events().hasListeners(ClientEvents.FIELD_OF_VIEW)) {
            return fov;
        }
        float[] result = {fov};
        context.events().publish(ClientEvents.FIELD_OF_VIEW, listener -> result[0] = listener.modifyFov(result[0]));
        return Float.isFinite(result[0]) && result[0] > 0 ? result[0] : fov;
    }

    /**
     * Before the return of {@code ReloadableResourceManager(PackType)}: the client's resource manager
     * gets the mod asset listener.
     */
    public static void onResourceManagerCreated(ReloadableResourceManager manager, PackType type) {
        if (type != PackType.CLIENT_RESOURCES) {
            return;
        }
        List<NativeDeclarations.ReloadListenerDeclaration> assets = LayerState.context()
                .map(context -> context.declarations().reloadListeners().stream()
                        .filter(NativeDeclarations.ReloadListenerDeclaration::assets)
                        .toList())
                .orElse(List.of());
        if (!assets.isEmpty()) {
            manager.registerReloadListener(new ModReloadListener(assets));
        }
    }

    /** Whether {@link ClientEvents#JOINED_WORLD} fired for the current connection. */
    private static volatile boolean joined;

    /** Before the return of {@code ClientPacketListener.handleLogin}. */
    public static void onClientLogin(ClientPacketListener listener, ClientboundLoginPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (joined || minecraft.player == null) {
            return;
        }
        joined = true;
        SyncChannel.setLocalPlayerCheck(entity -> entity == Minecraft.getInstance().player);
        LayerState.context()
                .ifPresent(context -> context.events()
                        .publish(ClientEvents.JOINED_WORLD, l -> l.onConnection(new MinecraftView(minecraft))));
    }

    /** Head of {@code Minecraft.disconnect(Screen, boolean, boolean)}. */
    public static void onClientDisconnect(
            Minecraft minecraft, Screen screen, boolean keepResourcePacks, boolean stopSound) {
        if (!joined) {
            return;
        }
        joined = false;
        try {
            LayerState.context()
                    .ifPresent(context -> context.events()
                            .publish(ClientEvents.LEFT_WORLD, l -> l.onConnection(new MinecraftView(minecraft))));
        } finally {
            SyncChannel.clearClientCopies();
        }
    }

    /** Sends a packet over the client's connection. */
    public static void sendToServer(ServerboundCustomPayloadPacket packet) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            throw new IllegalStateException("Not connected to a server");
        }
        connection.send(packet);
    }

    private record MinecraftView(Minecraft minecraft) implements ClientView {

        @Override
        public Optional<PlayerView> player() {
            return Optional.ofNullable(minecraft.player).map(Views::player);
        }

        @Override
        public Optional<WorldView> world() {
            return Optional.ofNullable(minecraft.level).map(Views::world);
        }

        @Override
        public void showActionBar(String text) {
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(Component.literal(text), true);
            }
        }

        @Override
        public void openScreen(ModScreen screen) {
            minecraft.setScreen(new PlexusScreen(screen));
        }

        @Override
        public void closeScreen() {
            minecraft.setScreen(null);
        }

        @Override
        public boolean isScreenOpen() {
            return minecraft.screen != null;
        }

        @Override
        public CameraPerspective cameraPerspective() {
            return switch (minecraft.options.getCameraType()) {
                case FIRST_PERSON -> CameraPerspective.FIRST_PERSON;
                case THIRD_PERSON_BACK -> CameraPerspective.THIRD_PERSON_BACK;
                case THIRD_PERSON_FRONT -> CameraPerspective.THIRD_PERSON_FRONT;
            };
        }

        @Override
        public void setCameraPerspective(CameraPerspective perspective) {
            minecraft.options.setCameraType(CameraType.valueOf(perspective.name()));
        }

        @Override
        public boolean setPostEffect(ResourceId effect) {
            Identifier id = Views.id(effect);
            if (minecraft.getShaderManager().getPostChain(id, LevelTargetBundle.MAIN_TARGETS) == null) {
                return false;
            }
            try {
                Method set = GameRenderer.class.getDeclaredMethod("setPostEffect", Identifier.class);
                set.setAccessible(true);
                set.invoke(minecraft.gameRenderer, id);
                return true;
            } catch (ReflectiveOperationException e) {
                LOGGER.error("[Poly Mod Loader] PLEXUS-CLIENT-005 cannot apply post effect {}", effect, e);
                return false;
            }
        }

        @Override
        public void clearPostEffect() {
            minecraft.gameRenderer.clearPostEffect();
        }

        @Override
        public Optional<ResourceId> postEffect() {
            return Optional.ofNullable(minecraft.gameRenderer.currentPostEffect())
                    .map(Views::id);
        }

        @Override
        public void leaveWorld() {
            if (minecraft.level != null) {
                minecraft.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE);
            }
        }
    }

    record Canvas(GuiGraphics graphics, Font font) implements HudCanvas {

        @Override
        public int width() {
            return graphics.guiWidth();
        }

        @Override
        public int height() {
            return graphics.guiHeight();
        }

        @Override
        public int textWidth(String text) {
            return font.width(text);
        }

        @Override
        public void drawText(String text, int x, int y, int argb) {
            graphics.drawString(font, text, x, y, argb, true);
        }

        @Override
        public void fill(int x1, int y1, int x2, int y2, int argb) {
            graphics.fill(x1, y1, x2, y2, argb);
        }

        @Override
        public void drawTexture(ResourceId texture, int x, int y, int width, int height) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, Views.id(texture), x, y, 0.0f, 0.0f, width, height, 1, 1, 1, 1);
        }
    }

    /** Before the return of {@code EntityRenderers.createEntityRenderers}: adds renderers for mod mobs. */
    public static Map<EntityType<?>, EntityRenderer<?, ?>> onCreateEntityRenderers(
            EntityRendererProvider.Context context, Map<EntityType<?>, EntityRenderer<?, ?>> renderers) {
        Map<EntityType<?>, ResourceId> textures = EntityInstaller.textures();
        Set<EntityType<?>> projectiles = EntityInstaller.projectileTypes();
        if (textures.isEmpty() && projectiles.isEmpty()) {
            return renderers;
        }
        Map<EntityType<?>, EntityRenderer<?, ?>> all = new HashMap<>(renderers);
        Map<EntityType<?>, EntityDefinition> definitions = EntityInstaller.definitions();
        textures.forEach((type, texture) -> {
            EntityDefinition definition = definitions.get(type);
            all.put(
                    type,
                    definition != null && definition.model().isPresent()
                            ? new PlexusModelRenderer(
                                    context,
                                    PlexusEntityModel.create(definition.model().get()),
                                    Views.id(texture),
                                    definition.width() * 0.5f,
                                    String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(type)))
                            : new PlexusHumanoidRenderer(context, Views.id(texture)));
        });
        projectiles.forEach(type -> all.put(type, new ThrownItemRenderer<>(context)));
        LOGGER.info("[Poly Mod Loader] Added {} mod entity renderer(s)", textures.size() + projectiles.size());
        return Map.copyOf(all);
    }
}
