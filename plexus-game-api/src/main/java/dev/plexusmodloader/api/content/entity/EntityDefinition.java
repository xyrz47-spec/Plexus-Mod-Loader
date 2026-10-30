package dev.plexusmodloader.api.content.entity;

import dev.plexusmodloader.api.content.CreativeTab;
import dev.plexusmodloader.api.content.entity.model.EntityModelDefinition;
import dev.plexusmodloader.api.resource.ResourceId;
import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;
import dev.plexusmodloader.api.worldgen.BiomeSelector;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * A mod mob: a pathfinding creature with the game's standard AI goals, drawn with the humanoid
 * (player-shaped) model and a 64×64 skin-layout texture, or with its own box {@link #model()}.
 *
 * @param width hitbox width in blocks
 * @param height hitbox height in blocks
 * @param category spawn group
 * @param maxHealth health in half-hearts
 * @param movementSpeed base speed (zombie 0.23, villager 0.5)
 * @param attackDamage melee damage; used only with {@link #attackPlayers()}
 * @param wander walk around randomly
 * @param lookAtPlayers look at nearby players
 * @param panicWhenHurt run away when hurt
 * @param attackPlayers chase and hit players
 * @param temptedBy follow players holding this item, if set
 * @param texture skin texture, for example {@code my_mod:textures/entity/golem.png}
 * @param spawnEggTab when present, declares {@code <id>_spawn_egg} in this creative tab
 * @param behavior callbacks
 * @param naturalSpawns where the mob spawns by itself; empty for never
 * @param goals extra AI goals, each created per mob
 * @param riding whether and how players ride the mob
 * @param noGravity whether the mob floats
 * @param pushable whether other entities push the mob
 * @param model a box model drawn with {@code texture}, or empty for the player-shaped model
 * @param movement how the mob moves and finds paths
 * @param flyingSpeed speed in the air for {@link Movement#FLY} (parrot 0.4); ignored otherwise
 */
@ApiStability(StabilityLevel.PREVIEW)
public record EntityDefinition(
        float width,
        float height,
        MobCategory category,
        double maxHealth,
        double movementSpeed,
        double attackDamage,
        boolean wander,
        boolean lookAtPlayers,
        boolean panicWhenHurt,
        boolean attackPlayers,
        Optional<ResourceId> temptedBy,
        ResourceId texture,
        Optional<CreativeTab> spawnEggTab,
        EntityBehavior behavior,
        List<NaturalSpawn> naturalSpawns,
        List<GoalEntry> goals,
        Riding riding,
        boolean noGravity,
        boolean pushable,
        Optional<EntityModelDefinition> model,
        Movement movement,
        double flyingSpeed) {

    public EntityDefinition {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("width and height must be > 0");
        }
        if (maxHealth <= 0) {
            throw new IllegalArgumentException("maxHealth must be > 0");
        }
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(temptedBy, "temptedBy");
        Objects.requireNonNull(texture, "texture");
        Objects.requireNonNull(spawnEggTab, "spawnEggTab");
        Objects.requireNonNull(behavior, "behavior");
        naturalSpawns = List.copyOf(naturalSpawns);
        goals = List.copyOf(goals);
        Objects.requireNonNull(riding, "riding");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(movement, "movement");
        if (flyingSpeed <= 0) {
            throw new IllegalArgumentException("flyingSpeed must be > 0");
        }
    }

    /** How a mob moves and finds paths. */
    public enum Movement {
        /** Walks and jumps; swims to the surface in water. */
        WALK,
        /**
         * Flies in any direction and hovers when it stops, like a bee; takes no fall damage. Paths go
         * through the air.
         */
        FLY,
        /**
         * Swims in any direction under water, like a dolphin, and breathes there; paths stay in water.
         * Out of water it only crawls slowly. Use {@link MobCategory#WATER_CREATURE} to spawn it in water.
         */
        SWIM
    }

    /**
     * An extra AI goal.
     *
     * @param priority lower runs first; the built-in goals use 0 (float), 1 (panic), 2 (attack), 3
     *     (tempt), 5 (wander), 6–7 (look around)
     * @param factory creates the goal for each mob
     * @param target whether it is a target goal (chooses whom to attack) rather than a behaviour goal
     */
    public record GoalEntry(int priority, Supplier<AiGoal> factory, boolean target) {
        public GoalEntry {
            Objects.requireNonNull(factory, "factory");
        }
    }

    /** How players ride a mob. */
    public enum Riding {
        /** Not at all. */
        NONE,
        /** Players climb on by using the mob with an empty hand and are carried along. */
        PASSENGER,
        /** Like {@link #PASSENGER}, and the rider steers with the movement keys, like a horse. */
        STEERABLE
    }

    /** A builder for a 0.6×1.95 creature (player-sized) with the given skin texture. */
    public static Builder builder(ResourceId texture) {
        return new Builder(texture);
    }

    /** Builds an {@link EntityDefinition}. */
    public static final class Builder {

        private final ResourceId texture;
        private float width = 0.6f;
        private float height = 1.95f;
        private MobCategory category = MobCategory.CREATURE;
        private double maxHealth = 20;
        private double movementSpeed = 0.25;
        private double attackDamage = 2;
        private boolean wander = true;
        private boolean lookAtPlayers = true;
        private boolean panicWhenHurt;
        private boolean attackPlayers;
        private Optional<ResourceId> temptedBy = Optional.empty();
        private Optional<CreativeTab> spawnEggTab = Optional.empty();
        private EntityBehavior behavior = new EntityBehavior() {};
        private final List<NaturalSpawn> naturalSpawns = new ArrayList<>();
        private final List<GoalEntry> goals = new ArrayList<>();
        private Riding riding = Riding.NONE;
        private boolean noGravity;
        private boolean pushable = true;
        private Optional<EntityModelDefinition> model = Optional.empty();
        private Movement movement = Movement.WALK;
        private double flyingSpeed = 0.4;

        private Builder(ResourceId texture) {
            this.texture = texture;
        }

        /** Hitbox width and height in blocks. */
        public Builder size(float width, float height) {
            this.width = width;
            this.height = height;
            return this;
        }

        /** Spawn group; {@link MobCategory#CREATURE} by default. */
        public Builder category(MobCategory category) {
            this.category = category;
            return this;
        }

        /** Health in half-hearts; 20 by default. */
        public Builder maxHealth(double maxHealth) {
            this.maxHealth = maxHealth;
            return this;
        }

        /** Base speed (zombie 0.23, villager 0.5); 0.25 by default. */
        public Builder movementSpeed(double movementSpeed) {
            this.movementSpeed = movementSpeed;
            return this;
        }

        /** The mob stands still. */
        public Builder noWandering() {
            this.wander = false;
            return this;
        }

        /** The mob runs away for a while after being hurt. */
        public Builder panicWhenHurt() {
            this.panicWhenHurt = true;
            return this;
        }

        /** The mob chases and hits players for {@code damage}. */
        public Builder attackPlayers(double damage) {
            this.attackPlayers = true;
            this.attackDamage = damage;
            return this;
        }

        /** The mob follows players holding {@code item}. */
        public Builder temptedBy(ResourceId item) {
            this.temptedBy = Optional.of(item);
            return this;
        }

        /** Declares a spawn egg item {@code <id>_spawn_egg} listed in {@code tab}. */
        public Builder spawnEgg(CreativeTab tab) {
            this.spawnEggTab = Optional.of(tab);
            return this;
        }

        /** Callbacks for ticks and interaction. */
        public Builder behavior(EntityBehavior behavior) {
            this.behavior = behavior;
            return this;
        }

        /** The mob spawns by itself in {@code biomes}, in groups of {@code minGroup..maxGroup}. */
        public Builder spawnsNaturally(BiomeSelector biomes, int weight, int minGroup, int maxGroup) {
            naturalSpawns.add(new NaturalSpawn(biomes, weight, minGroup, maxGroup));
            return this;
        }

        /** Adds an AI goal created for each mob by {@code factory}. */
        public Builder goal(int priority, Supplier<AiGoal> factory) {
            goals.add(new GoalEntry(priority, factory, false));
            return this;
        }

        /** Adds a target goal (chooses whom to attack) created for each mob by {@code factory}. */
        public Builder targetGoal(int priority, Supplier<AiGoal> factory) {
            goals.add(new GoalEntry(priority, factory, true));
            return this;
        }

        /** Players can ride the mob; with {@code steerable} they also steer it. */
        public Builder rideable(boolean steerable) {
            this.riding = steerable ? Riding.STEERABLE : Riding.PASSENGER;
            return this;
        }

        /** The mob is not pulled down by gravity. */
        public Builder noGravity() {
            this.noGravity = true;
            return this;
        }

        /** Other entities do not push the mob. */
        public Builder notPushable() {
            this.pushable = false;
            return this;
        }

        /** Draws the mob with a box model instead of the player-shaped one. */
        public Builder model(EntityModelDefinition model) {
            this.model = Optional.of(model);
            return this;
        }

        /**
         * The mob flies and hovers ({@link Movement#FLY}) at {@code flyingSpeed} (parrot 0.4) in the air;
         * {@link #movementSpeed} still applies on the ground.
         */
        public Builder flying(double flyingSpeed) {
            this.movement = Movement.FLY;
            this.flyingSpeed = flyingSpeed;
            return this;
        }

        /** The mob swims under water ({@link Movement#SWIM}) at {@link #movementSpeed}. */
        public Builder swimming() {
            this.movement = Movement.SWIM;
            return this;
        }

        /** Creates the definition; invalid values throw {@link IllegalArgumentException}. */
        public EntityDefinition build() {
            return new EntityDefinition(
                    width,
                    height,
                    category,
                    maxHealth,
                    movementSpeed,
                    attackDamage,
                    wander,
                    lookAtPlayers,
                    panicWhenHurt,
                    attackPlayers,
                    temptedBy,
                    texture,
                    spawnEggTab,
                    behavior,
                    naturalSpawns,
                    goals,
                    riding,
                    noGravity,
                    pushable,
                    model,
                    movement,
                    flyingSpeed);
        }
    }
}
