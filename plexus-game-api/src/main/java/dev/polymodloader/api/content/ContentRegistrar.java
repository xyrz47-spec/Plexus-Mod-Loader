package dev.polymodloader.api.content;

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
import dev.polymodloader.api.content.rule.GameRuleCategory;
import dev.polymodloader.api.content.rule.GameRuleKey;
import dev.polymodloader.api.resource.ResourceId;
import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;
import dev.polymodloader.api.tag.TagKey;

/**
 * Declares a mod's blocks and items. IDs are {@code <mod id>:<path>}.
 *
 * <p><b>When:</b> only inside common entrypoints. Poly Mod Loader creates the declared content just before the
 * game freezes its registries; content cannot be added afterwards ({@code PLEXUS-CONTENT-002}).
 * Models, textures, translations, recipes, loot tables, and tags come from the mod jar's
 * {@code assets/} and {@code data/} folders, which Poly Mod Loader adds as a required resource and data pack.
 *
 * <p><b>Both sides:</b> client and server must declare the same content in the same order, because
 * the game synchronises blocks by number.
 */
@ApiStability(StabilityLevel.PREVIEW)
public interface ContentRegistrar {

    /**
     * Declares a block; unless the definition says otherwise, a block item with the same ID is
     * declared too.
     *
     * @throws IllegalArgumentException for an invalid path or a path this mod already used
     *     ({@code PLEXUS-CONTENT-001})
     * @throws IllegalStateException after registration closed ({@code PLEXUS-CONTENT-002})
     */
    BlockHandle block(String path, BlockDefinition definition);

    /**
     * Declares an item.
     *
     * @throws IllegalArgumentException for an invalid path or a path this mod already used
     *     ({@code PLEXUS-CONTENT-001})
     * @throws IllegalStateException after registration closed ({@code PLEXUS-CONTENT-002})
     */
    ItemHandle item(String path, ItemDefinition definition);

    /**
     * Declares a mob type; with {@code spawnEgg} also an item {@code <path>_spawn_egg}. Mobs are
     * spawned by eggs, commands ({@code /summon}), {@code WorldView.spawnEntity}, and naturally where
     * the definition says.
     */
    EntityHandle entity(String path, EntityDefinition definition);

    /** Declares a status effect usable with {@code /effect} and {@code LivingEntityView.addEffect}. */
    EffectHandle effect(String path, EffectDefinition definition);

    /** Declares a boolean game rule. */
    GameRuleKey<Boolean> booleanRule(String path, boolean defaultValue, GameRuleCategory category);

    /** Declares an integer game rule limited to {@code [min, max]}. */
    GameRuleKey<Integer> integerRule(String path, int defaultValue, int min, int max, GameRuleCategory category);

    /** Declares a sound event. */
    SoundHandle sound(String path);

    /** Declares a thrown projectile type. */
    EntityHandle projectile(String path, ProjectileDefinition definition);

    /**
     * Declares a data component that item stacks can carry.
     *
     * @throws IllegalArgumentException for an invalid or used path ({@code PLEXUS-CONTENT-001})
     */
    <T> ItemComponentKey<T> itemComponent(String path, ComponentType<T> type);

    /** Declares a particle type, spawnable with {@code WorldView.spawnParticles} and {@code /particle}. */
    ContentHandle particle(String path, dev.polymodloader.api.content.particle.ParticleDefinition definition);

    /** Declares data that entities carry; see {@link EntityDataKey}. */
    default EntityDataKey entityData(String path, boolean syncToOwner) {
        return entityData(path, syncToOwner ? EntityDataOptions.DEFAULT.syncedToOwner() : EntityDataOptions.DEFAULT);
    }

    /** Declares data that entities carry, with synchronisation and version options. */
    EntityDataKey entityData(String path, EntityDataOptions options);

    /** Declares an advancement criterion trigger {@code <mod id>:<path>}. */
    AdvancementTrigger advancementTrigger(String path);

    /**
     * Adds entries to a tag of any namespace, as a tag file with {@code "replace": false} in the mod
     * jar would; Poly Mod Loader writes them to its generated data pack. Several calls and several mods add
     * up. Entries are checked when the registries freeze: an entry that is not registered then is
     * reported ({@code PLEXUS-CONTENT-007}) and left out, the rest of the tag still loads.
     *
     * @param entries block, item, or entity type IDs matching the tag's registry
     * @throws IllegalArgumentException for no entries
     * @throws IllegalStateException after registration closed ({@code PLEXUS-CONTENT-002})
     */
    void addToTag(TagKey tag, ResourceId... entries);

    /**
     * Declares a creative inventory tab {@code <mod id>:<path>}, titled by the translation of {@code
     * itemGroup.<mod id>.<path>}. Entries and icon are checked when the registries freeze: one that is
     * not a registered item then is reported ({@code PLEXUS-CONTENT-008}) and left out (a missing icon
     * falls back to the first valid entry).
     *
     * @throws IllegalArgumentException for an invalid or used path ({@code PLEXUS-CONTENT-001})
     * @throws IllegalStateException after registration closed ({@code PLEXUS-CONTENT-002})
     */
    ContentHandle creativeTab(String path, CreativeTabDefinition definition);
}
