package dev.plexusmodloader.api.entity;

import dev.plexusmodloader.api.resource.ResourceId;
import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;
import dev.plexusmodloader.api.world.BlockPos;
import dev.plexusmodloader.api.world.Vector3;
import dev.plexusmodloader.api.world.WorldView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * An entity in a loaded world. Same thread and lifetime rules as {@link WorldView}.
 */
@ApiStability(StabilityLevel.PREVIEW)
public interface EntityView {

    /** Unique, persistent entity ID. */
    UUID uuid();

    /** Entity type, for example {@code minecraft:pig}. */
    ResourceId type();

    /**
     * Whether the entity's type is in an entity type tag, for example {@code #minecraft:skeletons}.
     *
     * @throws IllegalArgumentException when {@code tag} is not an entity type tag
     */
    boolean isIn(dev.plexusmodloader.api.tag.TagKey tag);

    /** Display name as plain text. */
    String name();

    /** X coordinate. */
    double x();

    /** Y coordinate (feet). */
    double y();

    /** Z coordinate. */
    double z();

    /** The block the entity stands in. */
    BlockPos blockPos();

    /** The world the entity is in. */
    WorldView world();

    /** Whether the entity is alive and in a world. */
    boolean isAlive();

    /** Removes the entity without dropping loot; server side only. */
    void remove();

    /** Moves the entity within its dimension; server side only. */
    void teleport(double x, double y, double z);

    /**
     * Moves the entity to another dimension, for example {@code minecraft:the_nether} or a mod
     * dimension from data; server side only.
     *
     * @return the view of the entity in the new dimension (it may be a new object)
     * @throws IllegalArgumentException for an unknown or unloaded dimension
     */
    EntityView changeDimension(dev.plexusmodloader.api.resource.ResourceId dimension, double x, double y, double z);

    /** Velocity in blocks per tick. */
    Vector3 velocity();

    /** Sets the velocity; for players it reaches their client. Server side only. */
    void setVelocity(double x, double y, double z);

    /** Whether the entity stands on a block. */
    boolean isOnGround();

    /** Horizontal facing in degrees (0 south, 90 west, 180 north, 270 east). */
    float yaw();

    /** Vertical facing in degrees (-90 up, 90 down). */
    float pitch();

    /** Turns the entity. Server side only. */
    void setRotation(float yaw, float pitch);

    /** Whether gravity is off for this entity. */
    boolean hasNoGravity();

    /** Turns gravity off or on. Server side only. */
    void setNoGravity(boolean noGravity);

    /** What the entity rides, if anything. */
    Optional<EntityView> vehicle();

    /** Who rides the entity. */
    List<EntityView> passengers();

    /**
     * Mounts {@code vehicle}. Mod mobs accept riders only when their definition is rideable.
     *
     * @return whether the entity now rides it
     */
    boolean startRiding(EntityView vehicle);

    /** Dismounts, if riding. */
    void stopRiding();

    /**
     * The entity's data for {@code key}; call {@code markChanged()} after changing it. On the client
     * this is the synchronised copy (the local player's, or any visible entity's for keys synchronised
     * to trackers), read-only, empty until the server sends it.
     *
     * @throws IllegalArgumentException for an undeclared key
     */
    dev.plexusmodloader.api.world.StoredData data(dev.plexusmodloader.api.content.EntityDataKey key);
}
