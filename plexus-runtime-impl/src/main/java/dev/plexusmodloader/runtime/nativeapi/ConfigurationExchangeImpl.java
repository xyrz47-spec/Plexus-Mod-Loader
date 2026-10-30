package dev.plexusmodloader.runtime.nativeapi;

import dev.plexusmodloader.api.network.ConfigurationExchange;
import dev.plexusmodloader.api.network.PayloadCodec;
import dev.plexusmodloader.api.resource.ResourceId;
import dev.plexusmodloader.game.version.layer.ConfigurationEndpoint;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A declared configuration exchange: holds the mod's parts, encodes and decodes with its codec, and
 * turns the mod's exceptions into {@code PLEXUS-NETWORK-005} log entries.
 */
final class ConfigurationExchangeImpl<T> implements ConfigurationExchange<T>, ConfigurationEndpoint {

    private static final Logger LOGGER = LoggerFactory.getLogger("Poly Mod Loader");

    private final ResourceId id;
    private final String ownerModId;
    private final PayloadCodec<T> codec;
    private volatile Request<T> request;
    private volatile Answer<T> answer;
    private volatile Check<T> check;
    private volatile int timeoutTicks = 100;

    ConfigurationExchangeImpl(ResourceId id, String ownerModId, PayloadCodec<T> codec) {
        this.id = id;
        this.ownerModId = ownerModId;
        this.codec = codec;
    }

    @Override
    public ResourceId id() {
        return id;
    }

    @Override
    public String ownerModId() {
        return ownerModId;
    }

    @Override
    public ConfigurationExchange<T> serverRequest(Request<T> request) {
        this.request = Objects.requireNonNull(request, "request");
        return this;
    }

    @Override
    public ConfigurationExchange<T> clientAnswer(Answer<T> answer) {
        this.answer = Objects.requireNonNull(answer, "answer");
        return this;
    }

    @Override
    public ConfigurationExchange<T> serverCheck(Check<T> check) {
        this.check = Objects.requireNonNull(check, "check");
        return this;
    }

    @Override
    public ConfigurationExchange<T> timeoutTicks(int ticks) {
        if (ticks < 1 || ticks > 1200) {
            throw new IllegalArgumentException("PLEXUS-NETWORK-005 timeout must be 1-1200 ticks: " + ticks);
        }
        this.timeoutTicks = ticks;
        return this;
    }

    @Override
    public int timeoutTicks() {
        return timeoutTicks;
    }

    @Override
    public Optional<byte[]> request(UUID playerId, String playerName) {
        Request<T> current = request;
        if (current == null) {
            return Optional.empty();
        }
        try {
            return current.request(playerId, playerName)
                    .map(message -> PayloadBytes.encode(codec, message, PayloadChannelImpl.MAX_TO_CLIENT, id));
        } catch (RuntimeException e) {
            LOGGER.error(
                    "[Poly Mod Loader] PLEXUS-NETWORK-005 request of {} ({}) failed; the exchange is skipped",
                    id,
                    ownerModId,
                    e);
            return Optional.empty();
        }
    }

    @Override
    public Optional<byte[]> answer(byte[] requestBytes) {
        Answer<T> current = answer;
        if (current == null) {
            return Optional.empty();
        }
        try {
            return PayloadBytes.decode(codec, requestBytes, id, ownerModId)
                    .map(current::answer)
                    .map(reply -> PayloadBytes.encode(codec, reply, PayloadChannelImpl.MAX_TO_SERVER, id));
        } catch (RuntimeException e) {
            LOGGER.error(
                    "[Poly Mod Loader] PLEXUS-NETWORK-005 answer of {} ({}) failed; no answer is sent",
                    id,
                    ownerModId,
                    e);
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> check(UUID playerId, String playerName, Optional<byte[]> reply) {
        Optional<T> decoded = reply.flatMap(bytes -> PayloadBytes.decode(codec, bytes, id, ownerModId));
        Check<T> current = check;
        if (current == null) {
            return decoded.isPresent()
                    ? Optional.empty()
                    : Optional.of("This server needs the mod " + ownerModId + " on your client.");
        }
        try {
            return current.check(playerId, playerName, decoded);
        } catch (RuntimeException e) {
            LOGGER.error(
                    "[Poly Mod Loader] PLEXUS-NETWORK-005 check of {} ({}) failed; the player is refused",
                    id,
                    ownerModId,
                    e);
            return Optional.of("The server could not check your connection (" + id + ").");
        }
    }
}
