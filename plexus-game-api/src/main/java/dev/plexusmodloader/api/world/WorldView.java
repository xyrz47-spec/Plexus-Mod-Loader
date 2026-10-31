package dev.plexusmodloader.api.world;

import dev.plexusmodloader.api.content.block.entity.BlockEntityView;
import dev.plexusmodloader.api.content.block.state.BlockProperty;
import dev.plexusmodloader.api.content.block.state.BlockStateView;
import dev.plexusmodloader.api.entity.PlayerView;
import dev.plexusmodloader.api.resource.ResourceId;
import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;
import java.util.List;
import java.util.Optional;

/**
 * Read and write access to one loaded dimension.
 *
 * <p><b>Thread:</b> only on the thread that handed out the view (the server or client thread).
 * <b>Lifetime:</b> views passed to callbacks are valid only during the callback; do not store them.
 */
@ApiStability(StabilityLevel.PREVIEW)
public interface WorldView {

    /** The dimension, for example {@code minecraft:overworld}. */
    ResourceId dimension();

    /** Whether this is the client's copy of the world, which must not be changed. */
    boolean isClientSide();

    /** The block at {@code pos}, for example {@code minecraft:air}. Unloaded positions read as air. */
    ResourceId blockAt(BlockPos pos);

    /**
     * Replaces the block at {@code pos} with the default state of {@code block} and notifies
     * neighbours and clients.
     *
     * @return whether the block changed
     * @throws IllegalArgumentException when {@code block} is not a registered block
     * @throws IllegalStateException on the client side
     */
    boolean setBlock(BlockPos pos, ResourceId block);

    /** Ticks since the world was created. */
    long gameTime();

    /** Players in this dimension. */
    List<PlayerView> players();

    /** The block and its property values at {@code pos}. */
    BlockStateView stateAt(BlockPos pos);

    /**
     * Changes one property of the block at {@code pos}, keeping the others.
     *
     * @return whether the state changed
     * @throws IllegalArgumentException when the block has no such property or the value is not allowed
     * @throws IllegalStateException on the client side
     */
    <T> boolean setProperty(BlockPos pos, BlockProperty<T> property, T value);

    /** The mod block entity at {@code pos}, if there is one. */
    Optional<BlockEntityView> blockEntityAt(BlockPos pos);

    /**
     * Data saved with this dimension under {@code id} (use your mod ID as namespace), created empty
     * on first use.
     *
     * @throws IllegalStateException on the client side
     */
    StoredData storage(ResourceId id);

    /**
     * Runs {@code task} on the server thread after {@code ticks} ticks of this dimension (0 = next
     * tick). Pending tasks are dropped when the server stops; they are not saved.
     *
     * @throws IllegalStateException on the client side
     */
    void schedule(int ticks, Runnable task);

    /**
     * World storage with a data version and optional client synchronisation. Saved data records its
     * version; loading older data runs the migration first. The key {@code plexus_version} is reserved.
     * On the client this returns the synchronised copy (empty until the server sends it), read-only.
     */
    StoredData storage(ResourceId id, StorageOptions options);

    /**
     * Spawns an entity of {@code type} (for example {@code minecraft:pig} or a mod mob) at the
     * position; server side only.
     *
     * @throws IllegalArgumentException for an unknown or unspawnable type
     */
    dev.plexusmodloader.api.entity.EntityView spawnEntity(ResourceId type, double x, double y, double z);

    /** Entities whose position is within {@code radius} blocks of the point. */
    List<dev.plexusmodloader.api.entity.EntityView> entitiesNear(double x, double y, double z, double radius);

    /**
     * Plays a sound to players near {@code pos} (server) or locally (client).
     *
     * @throws IllegalArgumentException for an unknown sound event
     */
    void playSound(BlockPos pos, ResourceId sound, float volume, float pitch);

    /**
     * Shows a simple particle (one without options, such as {@code minecraft:heart} or
     * {@code minecraft:flame}) to nearby players; server side only.
     *
     * @param spread random offset in blocks on each axis
     * @throws IllegalArgumentException for an unknown particle or one that needs options
     */
    void spawnParticles(ResourceId particle, double x, double y, double z, int count, double spread);

    /** The current value of a game rule in this world; server side only. */
    <T> T gameRule(dev.plexusmodloader.api.content.rule.GameRuleKey<T> rule);

    /** Changes a game rule for this world; server side only. */
    <T> void setGameRule(dev.plexusmodloader.api.content.rule.GameRuleKey<T> rule, T value);

    /**
     * Keeps a chunk loaded (and ticking) until released, like {@code /forceload}; saved with the
     * world. Chunk coordinates are block coordinates divided by 16 (rounded down).
     *
     * @return whether the setting changed
     * @throws IllegalStateException on the client side
     */
    boolean setChunkForced(int chunkX, int chunkZ, boolean forced);

    /** Whether the chunk is loaded. */
    boolean isChunkLoaded(int chunkX, int chunkZ);

    /** The biome at {@code pos}, for example {@code minecraft:plains}. */
    ResourceId biomeAt(BlockPos pos);

    /** The block, item, and entity type tags the game loaded (the server's data, or the client's copy). */
    dev.plexusmodloader.api.tag.TagView tags();

    /**
     * Whether the block at {@code pos} is in a block tag, for example {@code #minecraft:logs}.
     * Unloaded positions read as air.
     *
     * @throws IllegalArgumentException when {@code tag} is not a block tag
     */
    boolean isBlockIn(BlockPos pos, dev.plexusmodloader.api.tag.TagKey tag);

    /** Light the block at {@code pos} emits, 0–15. */
    int emittedLight(BlockPos pos);

    /**
     * Mobs that can spawn by themselves at {@code pos} in {@code category}, by the biome there; for
     * checking natural spawn settings.
     */
    List<ResourceId> naturalSpawns(BlockPos pos, dev.plexusmodloader.api.content.entity.MobCategory category);

    /** Another loaded dimension of the same server, such as a mod dimension from data; server side only. */
    Optional<WorldView> otherDimension(ResourceId dimension);
}
