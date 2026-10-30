package dev.polymodloader.game.version.layer;

import dev.polymodloader.api.resource.ResourceId;
import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;
import java.util.Optional;
import java.util.UUID;

/**
 * A mod's configuration exchange as the game layer runs it: encoded messages in, decisions out. The
 * runtime decodes with the mod's codec and catches the mod's exceptions ({@code PLEXUS-NETWORK-005}).
 */
@ApiStability(StabilityLevel.INTERNAL)
public interface ConfigurationEndpoint {

    ResourceId id();

    String ownerModId();

    /** Ticks the server waits for the answer. */
    int timeoutTicks();

    /** Server: the encoded request for a joining player, or empty to skip them. */
    Optional<byte[]> request(UUID playerId, String playerName);

    /** Client: the encoded answer to an encoded request, or empty for no answer. */
    Optional<byte[]> answer(byte[] request);

    /** Server: empty to let the player in, or the refusal message; {@code reply} is empty without an answer. */
    Optional<String> check(UUID playerId, String playerName, Optional<byte[]> reply);
}
