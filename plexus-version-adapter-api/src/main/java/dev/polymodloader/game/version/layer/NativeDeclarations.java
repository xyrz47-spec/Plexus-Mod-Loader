package dev.polymodloader.game.version.layer;

import dev.polymodloader.api.client.HudLayer;
import dev.polymodloader.api.command.LiteralCommand;
import dev.polymodloader.api.content.block.BlockDefinition;
import dev.polymodloader.api.content.item.ItemDefinition;
import dev.polymodloader.api.devtools.gametest.GameTest;
import dev.polymodloader.api.resource.ResourceId;
import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;
import java.util.List;

/**
 * Everything mods declared through the Native API, in mod load order. The runtime collects it while
 * entrypoints run; the game layer reads it.
 */
@ApiStability(StabilityLevel.INTERNAL)
public interface NativeDeclarations {

    /** Declared blocks. */
    List<BlockDeclaration> blocks();

    /** Declared items, including block items. */
    List<ItemDeclaration> items();

    /** Declared mob types. */
    List<EntityDeclaration> entities();

    /** Declared status effects. */
    List<EffectDeclaration> effects();

    /** Tag entries added from code, in declaration order. */
    List<TagEntriesDeclaration> tagEntries();

    /** Declared mod creative tabs, in declaration order (which sets their page and position). */
    List<CreativeTabDeclaration> creativeTabs();

    /** Declared game rules. */
    List<dev.polymodloader.api.content.rule.GameRuleKey<?>> gameRules();

    /** Declared sound events. */
    List<SoundDeclaration> sounds();

    /** Declared projectile types. */
    List<ProjectileDeclaration> projectiles();

    /** Declared item data components. */
    List<dev.polymodloader.api.content.item.ItemComponentKey<?>> itemComponents();

    /** Declared advancement criterion triggers. */
    List<AdvancementTriggerDeclaration> advancementTriggers();

    /** Terrain generators written in code. */
    List<ChunkGeneratorDeclaration> chunkGenerators();

    /** Biomes defined in code. */
    List<BiomeDeclaration> biomes();

    /** Features written in code. */
    List<CodeFeatureDeclaration> codeFeatures();

    /** Density functions written in code. */
    List<DensityFunctionDeclaration> densityFunctions();

    /** Structures built in code. */
    List<StructureDeclaration> structures();

    /** Declared particle types. */
    List<ParticleDeclaration> particles();

    /** Declared world painters (client only). */
    List<WorldPainterDeclaration> worldPainters();

    /** Declared shader pipelines (client only). */
    List<ShaderPipelineDeclaration> shaderPipelines();

    /** Declared block entity painters (client only). */
    List<BlockEntityRendererDeclaration> blockEntityRenderers();

    /** Declared dynamic textures (client only). */
    List<DynamicTextureDeclaration> dynamicTextures();

    /** Reload listeners for mod data and asset files. */
    List<ReloadListenerDeclaration> reloadListeners();

    /** Declared entity data keys. */
    List<dev.polymodloader.api.content.EntityDataKey> entityData();

    /** Permission providers, in registration order. */
    List<dev.polymodloader.api.permission.PermissionProvider> permissionProviders();

    /** Features to attach to biomes. */
    List<FeatureDeclaration> features();

    /** Registered top-level commands. */
    List<CommandDeclaration> commands();

    /** Declared network channels. */
    List<ChannelEndpoint> channels();

    /** Declared configuration exchanges, in declaration order. */
    List<ConfigurationEndpoint> configurationExchanges();

    /** Registered game tests. */
    List<GameTestDeclaration> gameTests();

    /** Declared key bindings (client only). */
    List<KeyBindingDeclaration> keyBindings();

    /** Declared HUD layers (client only). */
    List<HudLayerDeclaration> hudLayers();

    /** Connects channels to the game's networking; until then sending throws. */
    void bindTransport(PayloadTransport transport);

    /**
     * Ends registration: later declarations throw {@code PLEXUS-CONTENT-002} and its relatives. Called
     * by the game layer when the game's registries freeze.
     */
    void close();

    /**
     * A declared block.
     *
     * @param ownerModId declaring mod
     * @param id block ID
     * @param definition properties
     * @param registered call after the game registered the block
     */
    record BlockDeclaration(String ownerModId, ResourceId id, BlockDefinition definition, Runnable registered) {}

    /**
     * A declared item.
     *
     * @param ownerModId declaring mod
     * @param id item ID
     * @param definition properties
     * @param blockItem whether this is the item of the block with the same ID
     * @param registered call after the game registered the item
     */
    record ItemDeclaration(
            String ownerModId, ResourceId id, ItemDefinition definition, boolean blockItem, Runnable registered) {}

    /**
     * A declared mob type.
     *
     * @param ownerModId declaring mod
     * @param id entity type ID
     * @param definition properties
     * @param registered call after the game registered the type
     */
    record EntityDeclaration(
            String ownerModId,
            ResourceId id,
            dev.polymodloader.api.content.entity.EntityDefinition definition,
            Runnable registered) {}

    /**
     * A declared status effect.
     *
     * @param ownerModId declaring mod
     * @param id effect ID
     * @param definition properties
     * @param registered call after the game registered the effect
     */
    record EffectDeclaration(
            String ownerModId,
            ResourceId id,
            dev.polymodloader.api.content.effect.EffectDefinition definition,
            Runnable registered) {}

    /**
     * A declared projectile type.
     *
     * @param ownerModId declaring mod
     * @param id entity type ID
     * @param definition properties
     * @param registered call after the game registered the type
     */
    record ProjectileDeclaration(
            String ownerModId,
            ResourceId id,
            dev.polymodloader.api.content.entity.ProjectileDefinition definition,
            Runnable registered) {}

    /**
     * A declared advancement criterion trigger.
     *
     * @param ownerModId declaring mod
     * @param id trigger ID
     * @param bind call with the code that fires the game's criterion, once it is registered
     */
    record AdvancementTriggerDeclaration(
            String ownerModId,
            ResourceId id,
            java.util.function.Consumer<java.util.function.Consumer<dev.polymodloader.api.entity.PlayerView>> bind) {}

    /**
     * A terrain generator written in code.
     *
     * @param ownerModId declaring mod
     * @param id generator type ID
     * @param generator the terrain
     */
    record ChunkGeneratorDeclaration(
            String ownerModId, ResourceId id, dev.polymodloader.api.worldgen.TerrainGenerator generator) {}

    /**
     * A biome defined in code.
     *
     * @param ownerModId declaring mod
     * @param id biome ID
     * @param definition the biome
     */
    record BiomeDeclaration(
            String ownerModId, ResourceId id, dev.polymodloader.api.worldgen.BiomeDefinition definition) {}

    /**
     * A feature written in code.
     *
     * @param ownerModId declaring mod
     * @param id feature type, configured feature and placed feature ID
     * @param placement where it is tried
     * @param feature the code
     */
    record CodeFeatureDeclaration(
            String ownerModId,
            ResourceId id,
            dev.polymodloader.api.worldgen.FeaturePlacement placement,
            dev.polymodloader.api.worldgen.CodeFeature feature) {}

    /**
     * A density function written in code.
     *
     * @param ownerModId declaring mod
     * @param id density function type ID
     * @param minValue lowest value
     * @param maxValue highest value
     * @param function the code
     */
    record DensityFunctionDeclaration(
            String ownerModId,
            ResourceId id,
            double minValue,
            double maxValue,
            dev.polymodloader.api.worldgen.NoiseFunction function) {}

    /**
     * A structure built in code.
     *
     * @param ownerModId declaring mod
     * @param id structure, structure type and structure set ID
     * @param definition the structure
     */
    record StructureDeclaration(
            String ownerModId, ResourceId id, dev.polymodloader.api.worldgen.StructureDefinition definition) {}

    /**
     * A declared particle type.
     *
     * @param ownerModId declaring mod
     * @param id particle type ID
     * @param definition how clients draw it
     * @param registered call after the game registered it
     */
    record ParticleDeclaration(
            String ownerModId,
            ResourceId id,
            dev.polymodloader.api.content.particle.ParticleDefinition definition,
            Runnable registered) {}

    /**
     * A painter drawing in the world every frame.
     *
     * @param ownerModId declaring mod
     * @param id painter ID
     * @param painter what to draw
     */
    record WorldPainterDeclaration(
            String ownerModId, ResourceId id, dev.polymodloader.api.client.WorldPainter painter) {}

    /**
     * A render pipeline with a mod's core shader.
     *
     * @param ownerModId declaring mod
     * @param id pipeline ID
     * @param definition shader and render state
     */
    record ShaderPipelineDeclaration(
            String ownerModId, ResourceId id, dev.polymodloader.api.client.ShaderPipelineDefinition definition) {}

    /**
     * A painter for a mod block entity.
     *
     * @param ownerModId declaring mod
     * @param block the block
     * @param painter what to draw
     */
    record BlockEntityRendererDeclaration(
            String ownerModId, ResourceId block, dev.polymodloader.api.client.BlockEntityPainter painter) {}

    /**
     * A texture whose pixels a mod sets.
     *
     * @param ownerModId declaring mod
     * @param id texture ID
     * @param width width in pixels
     * @param height height in pixels
     * @param bind call with the code that uploads pixels (row by row, {@code 0xAARRGGBB}), once the
     *     game's texture manager exists
     */
    record DynamicTextureDeclaration(
            String ownerModId,
            ResourceId id,
            int width,
            int height,
            java.util.function.Consumer<java.util.function.Consumer<int[]>> bind) {}

    /**
     * A reload listener for a mod's own files.
     *
     * @param ownerModId declaring mod
     * @param assets {@code true} for client resource packs, {@code false} for data packs
     * @param directory directory under each namespace
     * @param suffix file name ending, for example {@code .json}
     * @param listener receives the files as bytes
     */
    record ReloadListenerDeclaration(
            String ownerModId,
            boolean assets,
            String directory,
            String suffix,
            dev.polymodloader.api.resource.BinaryReloadListener listener) {}

    /**
     * A declared sound event.
     *
     * @param ownerModId declaring mod
     * @param id sound event ID
     * @param registered call after the game registered it
     */
    record SoundDeclaration(String ownerModId, ResourceId id, Runnable registered) {}

    /**
     * A placed feature to attach to biomes.
     *
     * @param ownerModId declaring mod
     * @param biomes which biomes
     * @param step generation step
     * @param placedFeature placed feature ID
     */
    record FeatureDeclaration(
            String ownerModId,
            dev.polymodloader.api.worldgen.BiomeSelector biomes,
            dev.polymodloader.api.worldgen.GenerationStepName step,
            ResourceId placedFeature) {}

    /**
     * A registered command tree.
     *
     * @param ownerModId registering mod
     * @param command top-level node
     */
    record CommandDeclaration(String ownerModId, LiteralCommand command) {}

    /**
     * A registered game test.
     *
     * @param ownerModId registering mod
     * @param id test ID, {@code <mod id>:<name>}
     * @param maxTicks timeout
     * @param area the space the test gets
     * @param test the test
     */
    record GameTestDeclaration(
            String ownerModId,
            ResourceId id,
            int maxTicks,
            dev.polymodloader.api.devtools.gametest.TestArea area,
            GameTest test) {}

    /**
     * A declared key binding.
     *
     * @param ownerModId declaring mod
     * @param id binding ID
     * @param defaultKey default Poly Mod Loader key code (a GLFW key token, ADR-0029), or -1 for unbound; the game
     *     layer converts it to the running version's key code
     * @param bind call with the game's key state once the binding exists
     */
    record KeyBindingDeclaration(
            String ownerModId, ResourceId id, int defaultKey, java.util.function.Consumer<KeyState> bind) {}

    /**
     * A declared HUD layer.
     *
     * @param ownerModId declaring mod
     * @param id layer ID
     * @param layer the layer
     */
    record HudLayerDeclaration(String ownerModId, ResourceId id, HudLayer layer) {}

    /**
     * A declared creative tab.
     *
     * @param ownerModId declaring mod
     * @param id tab ID
     * @param definition icon and entries, not yet checked against the item registry
     * @param registered call after the game registered the tab
     */
    record CreativeTabDeclaration(
            String ownerModId,
            ResourceId id,
            dev.polymodloader.api.content.CreativeTabDefinition definition,
            Runnable registered) {}

    /**
     * Entries a mod adds to a tag from code.
     *
     * @param ownerModId declaring mod
     * @param tag the tag
     * @param entries IDs in the tag's registry, not yet checked against it
     */
    record TagEntriesDeclaration(String ownerModId, dev.polymodloader.api.tag.TagKey tag, List<ResourceId> entries) {

        public TagEntriesDeclaration {
            entries = List.copyOf(entries);
        }
    }

    /** The game's state of one key binding. */
    interface KeyState {

        /** Whether the key is held. */
        boolean isDown();

        /** Consumes one queued press. */
        boolean consumeClick();
    }
}
