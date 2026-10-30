package dev.plexusmodloader.game.layer.v1_21_11;

import dev.plexusmodloader.api.resource.ResourceId;
import dev.plexusmodloader.api.tag.TagKey;
import dev.plexusmodloader.api.tag.TagRegistry;
import dev.plexusmodloader.api.tag.TagView;
import dev.plexusmodloader.game.version.layer.NativeDeclarations.TagEntriesDeclaration;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maps Native API tag keys onto the game's tags of the built-in block, item, and entity type
 * registries, whose holders carry the tags the server loaded (or the client received).
 */
public final class GameTags {

    private static final Logger LOGGER = LoggerFactory.getLogger("Poly Mod Loader");

    /** The tags view handed out by world views; it reads the registries' current tags. */
    public static final TagView VIEW = new TagView() {
        @Override
        public boolean contains(TagKey tag, ResourceId entry) {
            return GameTags.contains(tag, entry);
        }

        @Override
        public List<ResourceId> entries(TagKey tag) {
            return GameTags.entries(tag);
        }
    };

    private GameTags() {}

    public static net.minecraft.tags.TagKey<Block> block(TagKey tag) {
        require(tag, TagRegistry.BLOCK);
        return net.minecraft.tags.TagKey.create(Registries.BLOCK, Views.id(tag.id()));
    }

    public static net.minecraft.tags.TagKey<Item> item(TagKey tag) {
        require(tag, TagRegistry.ITEM);
        return net.minecraft.tags.TagKey.create(Registries.ITEM, Views.id(tag.id()));
    }

    public static net.minecraft.tags.TagKey<EntityType<?>> entityType(TagKey tag) {
        require(tag, TagRegistry.ENTITY_TYPE);
        return net.minecraft.tags.TagKey.create(Registries.ENTITY_TYPE, Views.id(tag.id()));
    }

    /** Whether {@code block} is in the block tag; {@link IllegalArgumentException} for another kind of tag. */
    public static boolean isIn(Block block, TagKey tag) {
        return BuiltInRegistries.BLOCK.wrapAsHolder(block).is(block(tag));
    }

    /** Whether {@code item} is in the item tag; {@link IllegalArgumentException} for another kind of tag. */
    public static boolean isIn(Item item, TagKey tag) {
        return BuiltInRegistries.ITEM.wrapAsHolder(item).is(item(tag));
    }

    /** Whether {@code type} is in the entity type tag; {@link IllegalArgumentException} for another kind. */
    public static boolean isIn(EntityType<?> type, TagKey tag) {
        return BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(type).is(entityType(tag));
    }

    private static void require(TagKey tag, TagRegistry registry) {
        if (tag.registry() != registry) {
            throw new IllegalArgumentException(tag + " is a " + tag.registry() + " tag, not a " + registry + " tag");
        }
    }

    static boolean contains(TagKey tag, ResourceId entry) {
        return switch (tag.registry()) {
            case BLOCK -> contains(BuiltInRegistries.BLOCK, block(tag), entry);
            case ITEM -> contains(BuiltInRegistries.ITEM, item(tag), entry);
            case ENTITY_TYPE -> contains(BuiltInRegistries.ENTITY_TYPE, entityType(tag), entry);
        };
    }

    static List<ResourceId> entries(TagKey tag) {
        return switch (tag.registry()) {
            case BLOCK -> entries(BuiltInRegistries.BLOCK, block(tag));
            case ITEM -> entries(BuiltInRegistries.ITEM, item(tag));
            case ENTITY_TYPE -> entries(BuiltInRegistries.ENTITY_TYPE, entityType(tag));
        };
    }

    private static <T> boolean contains(Registry<T> registry, net.minecraft.tags.TagKey<T> tag, ResourceId entry) {
        return registry.get(Views.id(entry)).map(holder -> holder.is(tag)).orElse(false);
    }

    private static <T> List<ResourceId> entries(Registry<T> registry, net.minecraft.tags.TagKey<T> tag) {
        List<ResourceId> ids = new ArrayList<>();
        for (Holder<T> holder : registry.getTagOrEmpty(tag)) {
            holder.unwrapKey().ifPresent(key -> ids.add(Views.id(key.identifier())));
        }
        return List.copyOf(ids);
    }

    /**
     * Reports tag entries added from code that are not registered now that every mod's content is;
     * the game leaves them out (they are written as optional entries).
     */
    static void checkDeclaredEntries(List<TagEntriesDeclaration> declarations) {
        for (TagEntriesDeclaration declaration : declarations) {
            Registry<?> registry = switch (declaration.tag().registry()) {
                case BLOCK -> BuiltInRegistries.BLOCK;
                case ITEM -> BuiltInRegistries.ITEM;
                case ENTITY_TYPE -> BuiltInRegistries.ENTITY_TYPE;
            };
            for (ResourceId entry : declaration.entries()) {
                if (!registry.containsKey(Views.id(entry))) {
                    LOGGER.error(
                            "[Poly Mod Loader] PLEXUS-CONTENT-007 mod={} added {} to {}, but no {} with that ID is registered;"
                                    + " the entry is left out of the tag",
                            declaration.ownerModId(),
                            entry,
                            declaration.tag(),
                            declaration.tag().registry().directory().replace('_', ' '));
                }
            }
        }
    }
}
