package dev.polymodloader.runtime.nativeapi;

import dev.polymodloader.api.client.BlockEntityPainter;
import dev.polymodloader.api.client.ClientRegistrar;
import dev.polymodloader.api.client.DynamicTexture;
import dev.polymodloader.api.client.HudLayer;
import dev.polymodloader.api.client.KeyBinding;
import dev.polymodloader.api.client.ShaderPipeline;
import dev.polymodloader.api.client.ShaderPipelineDefinition;
import dev.polymodloader.api.client.WorldPainter;
import dev.polymodloader.api.command.CommandRegistrar;
import dev.polymodloader.api.command.LiteralCommand;
import dev.polymodloader.api.content.AdvancementTrigger;
import dev.polymodloader.api.content.ContentHandle;
import dev.polymodloader.api.content.ContentRegistrar;
import dev.polymodloader.api.content.CreativeTabDefinition;
import dev.polymodloader.api.content.EntityDataKey;
import dev.polymodloader.api.content.EntityDataOptions;
import dev.polymodloader.api.content.SoundHandle;
import dev.polymodloader.api.content.block.BlockDefinition;
import dev.polymodloader.api.content.block.BlockHandle;
import dev.polymodloader.api.content.effect.EffectDefinition;
import dev.polymodloader.api.content.effect.EffectHandle;
import dev.polymodloader.api.content.entity.EntityDefinition;
import dev.polymodloader.api.content.entity.EntityHandle;
import dev.polymodloader.api.content.entity.ProjectileDefinition;
import dev.polymodloader.api.content.item.ComponentType;
import dev.polymodloader.api.content.item.ItemComponentKey;
import dev.polymodloader.api.content.item.ItemDefinition;
import dev.polymodloader.api.content.item.ItemHandle;
import dev.polymodloader.api.content.particle.ParticleDefinition;
import dev.polymodloader.api.content.rule.GameRuleCategory;
import dev.polymodloader.api.content.rule.GameRuleKey;
import dev.polymodloader.api.devtools.gametest.GameTest;
import dev.polymodloader.api.devtools.gametest.GameTestRegistrar;
import dev.polymodloader.api.devtools.gametest.TestArea;
import dev.polymodloader.api.network.ConfigurationExchange;
import dev.polymodloader.api.network.NetworkRegistrar;
import dev.polymodloader.api.network.PayloadChannel;
import dev.polymodloader.api.network.PayloadCodec;
import dev.polymodloader.api.permission.PermissionNode;
import dev.polymodloader.api.permission.PermissionProvider;
import dev.polymodloader.api.permission.PermissionRegistrar;
import dev.polymodloader.api.resource.BinaryReloadListener;
import dev.polymodloader.api.resource.ResourceId;
import dev.polymodloader.api.resource.ResourceRegistrar;
import dev.polymodloader.api.resource.ResourceReloadListener;
import dev.polymodloader.api.runtime.GameSide;
import dev.polymodloader.api.tag.TagKey;
import dev.polymodloader.api.worldgen.BiomeDefinition;
import dev.polymodloader.api.worldgen.BiomeSelector;
import dev.polymodloader.api.worldgen.CodeFeature;
import dev.polymodloader.api.worldgen.FeaturePlacement;
import dev.polymodloader.api.worldgen.GenerationStepName;
import dev.polymodloader.api.worldgen.NoiseFunction;
import dev.polymodloader.api.worldgen.StructureDefinition;
import dev.polymodloader.api.worldgen.TerrainGenerator;
import dev.polymodloader.api.worldgen.WorldgenRegistrar;
import dev.polymodloader.game.version.layer.ChannelEndpoint;
import dev.polymodloader.game.version.layer.ConfigurationEndpoint;
import dev.polymodloader.game.version.layer.NativeDeclarations;
import dev.polymodloader.game.version.layer.NativeDeclarations.CodeFeatureDeclaration;
import dev.polymodloader.game.version.layer.NativeDeclarations.CreativeTabDeclaration;
import dev.polymodloader.game.version.layer.NativeDeclarations.DensityFunctionDeclaration;
import dev.polymodloader.game.version.layer.NativeDeclarations.ShaderPipelineDeclaration;
import dev.polymodloader.game.version.layer.NativeDeclarations.StructureDeclaration;
import dev.polymodloader.game.version.layer.NativeDeclarations.TagEntriesDeclaration;
import dev.polymodloader.game.version.layer.NativeDeclarations.WorldPainterDeclaration;
import dev.polymodloader.game.version.layer.PayloadTransport;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Collects what mods declare through the Native API while their entrypoints run, validates it
 * eagerly so mistakes surface in the entrypoint that made them, and hands it to the game layer.
 * Not thread-safe: entrypoints run on one thread.
 */
public final class NativeRegistrations implements NativeDeclarations {

    private static final Logger LOGGER = LoggerFactory.getLogger("Poly Mod Loader");
    private static final Pattern DIRECTORY = Pattern.compile("[a-z0-9_./-]+");
    private static final Pattern SUFFIX = Pattern.compile("\\.[a-z0-9_]+(\\.[a-z0-9_]+)*");
    private static final Pattern PERMISSION = Pattern.compile("[a-z0-9_]+(\\.[a-z0-9_]+)*");

    private final GameSide side;
    private final List<BlockDeclaration> blocks = new ArrayList<>();
    private final List<ItemDeclaration> items = new ArrayList<>();
    private final List<EntityDeclaration> entities = new ArrayList<>();
    private final List<EffectDeclaration> effects = new ArrayList<>();
    private final List<GameRuleKey<?>> gameRules = new ArrayList<>();
    private final List<SoundDeclaration> sounds = new ArrayList<>();
    private final List<ProjectileDeclaration> projectiles = new ArrayList<>();
    private final List<ItemComponentKey<?>> itemComponents = new ArrayList<>();
    private final Set<ResourceId> componentIds = new HashSet<>();
    private final List<AdvancementTriggerDeclaration> advancementTriggers = new ArrayList<>();
    private final Set<ResourceId> triggerIds = new HashSet<>();
    private final List<ChunkGeneratorDeclaration> chunkGenerators = new ArrayList<>();
    private final List<BiomeDeclaration> biomes = new ArrayList<>();
    private final List<CodeFeatureDeclaration> codeFeatures = new ArrayList<>();
    private final List<DensityFunctionDeclaration> densityFunctions = new ArrayList<>();
    private final List<StructureDeclaration> structures = new ArrayList<>();
    private final Set<ResourceId> worldgenIds = new HashSet<>();
    private final List<ParticleDeclaration> particles = new ArrayList<>();
    private final Set<ResourceId> particleIds = new HashSet<>();
    private final List<BlockEntityRendererDeclaration> blockEntityRenderers = new ArrayList<>();
    private final List<WorldPainterDeclaration> worldPainters = new ArrayList<>();
    private final List<ShaderPipelineDeclaration> shaderPipelines = new ArrayList<>();
    private final Set<ResourceId> paintedBlocks = new HashSet<>();
    private final List<DynamicTextureDeclaration> dynamicTextures = new ArrayList<>();
    private final List<ReloadListenerDeclaration> reloadListeners = new ArrayList<>();
    private final List<EntityDataKey> entityData = new ArrayList<>();
    private final Set<ResourceId> entityDataIds = new HashSet<>();
    private final List<PermissionProvider> permissionProviders = new ArrayList<>();
    private final Set<String> permissionIds = new HashSet<>();
    private final List<FeatureDeclaration> features = new ArrayList<>();
    private final List<CommandDeclaration> commands = new ArrayList<>();
    private final List<ChannelEndpoint> channels = new ArrayList<>();
    private final List<ConfigurationExchangeImpl<?>> configurationExchanges = new ArrayList<>();
    private final List<GameTestDeclaration> gameTests = new ArrayList<>();
    private final List<KeyBindingDeclaration> keyBindings = new ArrayList<>();
    private final List<HudLayerDeclaration> hudLayers = new ArrayList<>();
    private final List<TagEntriesDeclaration> tagEntries = new ArrayList<>();
    private final List<CreativeTabDeclaration> creativeTabs = new ArrayList<>();
    private final Set<ResourceId> creativeTabIds = new HashSet<>();
    private final Set<ResourceId> contentIds = new HashSet<>();
    private final Set<ResourceId> channelIds = new HashSet<>();
    private final Set<ResourceId> effectIds = new HashSet<>();
    private final Set<ResourceId> ruleIds = new HashSet<>();
    private final Set<ResourceId> soundIds = new HashSet<>();
    private final Set<ResourceId> testIds = new HashSet<>();
    private final Set<ResourceId> clientIds = new HashSet<>();
    private final Map<String, String> commandOwners = new HashMap<>();
    private final TransportHolder transport = new TransportHolder();
    private volatile boolean closed;

    public NativeRegistrations(GameSide side) {
        this.side = side;
    }

    /** The registrars handed to {@code modId}'s entrypoints. */
    public ModRegistrars forMod(String modId) {
        return new ModRegistrars(modId);
    }

    @Override
    public List<BlockDeclaration> blocks() {
        return List.copyOf(blocks);
    }

    @Override
    public List<ItemDeclaration> items() {
        return List.copyOf(items);
    }

    @Override
    public List<EntityDeclaration> entities() {
        return List.copyOf(entities);
    }

    @Override
    public List<EffectDeclaration> effects() {
        return List.copyOf(effects);
    }

    @Override
    public List<TagEntriesDeclaration> tagEntries() {
        return List.copyOf(tagEntries);
    }

    @Override
    public List<CreativeTabDeclaration> creativeTabs() {
        return List.copyOf(creativeTabs);
    }

    @Override
    public List<GameRuleKey<?>> gameRules() {
        return List.copyOf(gameRules);
    }

    @Override
    public List<SoundDeclaration> sounds() {
        return List.copyOf(sounds);
    }

    @Override
    public List<ProjectileDeclaration> projectiles() {
        return List.copyOf(projectiles);
    }

    @Override
    public List<ItemComponentKey<?>> itemComponents() {
        return List.copyOf(itemComponents);
    }

    @Override
    public List<AdvancementTriggerDeclaration> advancementTriggers() {
        return List.copyOf(advancementTriggers);
    }

    @Override
    public List<ChunkGeneratorDeclaration> chunkGenerators() {
        return List.copyOf(chunkGenerators);
    }

    @Override
    public List<BiomeDeclaration> biomes() {
        return List.copyOf(biomes);
    }

    @Override
    public List<CodeFeatureDeclaration> codeFeatures() {
        return List.copyOf(codeFeatures);
    }

    @Override
    public List<DensityFunctionDeclaration> densityFunctions() {
        return List.copyOf(densityFunctions);
    }

    @Override
    public List<StructureDeclaration> structures() {
        return List.copyOf(structures);
    }

    @Override
    public List<ParticleDeclaration> particles() {
        return List.copyOf(particles);
    }

    @Override
    public List<BlockEntityRendererDeclaration> blockEntityRenderers() {
        return List.copyOf(blockEntityRenderers);
    }

    @Override
    public List<WorldPainterDeclaration> worldPainters() {
        return List.copyOf(worldPainters);
    }

    @Override
    public List<ShaderPipelineDeclaration> shaderPipelines() {
        return List.copyOf(shaderPipelines);
    }

    @Override
    public List<DynamicTextureDeclaration> dynamicTextures() {
        return List.copyOf(dynamicTextures);
    }

    @Override
    public List<ReloadListenerDeclaration> reloadListeners() {
        return List.copyOf(reloadListeners);
    }

    @Override
    public List<EntityDataKey> entityData() {
        return List.copyOf(entityData);
    }

    @Override
    public List<PermissionProvider> permissionProviders() {
        return List.copyOf(permissionProviders);
    }

    @Override
    public List<FeatureDeclaration> features() {
        return List.copyOf(features);
    }

    @Override
    public List<CommandDeclaration> commands() {
        return List.copyOf(commands);
    }

    @Override
    public List<ChannelEndpoint> channels() {
        return List.copyOf(channels);
    }

    @Override
    public List<ConfigurationEndpoint> configurationExchanges() {
        return List.copyOf(configurationExchanges);
    }

    @Override
    public List<GameTestDeclaration> gameTests() {
        return List.copyOf(gameTests);
    }

    @Override
    public List<KeyBindingDeclaration> keyBindings() {
        return List.copyOf(keyBindings);
    }

    @Override
    public List<HudLayerDeclaration> hudLayers() {
        return List.copyOf(hudLayers);
    }

    @Override
    public void bindTransport(PayloadTransport payloadTransport) {
        transport.set(payloadTransport);
    }

    @Override
    public void close() {
        closed = true;
    }

    /** Number of declarations of each kind, for the startup log. */
    public String summary() {
        return blocks.size() + " block(s), " + items.size() + " item(s), " + entities.size() + " entity type(s), "
                + effects.size() + " effect(s), " + gameRules.size() + " game rule(s), " + sounds.size() + " sound(s), "
                + commands.size() + " command(s), "
                + channels.size() + " channel(s), " + gameTests.size() + " game test(s), " + keyBindings.size()
                + " key binding(s), " + hudLayers.size() + " HUD layer(s), " + projectiles.size() + " projectile(s), "
                + itemComponents.size() + " item component(s), " + advancementTriggers.size()
                + " advancement trigger(s), " + chunkGenerators.size() + " chunk generator(s), " + biomes.size()
                + " biome(s), " + particles.size() + " particle type(s), " + blockEntityRenderers.size()
                + " block entity renderer(s), " + dynamicTextures.size() + " dynamic texture(s), "
                + reloadListeners.size() + " reload listener(s), " + entityData.size() + " entity data key(s), "
                + permissionIds.size() + " permission(s), " + codeFeatures.size() + " code feature(s), "
                + densityFunctions.size() + " density function(s), " + structures.size() + " structure(s), "
                + worldPainters.size() + " world painter(s), " + shaderPipelines.size() + " shader pipeline(s), "
                + configurationExchanges.size() + " configuration exchange(s), " + creativeTabs.size()
                + " creative tab(s), " + tagEntries.size() + " tag addition(s)";
    }

    private void checkOpen(String code, String what) {
        if (closed) {
            throw new IllegalStateException(
                    code + " " + what + " must be declared in a mod entrypoint; registration is closed");
        }
    }

    private static ResourceId idFor(String modId, String path, String code) {
        try {
            return ResourceId.of(modId, path);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(code + " " + e.getMessage(), e);
        }
    }

    private static void requireNew(Set<ResourceId> used, ResourceId id, String code, String what) {
        if (!used.add(id)) {
            throw new IllegalArgumentException(code + " " + what + " " + id + " is already declared");
        }
    }

    /** One mod's view of the registrations; every ID is in the mod's namespace. */
    public final class ModRegistrars
            implements ContentRegistrar,
                    ResourceRegistrar,
                    PermissionRegistrar,
                    CommandRegistrar,
                    NetworkRegistrar,
                    GameTestRegistrar,
                    ClientRegistrar,
                    WorldgenRegistrar {

        private final String modId;

        private ModRegistrars(String modId) {
            this.modId = modId;
        }

        @Override
        public BlockHandle block(String path, BlockDefinition definition) {
            Objects.requireNonNull(definition, "definition");
            checkOpen("PLEXUS-CONTENT-002", "Block " + modId + ":" + path);
            ResourceId id = idFor(modId, path, "PLEXUS-CONTENT-001");
            requireNew(contentIds, id, "PLEXUS-CONTENT-001", "block");
            ContentHandleImpl handle = new ContentHandleImpl(id);
            blocks.add(new BlockDeclaration(modId, id, definition, handle::markRegistered));
            definition.item().ifPresent(item -> items.add(new ItemDeclaration(modId, id, item, true, () -> {})));
            return handle;
        }

        @Override
        public ItemHandle item(String path, ItemDefinition definition) {
            Objects.requireNonNull(definition, "definition");
            checkOpen("PLEXUS-CONTENT-002", "Item " + modId + ":" + path);
            ResourceId id = idFor(modId, path, "PLEXUS-CONTENT-001");
            requireNew(contentIds, id, "PLEXUS-CONTENT-001", "item");
            ContentHandleImpl handle = new ContentHandleImpl(id);
            items.add(new ItemDeclaration(modId, id, definition, false, handle::markRegistered));
            return handle;
        }

        @Override
        public EntityHandle entity(String path, EntityDefinition definition) {
            Objects.requireNonNull(definition, "definition");
            checkOpen("PLEXUS-CONTENT-002", "Entity " + modId + ":" + path);
            ResourceId id = idFor(modId, path, "PLEXUS-CONTENT-001");
            requireNew(contentIds, id, "PLEXUS-CONTENT-001", "entity type");
            ContentHandleImpl handle = new ContentHandleImpl(id);
            entities.add(new EntityDeclaration(modId, id, definition, handle::markRegistered));
            if (definition.spawnEggTab().isPresent()) {
                ResourceId egg = idFor(modId, path + "_spawn_egg", "PLEXUS-CONTENT-001");
                requireNew(contentIds, egg, "PLEXUS-CONTENT-001", "item");
            }
            return handle;
        }

        @Override
        public EffectHandle effect(String path, EffectDefinition definition) {
            Objects.requireNonNull(definition, "definition");
            checkOpen("PLEXUS-CONTENT-002", "Effect " + modId + ":" + path);
            ResourceId id = idFor(modId, path, "PLEXUS-CONTENT-001");
            requireNew(effectIds, id, "PLEXUS-CONTENT-001", "effect");
            ContentHandleImpl handle = new ContentHandleImpl(id);
            effects.add(new EffectDeclaration(modId, id, definition, handle::markRegistered));
            return handle;
        }

        @Override
        public GameRuleKey<Boolean> booleanRule(String path, boolean defaultValue, GameRuleCategory category) {
            return addRule(new GameRuleKey<>(
                    ruleId(path), Boolean.class, defaultValue, 0, 1, Objects.requireNonNull(category, "category")));
        }

        @Override
        public GameRuleKey<Integer> integerRule(
                String path, int defaultValue, int min, int max, GameRuleCategory category) {
            return addRule(new GameRuleKey<>(ruleId(path), Integer.class, defaultValue, min, max, category));
        }

        @Override
        public SoundHandle sound(String path) {
            checkOpen("PLEXUS-CONTENT-002", "Sound " + modId + ":" + path);
            ResourceId id = idFor(modId, path, "PLEXUS-CONTENT-001");
            requireNew(soundIds, id, "PLEXUS-CONTENT-001", "sound");
            ContentHandleImpl handle = new ContentHandleImpl(id);
            sounds.add(new SoundDeclaration(modId, id, handle::markRegistered));
            return handle;
        }

        @Override
        public EntityHandle projectile(String path, ProjectileDefinition definition) {
            Objects.requireNonNull(definition, "definition");
            checkOpen("PLEXUS-CONTENT-002", "Projectile " + modId + ":" + path);
            ResourceId id = idFor(modId, path, "PLEXUS-CONTENT-001");
            requireNew(contentIds, id, "PLEXUS-CONTENT-001", "projectile type");
            ContentHandleImpl handle = new ContentHandleImpl(id);
            projectiles.add(new ProjectileDeclaration(modId, id, definition, handle::markRegistered));
            return handle;
        }

        @Override
        public <T> ItemComponentKey<T> itemComponent(String path, ComponentType<T> type) {
            Objects.requireNonNull(type, "type");
            checkOpen("PLEXUS-CONTENT-002", "Item component " + modId + ":" + path);
            ResourceId id = idFor(modId, path, "PLEXUS-CONTENT-001");
            requireNew(componentIds, id, "PLEXUS-CONTENT-001", "item component");
            ItemComponentKey<T> key = new ItemComponentKey<>(id, type);
            itemComponents.add(key);
            return key;
        }

        @Override
        public AdvancementTrigger advancementTrigger(String path) {
            checkOpen("PLEXUS-CONTENT-002", "Advancement trigger " + modId + ":" + path);
            ResourceId id = idFor(modId, path, "PLEXUS-CONTENT-001");
            requireNew(triggerIds, id, "PLEXUS-CONTENT-001", "advancement trigger");
            AdvancementTriggerImpl trigger = new AdvancementTriggerImpl(id);
            advancementTriggers.add(new AdvancementTriggerDeclaration(modId, id, trigger::bind));
            return trigger;
        }

        @Override
        public ContentHandle particle(String path, ParticleDefinition definition) {
            Objects.requireNonNull(definition, "definition");
            checkOpen("PLEXUS-CONTENT-002", "Particle " + modId + ":" + path);
            ResourceId id = idFor(modId, path, "PLEXUS-CONTENT-001");
            requireNew(particleIds, id, "PLEXUS-CONTENT-001", "particle type");
            ContentHandleImpl handle = new ContentHandleImpl(id);
            particles.add(new ParticleDeclaration(modId, id, definition, handle::markRegistered));
            return handle;
        }

        @Override
        public void addToTag(TagKey tag, ResourceId... entries) {
            Objects.requireNonNull(tag, "tag");
            Objects.requireNonNull(entries, "entries");
            checkOpen("PLEXUS-CONTENT-002", "Entries of tag " + tag);
            if (entries.length == 0) {
                throw new IllegalArgumentException("PLEXUS-CONTENT-001 addToTag(" + tag + ") needs at least one entry");
            }
            List<ResourceId> list = List.of(entries); // rejects null entries
            tagEntries.add(new TagEntriesDeclaration(modId, tag, list));
        }

        @Override
        public ContentHandle creativeTab(String path, CreativeTabDefinition definition) {
            Objects.requireNonNull(definition, "definition");
            checkOpen("PLEXUS-CONTENT-002", "Creative tab " + modId + ":" + path);
            ResourceId id = idFor(modId, path, "PLEXUS-CONTENT-001");
            requireNew(creativeTabIds, id, "PLEXUS-CONTENT-001", "creative tab");
            ContentHandleImpl handle = new ContentHandleImpl(id);
            creativeTabs.add(new CreativeTabDeclaration(modId, id, definition, handle::markRegistered));
            return handle;
        }

        @Override
        public EntityDataKey entityData(String path, EntityDataOptions options) {
            Objects.requireNonNull(options, "options");
            checkOpen("PLEXUS-CONTENT-002", "Entity data " + modId + ":" + path);
            ResourceId id = idFor(modId, path, "PLEXUS-CONTENT-001");
            requireNew(entityDataIds, id, "PLEXUS-CONTENT-001", "entity data key");
            EntityDataKey key = new EntityDataKey(id, options);
            entityData.add(key);
            return key;
        }

        @Override
        public void onDataReload(String directory, ResourceReloadListener listener) {
            addReloadListener(false, directory, ".json", json(listener));
        }

        @Override
        public void onAssetReload(String directory, ResourceReloadListener listener) {
            addReloadListener(true, directory, ".json", json(listener));
        }

        @Override
        public void onDataFiles(String directory, String suffix, BinaryReloadListener listener) {
            addReloadListener(false, directory, suffix, listener);
        }

        @Override
        public void onAssetFiles(String directory, String suffix, BinaryReloadListener listener) {
            addReloadListener(true, directory, suffix, listener);
        }

        private static BinaryReloadListener json(ResourceReloadListener listener) {
            Objects.requireNonNull(listener, "listener");
            return files -> {
                Map<ResourceId, String> text = new java.util.LinkedHashMap<>();
                files.forEach((id, bytes) -> text.put(id, new String(bytes, java.nio.charset.StandardCharsets.UTF_8)));
                listener.onReload(Map.copyOf(text));
            };
        }

        private void addReloadListener(boolean assets, String directory, String suffix, BinaryReloadListener listener) {
            Objects.requireNonNull(listener, "listener");
            if (suffix == null || !SUFFIX.matcher(suffix).matches()) {
                throw new IllegalArgumentException(
                        "PLEXUS-RESOURCE-001 invalid file suffix '" + suffix + "': use a dot and a-z 0-9 _");
            }
            checkOpen("PLEXUS-RESOURCE-002", "Reload listener for " + directory);
            if (directory == null
                    || !DIRECTORY.matcher(directory).matches()
                    || directory.startsWith("/")
                    || directory.endsWith("/")
                    || directory.contains("..")) {
                throw new IllegalArgumentException("PLEXUS-RESOURCE-001 invalid directory '" + directory
                        + "': use a-z 0-9 _ . - and / between names");
            }
            reloadListeners.add(new ReloadListenerDeclaration(modId, assets, directory, suffix, listener));
        }

        @Override
        public PermissionNode node(String path, int defaultLevel) {
            checkOpen("PLEXUS-PERMISSION-002", "Permission " + modId + "." + path);
            if (path == null || !PERMISSION.matcher(path).matches()) {
                throw new IllegalArgumentException(
                        "PLEXUS-PERMISSION-001 invalid permission '" + path + "': use a-z 0-9 _ and dots");
            }
            String id = modId + "." + path;
            if (!permissionIds.add(id)) {
                throw new IllegalArgumentException("PLEXUS-PERMISSION-001 permission " + id + " is already declared");
            }
            return new PermissionNode(id, defaultLevel);
        }

        @Override
        public void provider(PermissionProvider provider) {
            Objects.requireNonNull(provider, "provider");
            checkOpen("PLEXUS-PERMISSION-002", "Permission provider of " + modId);
            permissionProviders.add(provider);
        }

        private ResourceId ruleId(String path) {
            checkOpen("PLEXUS-CONTENT-002", "Game rule " + modId + ":" + path);
            ResourceId id = idFor(modId, path, "PLEXUS-CONTENT-001");
            requireNew(ruleIds, id, "PLEXUS-CONTENT-001", "game rule");
            return id;
        }

        private <T> GameRuleKey<T> addRule(GameRuleKey<T> key) {
            gameRules.add(key);
            return key;
        }

        @Override
        public void addFeature(BiomeSelector biomes, GenerationStepName step, ResourceId placedFeature) {
            Objects.requireNonNull(biomes, "biomes");
            Objects.requireNonNull(step, "step");
            Objects.requireNonNull(placedFeature, "placedFeature");
            checkOpen("PLEXUS-WORLDGEN-002", "Feature " + placedFeature);
            features.add(new FeatureDeclaration(modId, biomes, step, placedFeature));
        }

        @Override
        public ResourceId chunkGenerator(String path, TerrainGenerator generator) {
            Objects.requireNonNull(generator, "generator");
            checkOpen("PLEXUS-WORLDGEN-002", "Chunk generator " + modId + ":" + path);
            ResourceId id = idFor(modId, path, "PLEXUS-WORLDGEN-004");
            requireNew(worldgenIds, id, "PLEXUS-WORLDGEN-004", "chunk generator");
            chunkGenerators.add(new ChunkGeneratorDeclaration(modId, id, generator));
            return id;
        }

        @Override
        public ResourceId biome(String path, BiomeDefinition definition) {
            Objects.requireNonNull(definition, "definition");
            checkOpen("PLEXUS-WORLDGEN-002", "Biome " + modId + ":" + path);
            ResourceId id = idFor(modId, path, "PLEXUS-WORLDGEN-004");
            requireNew(worldgenIds, ResourceId.of(modId, "biome/" + path), "PLEXUS-WORLDGEN-004", "biome");
            biomes.add(new BiomeDeclaration(modId, id, definition));
            return id;
        }

        @Override
        public ResourceId feature(String path, FeaturePlacement placement, CodeFeature feature) {
            Objects.requireNonNull(placement, "placement");
            Objects.requireNonNull(feature, "feature");
            checkOpen("PLEXUS-WORLDGEN-002", "Feature " + modId + ":" + path);
            ResourceId id = idFor(modId, path, "PLEXUS-WORLDGEN-004");
            requireNew(worldgenIds, ResourceId.of(modId, "feature/" + path), "PLEXUS-WORLDGEN-004", "feature");
            codeFeatures.add(new CodeFeatureDeclaration(modId, id, placement, feature));
            return id;
        }

        @Override
        public ResourceId densityFunction(String path, double minValue, double maxValue, NoiseFunction function) {
            Objects.requireNonNull(function, "function");
            if (!(minValue <= maxValue)) {
                throw new IllegalArgumentException("PLEXUS-WORLDGEN-004 density function " + modId + ":" + path
                        + " has minValue " + minValue + " > maxValue " + maxValue);
            }
            checkOpen("PLEXUS-WORLDGEN-002", "Density function " + modId + ":" + path);
            ResourceId id = idFor(modId, path, "PLEXUS-WORLDGEN-004");
            requireNew(
                    worldgenIds,
                    ResourceId.of(modId, "density_function/" + path),
                    "PLEXUS-WORLDGEN-004",
                    "density function");
            densityFunctions.add(new DensityFunctionDeclaration(modId, id, minValue, maxValue, function));
            return id;
        }

        @Override
        public ResourceId structure(String path, StructureDefinition definition) {
            Objects.requireNonNull(definition, "definition");
            checkOpen("PLEXUS-WORLDGEN-002", "Structure " + modId + ":" + path);
            ResourceId id = idFor(modId, path, "PLEXUS-WORLDGEN-004");
            requireNew(worldgenIds, ResourceId.of(modId, "structure/" + path), "PLEXUS-WORLDGEN-004", "structure");
            structures.add(new StructureDeclaration(modId, id, definition));
            return id;
        }

        @Override
        public void register(LiteralCommand command) {
            Objects.requireNonNull(command, "command");
            checkOpen("PLEXUS-COMMAND-002", "Command /" + command.name());
            String previous = commandOwners.putIfAbsent(command.name(), modId);
            if (previous != null && !previous.equals(modId)) {
                LOGGER.warn(
                        "[Poly Mod Loader] PLEXUS-COMMAND-001 /{} is registered by both {} and {}; the game merges their trees",
                        command.name(),
                        previous,
                        modId);
            }
            commands.add(new CommandDeclaration(modId, command));
        }

        @Override
        public <T> ConfigurationExchange<T> configurationExchange(String path, PayloadCodec<T> codec) {
            Objects.requireNonNull(codec, "codec");
            checkOpen("PLEXUS-NETWORK-002", "Configuration exchange " + modId + ":" + path);
            ResourceId id = idFor(modId, path, "PLEXUS-NETWORK-001");
            requireNew(channelIds, id, "PLEXUS-NETWORK-001", "configuration exchange");
            ConfigurationExchangeImpl<T> exchange = new ConfigurationExchangeImpl<>(id, modId, codec);
            configurationExchanges.add(exchange);
            return exchange;
        }

        @Override
        public <T> PayloadChannel<T> channel(String path, PayloadCodec<T> codec) {
            Objects.requireNonNull(codec, "codec");
            checkOpen("PLEXUS-NETWORK-002", "Channel " + modId + ":" + path);
            ResourceId id = idFor(modId, path, "PLEXUS-NETWORK-001");
            requireNew(channelIds, id, "PLEXUS-NETWORK-001", "channel");
            PayloadChannelImpl<T> channel = new PayloadChannelImpl<>(id, modId, codec, transport);
            channels.add(channel);
            return channel;
        }

        @Override
        public void register(String name, int maxTicks, TestArea area, GameTest test) {
            Objects.requireNonNull(test, "test");
            Objects.requireNonNull(area, "area");
            checkOpen("PLEXUS-GAMETEST-002", "Game test " + modId + ":" + name);
            if (maxTicks < 1) {
                throw new IllegalArgumentException("PLEXUS-GAMETEST-001 maxTicks must be >= 1: " + maxTicks);
            }
            ResourceId id = idFor(modId, name, "PLEXUS-GAMETEST-001");
            requireNew(testIds, id, "PLEXUS-GAMETEST-001", "game test");
            gameTests.add(new GameTestDeclaration(modId, id, maxTicks, area, test));
        }

        @Override
        public KeyBinding keyBinding(String path, int defaultKey) {
            checkOpen("PLEXUS-CLIENT-002", "Key binding " + modId + ":" + path);
            ResourceId id = idFor(modId, path, "PLEXUS-CLIENT-002");
            requireNew(clientIds, id, "PLEXUS-CLIENT-002", "client registration");
            KeyBindingImpl binding = new KeyBindingImpl(id);
            keyBindings.add(new KeyBindingDeclaration(modId, id, defaultKey, binding::bind));
            return binding;
        }

        @Override
        public void hudLayer(String path, HudLayer layer) {
            Objects.requireNonNull(layer, "layer");
            checkOpen("PLEXUS-CLIENT-002", "HUD layer " + modId + ":" + path);
            ResourceId id = idFor(modId, path, "PLEXUS-CLIENT-002");
            requireNew(clientIds, id, "PLEXUS-CLIENT-002", "client registration");
            hudLayers.add(new HudLayerDeclaration(modId, id, layer));
        }

        @Override
        public void blockEntityRenderer(ResourceId block, BlockEntityPainter painter) {
            Objects.requireNonNull(block, "block");
            Objects.requireNonNull(painter, "painter");
            checkOpen("PLEXUS-CLIENT-002", "Block entity renderer for " + block);
            boolean ownBlockEntity = blocks.stream()
                    .anyMatch(declaration -> declaration.id().equals(block)
                            && declaration.ownerModId().equals(modId)
                            && declaration.definition().blockEntity().isPresent());
            if (!ownBlockEntity) {
                throw new IllegalArgumentException("PLEXUS-CLIENT-002 " + block
                        + " is not a block with a block entity declared by " + modId + " (declare it first)");
            }
            if (!paintedBlocks.add(block)) {
                throw new IllegalArgumentException(
                        "PLEXUS-CLIENT-002 " + block + " already has a block entity renderer");
            }
            blockEntityRenderers.add(new BlockEntityRendererDeclaration(modId, block, painter));
        }

        @Override
        public DynamicTexture dynamicTexture(String path, int width, int height) {
            checkOpen("PLEXUS-CLIENT-002", "Dynamic texture " + modId + ":" + path);
            if (width < 1 || height < 1 || width > 4096 || height > 4096) {
                throw new IllegalArgumentException(
                        "PLEXUS-CLIENT-002 texture size must be 1-4096: " + width + "x" + height);
            }
            ResourceId id = idFor(modId, "dynamic/" + path, "PLEXUS-CLIENT-002");
            requireNew(clientIds, id, "PLEXUS-CLIENT-002", "client registration");
            DynamicTextureImpl texture = new DynamicTextureImpl(id, width, height);
            dynamicTextures.add(new DynamicTextureDeclaration(modId, id, width, height, texture::bind));
            return texture;
        }

        @Override
        public void worldPainter(String path, WorldPainter painter) {
            Objects.requireNonNull(painter, "painter");
            checkOpen("PLEXUS-CLIENT-002", "World painter " + modId + ":" + path);
            ResourceId id = idFor(modId, path, "PLEXUS-CLIENT-002");
            requireNew(clientIds, ResourceId.of(modId, "world_painter/" + path), "PLEXUS-CLIENT-002", "world painter");
            worldPainters.add(new WorldPainterDeclaration(modId, id, painter));
        }

        @Override
        public ShaderPipeline shaderPipeline(String path, ShaderPipelineDefinition definition) {
            Objects.requireNonNull(definition, "definition");
            checkOpen("PLEXUS-CLIENT-002", "Shader pipeline " + modId + ":" + path);
            ResourceId id = idFor(modId, path, "PLEXUS-CLIENT-002");
            requireNew(
                    clientIds, ResourceId.of(modId, "shader_pipeline/" + path), "PLEXUS-CLIENT-002", "shader pipeline");
            shaderPipelines.add(new ShaderPipelineDeclaration(modId, id, definition));
            return new ShaderPipeline(id);
        }

        /** The client registrar, or {@code PLEXUS-CLIENT-001} on a dedicated server. */
        public ClientRegistrar client() {
            if (side != GameSide.CLIENT) {
                throw new IllegalStateException("PLEXUS-CLIENT-001 mod " + modId
                        + " asked for client registrations on a dedicated server; do this in a client entrypoint");
            }
            return this;
        }
    }

    /** The transport the game layer binds; sending before that fails clearly. */
    static final class TransportHolder implements Supplier<PayloadTransport> {

        private volatile PayloadTransport transport;

        void set(PayloadTransport transport) {
            this.transport = Objects.requireNonNull(transport, "transport");
        }

        @Override
        public PayloadTransport get() {
            PayloadTransport current = transport;
            if (current == null) {
                throw new IllegalStateException("PLEXUS-NETWORK-002 the game's networking is not connected yet");
            }
            return current;
        }
    }
}
