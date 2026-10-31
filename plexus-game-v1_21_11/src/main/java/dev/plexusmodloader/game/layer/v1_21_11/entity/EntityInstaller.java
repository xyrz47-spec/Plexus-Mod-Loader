package dev.plexusmodloader.game.layer.v1_21_11.entity;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.serialization.Codec;
import dev.plexusmodloader.api.content.effect.EffectDefinition;
import dev.plexusmodloader.api.content.entity.EntityDefinition;
import dev.plexusmodloader.api.content.entity.NaturalSpawn;
import dev.plexusmodloader.api.content.entity.ProjectileDefinition;
import dev.plexusmodloader.api.content.rule.GameRuleKey;
import dev.plexusmodloader.api.resource.ResourceId;
import dev.plexusmodloader.game.layer.v1_21_11.Views;
import dev.plexusmodloader.game.layer.v1_21_11.content.ContentInstaller;
import dev.plexusmodloader.game.version.layer.NativeDeclarations;
import dev.plexusmodloader.game.version.layer.NativeDeclarations.AdvancementTriggerDeclaration;
import dev.plexusmodloader.game.version.layer.NativeDeclarations.EffectDeclaration;
import dev.plexusmodloader.game.version.layer.NativeDeclarations.EntityDeclaration;
import dev.plexusmodloader.game.version.layer.NativeDeclarations.ProjectileDeclaration;
import dev.plexusmodloader.game.version.layer.NativeDeclarations.SoundDeclaration;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.advancements.criterion.PlayerTrigger;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRuleType;
import net.minecraft.world.level.gamerules.GameRuleTypeVisitor;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Registers mod mobs, spawn eggs, status effects, game rules, and sound events. */
public final class EntityInstaller {

    private static final Logger LOGGER = LoggerFactory.getLogger("Plexus Mod Loader");
    private static final Map<EntityType<?>, EntityDefinition> DEFINITIONS = new ConcurrentHashMap<>();
    /** Built on first use: attribute holders are only bound once the registries froze. */
    private static final Map<EntityType<?>, AttributeSupplier> ATTRIBUTES = new ConcurrentHashMap<>();

    private static final Map<EntityType<?>, ResourceId> TEXTURES = new ConcurrentHashMap<>();
    private static final Map<ResourceId, GameRule<?>> RULES = new ConcurrentHashMap<>();
    private static final Map<EntityType<?>, ProjectileDefinition> PROJECTILES = new ConcurrentHashMap<>();
    private static final List<SpawnEntry> NATURAL_SPAWNS = new CopyOnWriteArrayList<>();

    /** A mob that spawns naturally. */
    public record SpawnEntry(EntityType<?> type, MobCategory category, NaturalSpawn spawn) {}

    private EntityInstaller() {}

    public static void install(NativeDeclarations declarations) {
        for (EntityDeclaration declaration : declarations.entities()) {
            registerEntity(declaration);
        }
        for (ProjectileDeclaration declaration : declarations.projectiles()) {
            registerProjectile(declaration);
        }
        for (AdvancementTriggerDeclaration declaration : declarations.advancementTriggers()) {
            PlayerTrigger trigger = new PlayerTrigger();
            Registry.register(BuiltInRegistries.TRIGGER_TYPES, Views.id(declaration.id()), trigger);
            declaration.bind().accept(player -> {
                if (!(Views.gamePlayer(player) instanceof ServerPlayer serverPlayer)) {
                    throw new IllegalStateException("Advancement triggers fire on the server");
                }
                trigger.trigger(serverPlayer);
            });
        }
        for (EffectDeclaration declaration : declarations.effects()) {
            EffectDefinition definition = declaration.definition();
            MobEffect effect = new PlexusMobEffect(category(definition), definition.color(), definition);
            Registry.register(BuiltInRegistries.MOB_EFFECT, Views.id(declaration.id()), effect);
            declaration.registered().run();
        }
        for (GameRuleKey<?> key : declarations.gameRules()) {
            GameRule<?> rule = gameRule(key);
            Registry.register(BuiltInRegistries.GAME_RULE, Views.id(key.id()), rule);
            RULES.put(key.id(), rule);
        }
        for (SoundDeclaration declaration : declarations.sounds()) {
            Registry.register(
                    BuiltInRegistries.SOUND_EVENT,
                    Views.id(declaration.id()),
                    SoundEvent.createVariableRangeEvent(Views.id(declaration.id())));
            declaration.registered().run();
        }
        int total = declarations.entities().size()
                + declarations.projectiles().size()
                + declarations.effects().size()
                + declarations.gameRules().size()
                + declarations.sounds().size();
        if (total > 0) {
            LOGGER.info(
                    "[Plexus Mod Loader] Registered {} entity type(s), {} effect(s), {} game rule(s), {} sound(s) from mods",
                    declarations.entities().size(),
                    declarations.effects().size(),
                    declarations.gameRules().size(),
                    declarations.sounds().size());
        }
    }

    private static void registerEntity(EntityDeclaration declaration) {
        EntityDefinition definition = declaration.definition();
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Views.id(declaration.id()));
        EntityType<PlexusMob> type = EntityType.Builder.<PlexusMob>of(
                        (entityType, level) -> PlexusMob.create(entityType, level, definition), category(definition))
                .sized(definition.width(), definition.height())
                .clientTrackingRange(8)
                .build(key);
        Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
        DEFINITIONS.put(type, definition);
        if (!definition.naturalSpawns().isEmpty()) {
            registerSpawnPlacement(type, definition.category());
            for (NaturalSpawn spawn : definition.naturalSpawns()) {
                NATURAL_SPAWNS.add(new SpawnEntry(type, category(definition), spawn));
            }
        }
        TEXTURES.put(type, definition.texture());
        declaration.registered().run();
        definition.spawnEggTab().ifPresent(tab -> {
            ResourceId eggId =
                    ResourceId.of(declaration.id().namespace(), declaration.id().path() + "_spawn_egg");
            ResourceKey<Item> eggKey = ResourceKey.create(Registries.ITEM, Views.id(eggId));
            Item egg = new SpawnEggItem(new Item.Properties().setId(eggKey).spawnEgg(type));
            Registry.register(BuiltInRegistries.ITEM, eggKey, egg);
            ContentInstaller.listInCreativeTab(tab, egg);
        });
    }

    private static void registerProjectile(ProjectileDeclaration declaration) {
        ProjectileDefinition definition = declaration.definition();
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Views.id(declaration.id()));
        EntityType<PlexusProjectile> type = EntityType.Builder.<PlexusProjectile>of(
                        PlexusProjectile::new, MobCategory.MISC)
                .noLootTable()
                .sized(definition.size(), definition.size())
                .clientTrackingRange(4)
                .updateInterval(10)
                .build(key);
        Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
        PROJECTILES.put(type, definition);
        declaration.registered().run();
    }

    /** The definition of a mod projectile type. */
    static ProjectileDefinition projectile(EntityType<?> type) {
        ProjectileDefinition definition = PROJECTILES.get(type);
        if (definition == null) {
            throw new IllegalStateException("Not a Plexus Mod Loader projectile type: " + type);
        }
        return definition;
    }

    /** Mod projectile types, for the client renderers. */
    public static Set<EntityType<?>> projectileTypes() {
        return Set.copyOf(PROJECTILES.keySet());
    }

    /** Mobs that spawn naturally, for the biome spawn lists. */
    public static List<SpawnEntry> naturalSpawns() {
        return List.copyOf(NATURAL_SPAWNS);
    }

    /**
     * Tells the game where a naturally spawning mod mob may appear, like the game's mobs of the same
     * category. The game keeps the registration method private.
     */
    private static void registerSpawnPlacement(
            EntityType<PlexusMob> type, dev.plexusmodloader.api.content.entity.MobCategory category) {
        SpawnPlacementType placement = category == dev.plexusmodloader.api.content.entity.MobCategory.WATER_CREATURE
                ? SpawnPlacementTypes.IN_WATER
                : SpawnPlacementTypes.ON_GROUND;
        SpawnPlacements.SpawnPredicate<PlexusMob> predicate = switch (category) {
            case CREATURE ->
                (entityType, level, reason, pos, random) -> level.getBlockState(pos.below())
                                .is(BlockTags.ANIMALS_SPAWNABLE_ON)
                        && (EntitySpawnReason.ignoresLightRequirements(reason) || level.getRawBrightness(pos, 0) > 8);
            case MONSTER -> Monster::checkMonsterSpawnRules;
            case AMBIENT -> Mob::checkMobSpawnRules;
            case WATER_CREATURE ->
                (entityType, level, reason, pos, random) ->
                        level.getFluidState(pos.below()).is(FluidTags.WATER)
                                && level.getBlockState(pos.above()).is(Blocks.WATER);
        };
        try {
            Method register = SpawnPlacements.class.getDeclaredMethod(
                    "register",
                    EntityType.class,
                    SpawnPlacementType.class,
                    Heightmap.Types.class,
                    SpawnPlacements.SpawnPredicate.class);
            register.setAccessible(true);
            register.invoke(null, type, placement, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, predicate);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("PLEXUS-CONTENT-005 cannot register where " + type + " spawns", e);
        }
    }

    /** Head hook of {@code DefaultAttributes.getSupplier}: mod mobs' attributes, else null. */
    public static AttributeSupplier attributes(EntityType<?> type) {
        EntityDefinition definition = DEFINITIONS.get(type);
        if (definition == null) {
            return null;
        }
        return ATTRIBUTES.computeIfAbsent(
                type,
                ignored -> Mob.createMobAttributes()
                        .add(Attributes.MAX_HEALTH, definition.maxHealth())
                        .add(Attributes.MOVEMENT_SPEED, definition.movementSpeed())
                        .add(Attributes.ATTACK_DAMAGE, definition.attackDamage())
                        .add(Attributes.FOLLOW_RANGE, 16.0)
                        .add(Attributes.TEMPT_RANGE)
                        .add(Attributes.FLYING_SPEED, definition.flyingSpeed())
                        .build());
    }

    /** Head hook of {@code DefaultAttributes.hasSupplier}: TRUE for mod mobs, else null. */
    public static Boolean hasAttributes(EntityType<?> type) {
        return DEFINITIONS.containsKey(type) ? Boolean.TRUE : null;
    }

    /** Definitions of mod mobs, for the client renderers. */
    public static Map<EntityType<?>, EntityDefinition> definitions() {
        return Map.copyOf(DEFINITIONS);
    }

    /** Skin textures of mod mobs, for the client renderers. */
    public static Map<EntityType<?>, ResourceId> textures() {
        return Map.copyOf(TEXTURES);
    }

    @SuppressWarnings("unchecked")
    public static <T> GameRule<T> rule(GameRuleKey<T> key) {
        GameRule<?> rule = RULES.get(key.id());
        if (rule == null) {
            throw new IllegalArgumentException("Game rule " + key.id() + " is not registered");
        }
        return (GameRule<T>) rule;
    }

    /** The current value of a game rule in {@code level}. */
    public static <T> T value(ServerLevel level, GameRuleKey<T> key) {
        return level.getGameRules().get(rule(key));
    }

    /** Sets a game rule in {@code level}. */
    public static <T> void set(ServerLevel level, GameRuleKey<T> key, T value) {
        level.getGameRules().set(rule(key), value, level.getServer());
    }

    /** True when {@code entity} is one of the mod mobs. */
    public static boolean isModMob(LivingEntity entity) {
        return entity instanceof PlexusMob;
    }

    @SuppressWarnings("unchecked")
    private static GameRule<?> gameRule(GameRuleKey<?> key) {
        GameRuleCategory category = switch (key.category()) {
            case PLAYER -> GameRuleCategory.PLAYER;
            case MOBS -> GameRuleCategory.MOBS;
            case SPAWNING -> GameRuleCategory.SPAWNING;
            case DROPS -> GameRuleCategory.DROPS;
            case UPDATES -> GameRuleCategory.UPDATES;
            case CHAT -> GameRuleCategory.CHAT;
            case MISC -> GameRuleCategory.MISC;
        };
        if (key.type() == Boolean.class) {
            return new GameRule<>(
                    category,
                    GameRuleType.BOOL,
                    BoolArgumentType.bool(),
                    GameRuleTypeVisitor::visitBoolean,
                    Codec.BOOL,
                    value -> value ? 1 : 0,
                    (Boolean) key.defaultValue(),
                    FeatureFlagSet.of());
        }
        return new GameRule<>(
                category,
                GameRuleType.INT,
                IntegerArgumentType.integer(key.min(), key.max()),
                GameRuleTypeVisitor::visitInteger,
                Codec.intRange(key.min(), key.max()),
                value -> value,
                (Integer) key.defaultValue(),
                FeatureFlagSet.of());
    }

    private static MobCategory category(EntityDefinition definition) {
        return category(definition.category());
    }

    /** The game's mob category for the API's. */
    public static MobCategory category(dev.plexusmodloader.api.content.entity.MobCategory category) {
        return switch (category) {
            case CREATURE -> MobCategory.CREATURE;
            case MONSTER -> MobCategory.MONSTER;
            case AMBIENT -> MobCategory.AMBIENT;
            case WATER_CREATURE -> MobCategory.WATER_CREATURE;
        };
    }

    private static MobEffectCategory category(EffectDefinition definition) {
        return switch (definition.category()) {
            case BENEFICIAL -> MobEffectCategory.BENEFICIAL;
            case HARMFUL -> MobEffectCategory.HARMFUL;
            case NEUTRAL -> MobEffectCategory.NEUTRAL;
        };
    }
}
