package dev.polymodloader.game.layer.v26_2.content;

import dev.polymodloader.api.content.CreativeTab;
import dev.polymodloader.api.content.block.BlockDefinition;
import dev.polymodloader.api.content.block.state.BlockStateView;
import dev.polymodloader.api.content.item.ItemDefinition;
import dev.polymodloader.api.resource.ResourceId;
import dev.polymodloader.game.layer.v26_2.Views;
import dev.polymodloader.game.version.layer.NativeDeclarations;
import dev.polymodloader.game.version.layer.NativeDeclarations.BlockDeclaration;
import dev.polymodloader.game.version.layer.NativeDeclarations.ItemDeclaration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.ToIntFunction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Creates declared blocks and items in the game's registries, and lists items in creative tabs. */
public final class ContentInstaller {

    private static final Logger LOGGER = LoggerFactory.getLogger("Poly Mod Loader");
    private static final Map<CreativeTab, List<Item>> TAB_CONTENTS = new EnumMap<>(CreativeTab.class);
    private static final AtomicBoolean TAB_WARNING = new AtomicBoolean();

    private ContentInstaller() {}

    /** Registers everything declared; called just before the game freezes its registries. */
    public static void install(NativeDeclarations declarations) {
        Map<ResourceId, Block> blocks = new HashMap<>();
        for (BlockDeclaration declaration : declarations.blocks()) {
            ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Views.id(declaration.id()));
            BlockDefinition definition = declaration.definition();
            BlockBehaviour.Properties properties = properties(definition).setId(key);
            Block block = definition.blockEntity().isPresent()
                    ? new PolyEntityBlock(properties, definition)
                    : new PolyBlock(properties, definition);
            Registry.register(BuiltInRegistries.BLOCK, key, block);
            // The game numbers block states and fills their caches once, in Blocks' static
            // initialiser; later blocks must do the same or their states cannot be sent to clients.
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                Block.BLOCK_STATE_REGISTRY.add(state);
                state.initCache();
            }
            if (block instanceof PolyEntityBlock entityBlock) {
                registerBlockEntityType(declaration.id(), entityBlock);
                definition
                        .blockEntity()
                        .flatMap(entity -> entity.menu())
                        .ifPresent(layout ->
                                entityBlock.setMenuType(PolyMenus.register(Views.id(declaration.id()), layout)));
            }
            blocks.put(declaration.id(), block);
            declaration.registered().run();
        }
        for (ItemDeclaration declaration : declarations.items()) {
            ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Views.id(declaration.id()));
            Item.Properties properties = properties(declaration.definition()).setId(key);
            Item item;
            if (declaration.blockItem()) {
                Block block = blocks.get(declaration.id());
                BlockItem blockItem = new BlockItem(block, properties.useBlockDescriptionPrefix());
                blockItem.registerBlocks(Item.BY_BLOCK, blockItem);
                item = blockItem;
            } else {
                item = new PolyItem(properties, declaration.definition().behavior());
            }
            Registry.register(BuiltInRegistries.ITEM, key, item);
            declaration
                    .definition()
                    .creativeTab()
                    .ifPresent(tab -> TAB_CONTENTS
                            .computeIfAbsent(tab, ignored -> new ArrayList<>())
                            .add(item));
            declaration.registered().run();
        }
        if (!blocks.isEmpty() || !declarations.items().isEmpty()) {
            LOGGER.info(
                    "[Poly Mod Loader] Registered {} block(s) and {} item(s) from mods",
                    blocks.size(),
                    declarations.items().size());
        }
    }

    private static void registerBlockEntityType(ResourceId id, PolyEntityBlock block) {
        BlockEntityType<BlockEntity> type = new BlockEntityType<>(block::newBlockEntity, Set.of(block));
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Views.id(id), type);
        block.setType(type);
    }

    /** Appends mod items to a creative tab after the game built its contents. */
    public static void appendToCreativeTab(CreativeModeTab tab) {
        Identifier tabId = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
        if (tabId == null || !tabId.getNamespace().equals("minecraft")) {
            return;
        }
        CreativeTab polyTab;
        try {
            polyTab = CreativeTab.valueOf(tabId.getPath().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException notMapped) {
            return;
        }
        List<Item> items = TAB_CONTENTS.getOrDefault(polyTab, List.of());
        if (items.isEmpty()) {
            return;
        }
        try {
            add(tab.getDisplayItems(), items);
            add(tab.getSearchTabDisplayItems(), items);
        } catch (UnsupportedOperationException e) {
            if (TAB_WARNING.compareAndSet(false, true)) {
                LOGGER.warn("[Poly Mod Loader] POLY-CONTENT-003 creative tab contents are read-only in this version; "
                        + "mod items are not listed");
            }
        }
    }

    private static void add(Collection<ItemStack> stacks, List<Item> items) {
        for (Item item : items) {
            stacks.add(new ItemStack(item));
        }
    }

    private static BlockBehaviour.Properties properties(BlockDefinition definition) {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                .strength(definition.hardness(), definition.explosionResistance())
                .sound(sound(definition.sound()))
                .friction(definition.friction());
        if (definition.requiresCorrectTool()) {
            properties.requiresCorrectToolForDrops();
        }
        if (definition.lightByState().isPresent()) {
            ToIntFunction<BlockStateView> light = definition.lightByState().get();
            properties.lightLevel(state -> Math.max(0, Math.min(15, light.applyAsInt(BlockStates.view(state)))));
        } else if (definition.lightLevel() > 0) {
            int light = definition.lightLevel();
            properties.lightLevel(state -> light);
        }
        if (!definition.collision()) {
            properties.noCollision();
        }
        if (!definition.occludes()) {
            properties.noOcclusion();
        }
        return properties;
    }

    private static Item.Properties properties(ItemDefinition definition) {
        Item.Properties properties = new Item.Properties();
        if (definition.durability() > 0) {
            properties.durability(definition.durability());
        } else {
            properties.stacksTo(definition.maxStackSize());
        }
        if (definition.fireResistant()) {
            properties.fireResistant();
        }
        properties.rarity(
                switch (definition.rarity()) {
                    case COMMON -> Rarity.COMMON;
                    case UNCOMMON -> Rarity.UNCOMMON;
                    case RARE -> Rarity.RARE;
                    case EPIC -> Rarity.EPIC;
                });
        definition
                .food()
                .ifPresent(food ->
                        properties.food(new FoodProperties(food.nutrition(), food.saturation(), food.alwaysEdible())));
        return properties;
    }

    private static SoundType sound(dev.polymodloader.api.content.block.BlockSound sound) {
        return switch (sound) {
            case STONE -> SoundType.STONE;
            case WOOD -> SoundType.WOOD;
            case GRAVEL -> SoundType.GRAVEL;
            case GRASS -> SoundType.GRASS;
            case METAL -> SoundType.METAL;
            case GLASS -> SoundType.GLASS;
            case WOOL -> SoundType.WOOL;
            case SAND -> SoundType.SAND;
            case COPPER -> SoundType.COPPER;
            case AMETHYST -> SoundType.AMETHYST;
        };
    }

    /** Lists {@code item} at the end of {@code tab}; used for items created outside {@link #install}. */
    public static void listInCreativeTab(CreativeTab tab, Item item) {
        TAB_CONTENTS.computeIfAbsent(tab, ignored -> new ArrayList<>()).add(item);
    }
}
