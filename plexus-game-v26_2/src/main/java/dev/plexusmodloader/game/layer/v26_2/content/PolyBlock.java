package dev.plexusmodloader.game.layer.v26_2.content;

import dev.plexusmodloader.api.content.block.BlockBehavior;
import dev.plexusmodloader.api.content.block.BlockDefinition;
import dev.plexusmodloader.api.content.block.BlockShape;
import dev.plexusmodloader.api.content.block.BlockUse;
import dev.plexusmodloader.api.content.block.state.BlockProperty;
import dev.plexusmodloader.api.content.block.state.BlockStateView;
import dev.plexusmodloader.game.layer.v26_2.Views;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A mod block: a game block whose state properties come from the {@link BlockDefinition} and whose
 * callbacks forward to the mod's {@link BlockBehavior}.
 */
class PolyBlock extends Block {

    /**
     * The definition of the block being constructed. {@code Block}'s constructor calls {@link
     * #createBlockStateDefinition} before this class's fields are assigned, so the properties are
     * handed over through this thread-local.
     */
    private static final ThreadLocal<BlockDefinition> CONSTRUCTING = new ThreadLocal<>();

    private final BlockDefinition definition;
    private final Map<BlockProperty<?>, Property<?>> gameProperties;

    /** Outline and collision per state, when the definition gives shapes; computed once. */
    private final Map<BlockState, VoxelShape> shapes;

    private final Map<BlockState, VoxelShape> collisionShapes;

    PolyBlock(Properties properties, BlockDefinition definition) {
        super(beginConstruction(properties, definition));
        CONSTRUCTING.remove();
        this.definition = definition;
        Map<BlockProperty<?>, Property<?>> byProperty = new LinkedHashMap<>();
        for (BlockProperty<?> property : definition.properties()) {
            byProperty.put(property, getStateDefinition().getProperty(property.name()));
        }
        this.gameProperties = Map.copyOf(byProperty);
        BlockState defaults = getStateDefinition().any();
        for (BlockProperty<?> property : definition.properties()) {
            defaults = withDefault(defaults, property);
        }
        registerDefaultState(defaults);
        this.shapes = shapes(definition.shape());
        this.collisionShapes = definition.collisionShape().isPresent() ? shapes(definition.collisionShape()) : shapes;
    }

    private Map<BlockState, VoxelShape> shapes(Optional<Function<BlockStateView, BlockShape>> byState) {
        if (byState.isEmpty()) {
            return Map.of();
        }
        Map<BlockState, VoxelShape> computed = new IdentityHashMap<>();
        for (BlockState state : getStateDefinition().getPossibleStates()) {
            BlockShape shape;
            try {
                shape = byState.get().apply(BlockStates.view(state));
            } catch (RuntimeException e) {
                throw new IllegalStateException(
                        "PLEXUS-CONTENT-006 the shape function of a mod block failed for " + state, e);
            }
            if (shape == null) {
                throw new IllegalStateException(
                        "PLEXUS-CONTENT-006 the shape function of a mod block returned null for " + state);
            }
            computed.put(state, toGame(shape));
        }
        return computed;
    }

    static VoxelShape toGame(BlockShape shape) {
        VoxelShape result = Shapes.empty();
        for (BlockShape.Box box : shape.boxes()) {
            result = Shapes.or(
                    result, Block.box(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()));
        }
        return result.optimize();
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = shapes.get(state);
        return shape != null ? shape : super.getShape(state, level, pos, context);
    }

    @Override
    protected VoxelShape getCollisionShape(
            BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (!definition.collision()) {
            return Shapes.empty();
        }
        VoxelShape shape = collisionShapes.get(state);
        return shape != null ? shape : super.getCollisionShape(state, level, pos, context);
    }

    private static Properties beginConstruction(Properties properties, BlockDefinition definition) {
        CONSTRUCTING.set(definition);
        return properties;
    }

    private static <T> BlockState withDefault(BlockState state, BlockProperty<T> property) {
        return BlockStates.with(state, property, property.defaultValue());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        BlockDefinition constructing = CONSTRUCTING.get();
        if (constructing != null) {
            for (BlockProperty<?> property : constructing.properties()) {
                builder.add(BlockStates.create(property));
            }
        }
    }

    BlockDefinition definition() {
        return definition;
    }

    BlockBehavior behavior() {
        return definition.behavior();
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        if (state != null && definition.placementFacing().isPresent()) {
            state = BlockStates.with(
                    state,
                    definition.placementFacing().get(),
                    BlockStates.fromGame(context.getHorizontalDirection().getOpposite()));
        }
        return state;
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return Interactions.toGame(
                behavior().onUse(new BlockUse(Views.world(level), Views.pos(pos), Views.player(player))),
                () -> super.useWithoutItem(state, level, pos, player, hit));
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        behavior().onSteppedOn(Views.world(level), Views.pos(pos), Views.entity(entity));
        super.stepOn(level, pos, state, entity);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer != null) {
            behavior().onPlaced(Views.world(level), Views.pos(pos), Views.entity(placer));
        }
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return definition.randomTicks();
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        behavior().onRandomTick(Views.world(level), Views.pos(pos));
    }

    @Override
    protected void neighborChanged(
            BlockState state, Level level, BlockPos pos, Block neighbor, Orientation orientation, boolean moved) {
        super.neighborChanged(state, level, pos, neighbor, orientation, moved);
        behavior().onNeighborChanged(Views.world(level), Views.pos(pos));
    }

    @Override
    public String toString() {
        return "PolyBlock" + gameProperties.keySet();
    }
}
