package dev.plexusmodloader.game.layer.v26_1.entity;

import dev.plexusmodloader.api.content.entity.EntityDefinition;
import dev.plexusmodloader.api.content.entity.EntityInteraction;
import dev.plexusmodloader.api.content.entity.model.EntityModelDefinition;
import dev.plexusmodloader.api.content.entity.model.KeyframeAnimation;
import dev.plexusmodloader.api.entity.LivingEntityView;
import dev.plexusmodloader.api.entity.PlayerView;
import dev.plexusmodloader.game.layer.v26_1.Views;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.control.SmoothSwimmingLookControl;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomSwimmingGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** A mod mob: the game's pathfinding mob with goals chosen by the {@link EntityDefinition}. */
public final class PlexusMob extends PathfinderMob {

    /** {@code Mob}'s constructor calls {@link #registerGoals()} before fields are set. */
    private static final ThreadLocal<EntityDefinition> CONSTRUCTING = new ThreadLocal<>();

    /** Entity events {@code PLAY_EVENT + i} and {@code STOP_EVENT + i} start and stop on-demand animation i. */
    private static final int PLAY_EVENT = Byte.MIN_VALUE;

    private static final int STOP_EVENT = PLAY_EVENT + EntityModelDefinition.MAX_ON_DEMAND_ANIMATIONS;

    private final EntityDefinition definition;

    /** Names of the model's on-demand animations; index = position in {@link #animationStates}. */
    private final List<String> onDemandAnimations;

    /** Client-side play state of each on-demand animation. */
    private final AnimationState[] animationStates;

    private PlexusMob(EntityType<? extends PathfinderMob> type, Level level, EntityDefinition definition) {
        super(type, level);
        this.definition = definition;
        this.onDemandAnimations = definition
                .model()
                .map(EntityModelDefinition::onDemandAnimations)
                .orElse(List.of());
        this.animationStates = new AnimationState[onDemandAnimations.size()];
        for (int i = 0; i < animationStates.length; i++) {
            animationStates[i] = new AnimationState();
        }
        switch (definition.movement()) {
            case FLY -> this.moveControl = new FlyingMoveControl(this, 20, true);
            case SWIM -> {
                this.moveControl = new SmoothSwimmingMoveControl(this, 85, 10, 0.1f, 0.5f, false);
                this.lookControl = new SmoothSwimmingLookControl(this, 10);
            }
            case WALK -> {}
        }
    }

    static PlexusMob create(EntityType<PlexusMob> type, Level level, EntityDefinition definition) {
        CONSTRUCTING.set(definition);
        try {
            PlexusMob mob = new PlexusMob(type, level, definition);
            mob.setNoGravity(definition.noGravity());
            return mob;
        } finally {
            CONSTRUCTING.remove();
        }
    }

    @Override
    protected void registerGoals() {
        EntityDefinition constructing = CONSTRUCTING.get();
        if (constructing == null) {
            return;
        }
        if (constructing.movement() != EntityDefinition.Movement.SWIM) {
            goalSelector.addGoal(0, new FloatGoal(this));
        }
        if (constructing.panicWhenHurt()) {
            goalSelector.addGoal(1, new PanicGoal(this, 1.25));
        }
        if (constructing.attackPlayers()) {
            goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false));
            targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
        }
        constructing.temptedBy().ifPresent(item -> {
            Item temptation = Views.item(item);
            goalSelector.addGoal(3, new TemptGoal(this, 1.1, stack -> stack.is(temptation), false));
        });
        if (constructing.wander()) {
            goalSelector.addGoal(
                    5,
                    switch (constructing.movement()) {
                        case WALK -> new WaterAvoidingRandomStrollGoal(this, 1.0);
                        case FLY -> new WaterAvoidingRandomFlyingGoal(this, 1.0);
                        case SWIM -> new RandomSwimmingGoal(this, 1.0, 10);
                    });
        }
        if (constructing.lookAtPlayers()) {
            goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0f));
            goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        }
        for (EntityDefinition.GoalEntry entry : constructing.goals()) {
            PlexusGoal goal = new PlexusGoal(this, entry.factory().get());
            (entry.target() ? targetSelector : goalSelector).addGoal(entry.priority(), goal);
        }
    }

    /** {@code Mob}'s constructor creates the navigation before fields are set. */
    @Override
    protected PathNavigation createNavigation(Level level) {
        EntityDefinition constructing = CONSTRUCTING.get();
        EntityDefinition.Movement movement =
                constructing == null ? EntityDefinition.Movement.WALK : constructing.movement();
        return switch (movement) {
            case WALK -> super.createNavigation(level);
            case FLY -> {
                FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
                navigation.setCanOpenDoors(false);
                navigation.setCanFloat(true);
                yield navigation;
            }
            case SWIM -> new WaterBoundPathNavigation(this, level);
        };
    }

    @Override
    protected void checkFallDamage(double ya, boolean onGround, BlockState onState, BlockPos pos) {
        if (definition.movement() != EntityDefinition.Movement.FLY) {
            super.checkFallDamage(ya, onGround, onState, pos);
        }
    }

    @Override
    protected void travelInWater(Vec3 input, double baseGravity, boolean isFalling, double oldY) {
        if (definition.movement() != EntityDefinition.Movement.SWIM) {
            super.travelInWater(input, baseGravity, isFalling, oldY);
            return;
        }
        moveRelative(getSpeed(), input);
        move(MoverType.SELF, getDeltaMovement());
        setDeltaMovement(getDeltaMovement().scale(0.9));
    }

    @Override
    public boolean canBreatheUnderwater() {
        return definition.movement() == EntityDefinition.Movement.SWIM || super.canBreatheUnderwater();
    }

    @Override
    public boolean isPushedByFluid() {
        return definition.movement() != EntityDefinition.Movement.SWIM && super.isPushedByFluid();
    }

    /** Starts on-demand animation {@code name} on every client that sees this mob. */
    public void playAnimation(String name) {
        level().broadcastEntityEvent(this, (byte) (PLAY_EVENT + animationIndex(name)));
    }

    /** Stops on-demand animation {@code name} on every client that sees this mob. */
    public void stopAnimation(String name) {
        level().broadcastEntityEvent(this, (byte) (STOP_EVENT + animationIndex(name)));
    }

    private int animationIndex(String name) {
        int index = onDemandAnimations.indexOf(name);
        if (index < 0) {
            throw new IllegalArgumentException(
                    this + " has no ON_DEMAND model animation '" + name + "'; it has " + onDemandAnimations);
        }
        return index;
    }

    @Override
    public void handleEntityEvent(byte id) {
        int index = id - PLAY_EVENT;
        if (index >= 0 && index < animationStates.length) {
            animationStates[index].start(tickCount);
            return;
        }
        index = id - STOP_EVENT;
        if (index >= 0 && index < animationStates.length) {
            animationStates[index].stop();
            return;
        }
        super.handleEntityEvent(id);
    }

    /** Client-side play states of the on-demand animations, in {@link EntityModelDefinition#onDemandAnimations} order. */
    public AnimationState[] animationStates() {
        return animationStates;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && isAlive()) {
            definition.behavior().onServerTick((LivingEntityView) Views.entity(this));
        }
        if (level().isClientSide() && animationStates.length > 0) {
            stopFinishedAnimations();
        }
    }

    /** A non-looping on-demand animation ends at its last keyframe. */
    private void stopFinishedAnimations() {
        Map<String, KeyframeAnimation> animations =
                definition.model().orElseThrow().animations();
        for (int i = 0; i < animationStates.length; i++) {
            KeyframeAnimation animation = animations.get(onDemandAnimations.get(i));
            AnimationState state = animationStates[i];
            if (!animation.looping()
                    && state.isStarted()
                    && state.getTimeInMillis(tickCount) > animation.lengthSeconds() * 1000) {
                state.stop();
            }
        }
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        PlayerView playerView = Views.player(player);
        dev.plexusmodloader.api.world.InteractionResult result = definition
                .behavior()
                .onInteract(
                        new EntityInteraction(Views.world(level()), (LivingEntityView) Views.entity(this), playerView));
        return switch (result) {
            case SUCCESS -> InteractionResult.SUCCESS;
            case CONSUME -> InteractionResult.CONSUME;
            case FAIL -> InteractionResult.FAIL;
            case PASS -> mount(player, hand);
        };
    }

    /** Using a rideable mob with an empty hand climbs on. */
    private InteractionResult mount(Player player, InteractionHand hand) {
        if (definition.riding() == EntityDefinition.Riding.NONE
                || player.isSecondaryUseActive()
                || isVehicle()
                || !player.getItemInHand(hand).isEmpty()) {
            return super.mobInteract(player, hand);
        }
        if (!level().isClientSide()) {
            player.startRiding(this);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return definition != null
                && definition.riding() != EntityDefinition.Riding.NONE
                && getPassengers().isEmpty();
    }

    @Override
    public LivingEntity getControllingPassenger() {
        if (definition != null
                && definition.riding() == EntityDefinition.Riding.STEERABLE
                && getFirstPassenger() instanceof Player rider) {
            return rider;
        }
        return super.getControllingPassenger();
    }

    @Override
    protected void tickRidden(Player controller, Vec3 riddenInput) {
        super.tickRidden(controller, riddenInput);
        setRot(controller.getYRot(), controller.getXRot() * 0.5f);
        yRotO = yBodyRot = yHeadRot = getYRot();
    }

    @Override
    protected Vec3 getRiddenInput(Player controller, Vec3 selfInput) {
        float forward = controller.zza <= 0.0f ? controller.zza * 0.25f : controller.zza;
        return new Vec3(controller.xxa * 0.5f, 0.0, forward);
    }

    @Override
    protected float getRiddenSpeed(Player controller) {
        return (float) getAttributeValue(Attributes.MOVEMENT_SPEED);
    }

    @Override
    public boolean isPushable() {
        return (definition == null || definition.pushable()) && super.isPushable();
    }

    @Override
    public String toString() {
        return "PlexusMob[" + BuiltInRegistries.ENTITY_TYPE.getKey(getType()) + "]";
    }
}
