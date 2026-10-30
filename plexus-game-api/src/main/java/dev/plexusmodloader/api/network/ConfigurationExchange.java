package dev.plexusmodloader.api.network;

import dev.plexusmodloader.api.resource.ResourceId;
import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;
import java.util.Optional;
import java.util.UUID;

/**
 * A question the server asks each joining client before the player enters the world, during the
 * game's configuration phase: to check versions, agree on settings, or send data the client needs
 * first. The player joins only after the exchange finishes, and the server may refuse the connection.
 *
 * <ol>
 *   <li>The server's {@link #serverRequest} decides what to send to the player, or skips them.
 *   <li>The client's {@link #clientAnswer} replies.
 *   <li>The server's {@link #serverCheck} sees the reply, or nothing if the client did not answer
 *       within {@link #timeoutTicks} (a client without the mod never answers), and lets the player in
 *       or refuses with a message.
 * </ol>
 *
 * <p>Set the server parts in a common entrypoint and the client part in a client entrypoint. Without
 * a {@link #serverCheck}, a player whose client gives no answer is refused. Server parts run on the
 * server thread, the client part on the client thread.
 *
 * @param <T> the message type in both directions
 */
@ApiStability(StabilityLevel.PREVIEW)
public interface ConfigurationExchange<T> {

    /** The exchange's ID, {@code <mod id>:<path>}. */
    ResourceId id();

    /** What the server sends to each joining player; without it the exchange is skipped. */
    ConfigurationExchange<T> serverRequest(Request<T> request);

    /** How the client answers; without it the client does not answer. */
    ConfigurationExchange<T> clientAnswer(Answer<T> answer);

    /** Whether the server lets the player in after the client's answer. */
    ConfigurationExchange<T> serverCheck(Check<T> check);

    /**
     * How long the server waits for the answer, in ticks; 100 (5 seconds) by default.
     *
     * @throws IllegalArgumentException outside 1–1200
     */
    ConfigurationExchange<T> timeoutTicks(int ticks);

    /** The server's message for a joining player. */
    @FunctionalInterface
    interface Request<T> {
        /**
         * The message for player {@code playerName}, or empty to skip the exchange for them.
         *
         * @param playerId the player's UUID
         * @param playerName the player's name
         */
        Optional<T> request(UUID playerId, String playerName);
    }

    /** The client's reply. */
    @FunctionalInterface
    interface Answer<T> {
        /** The reply to the server's {@code request}. */
        T answer(T request);
    }

    /** The server's decision. */
    @FunctionalInterface
    interface Check<T> {
        /**
         * Empty to let the player in, or the message shown to them when the connection is refused.
         *
         * @param playerId the player's UUID
         * @param playerName the player's name
         * @param reply the client's answer, or empty when it did not answer in time or the answer could
         *     not be decoded
         */
        Optional<String> check(UUID playerId, String playerName, Optional<T> reply);
    }
}
