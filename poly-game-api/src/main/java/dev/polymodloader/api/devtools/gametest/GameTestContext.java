package dev.polymodloader.api.devtools.gametest;

import dev.polymodloader.api.entity.EntityView;
import dev.polymodloader.api.entity.PlayerView;
import dev.polymodloader.api.resource.ResourceId;
import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;
import dev.polymodloader.api.world.BlockPos;
import dev.polymodloader.api.world.Direction;
import dev.polymodloader.api.world.InteractionResult;
import dev.polymodloader.api.world.WorldView;

/**
 * A running game test. Positions are relative to the test area; {@code (0, 0, 0)} is its lowest
 * corner and the area is at least 1×1×1 blocks plus room above.
 *
 * <p>Assertions throw an exception the game reports as a test failure with the position and message.
 */
@ApiStability(StabilityLevel.PREVIEW)
public interface GameTestContext {

    /** The world the test runs in. */
    WorldView world();

    /** Converts a test-relative position into a world position. */
    BlockPos absolute(BlockPos relative);

    /** Places the default state of {@code block} at a test-relative position. */
    void setBlock(BlockPos relative, ResourceId block);

    /** Fails unless the block at a test-relative position is {@code expected}. */
    void assertBlock(BlockPos relative, ResourceId expected);

    /** Fails with {@code message} unless {@code condition} holds. */
    void assertTrue(boolean condition, String message);

    /** Creates a survival-mode player in the test world that is not connected to any client. */
    PlayerView createMockPlayer();

    /**
     * Creates a survival-mode server player that is not connected and not in the world, for what
     * needs one, such as advancements.
     */
    PlayerView createMockServerPlayer();

    /**
     * Creates a survival-mode player that joins the server like a client over an in-memory connection
     * ({@code PlayerEvents.JOINED} fires) and reports that it finished loading, so it can take damage.
     * It stands where the game puts new players. Interactions work as for {@link
     * #createMockServerPlayer()}.
     *
     * <p>While connected it affects the whole test world like a real player: mobs far from every player
     * despawn and mobs spawn near it, which disturbs other tests running at the same time. Call {@link
     * #disconnect} before the test function returns; players still connected when the test ends are
     * disconnected then.
     */
    PlayerView createConnectedMockPlayer();

    /**
     * Respawns a player from {@link #createConnectedMockPlayer()} as its client asks from the death
     * screen (or after the End credits): the game builds a new player object, {@code
     * PlayerEvents.COPY} and {@code RESPAWNED} fire, and the new player reports that it loaded.
     *
     * @param returnedFromEnd {@code true} to respawn as when leaving the End (everything is kept);
     *     {@code false} to respawn after death
     * @return the new player
     * @throws IllegalArgumentException when {@code player} is not a connected mock player of this test
     * @throws IllegalStateException when respawning after death and the player is alive
     */
    PlayerView respawn(PlayerView player, boolean returnedFromEnd);

    /**
     * Disconnects a player from {@link #createConnectedMockPlayer()} ({@code PlayerEvents.LEAVING}
     * fires); does nothing for a player that already left.
     *
     * @throws IllegalArgumentException when {@code player} is not a connected mock player of this test
     */
    void disconnect(PlayerView player);

    /** Makes {@code player} use (right-click) the block at a test-relative position with an empty hand. */
    void useBlock(BlockPos relative, PlayerView player);

    /**
     * Makes a server player (from {@link #createMockServerPlayer()}) right-click a face of the block at
     * a test-relative position with its main hand, the way the server handles the click from a
     * connected client: interaction events first, then the held item and the block. Unlike {@link
     * #useBlock}, a held block item is placed.
     *
     * @return the result the game reached, mapped onto the Native API's result
     * @throws IllegalArgumentException when {@code player} is not a server player
     */
    InteractionResult interactBlock(PlayerView player, BlockPos relative, Direction face);

    /**
     * Makes a server player use the item in its main hand in the air, the way the server handles the
     * click from a connected client (interaction events, then the item).
     *
     * @throws IllegalArgumentException when {@code player} is not a server player
     */
    InteractionResult interactItem(PlayerView player);

    /**
     * Makes a server player right-click {@code target} with its main hand, the way the server
     * handles the click from a connected client (interaction events, then the entity and the item).
     *
     * @throws IllegalArgumentException when {@code player} is not a server player
     */
    InteractionResult interactEntity(PlayerView player, EntityView target);

    /**
     * Makes a server player attack {@code target} with its main hand, as a left-click does
     * (interaction events, then damage and knockback).
     *
     * @throws IllegalArgumentException when {@code player} is not a server player
     */
    void attackEntity(PlayerView player, EntityView target);

    /**
     * Makes a server player break the block at a test-relative position at once, the way the server
     * finishes a player's digging: block events, the game's checks, drops in survival mode.
     *
     * @return whether the block was broken
     * @throws IllegalArgumentException when {@code player} is not a server player
     */
    boolean breakBlock(PlayerView player, BlockPos relative);

    /**
     * Runs a command as the server at the test area's origin.
     *
     * @return the command's result value; 0 when it failed
     */
    int runCommand(String command);

    /** Runs {@code action} after {@code ticks} more ticks, on the server thread. */
    void runAfter(int ticks, Runnable action);

    /** Marks the test passed. */
    void succeed();

    /** Marks the test failed. */
    void fail(String message);
}
