package dev.plexusmodloader.runtime.nativeapi;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.plexusmodloader.api.network.PayloadCodec;
import dev.plexusmodloader.api.resource.ResourceId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConfigurationExchangeImplTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static ConfigurationExchangeImpl<String> exchange() {
        return new ConfigurationExchangeImpl<>(
                ResourceId.of("test_mod", "handshake"), "test_mod", PayloadCodec.string());
    }

    @Test
    void requestAnswerAndCheckRoundTrip() {
        ConfigurationExchangeImpl<String> exchange = exchange();
        exchange.serverRequest((id, name) -> Optional.of("version?"))
                .clientAnswer(request -> request.equals("version?") ? "1.0" : "?")
                .serverCheck((id, name, reply) ->
                        reply.filter("1.0"::equals).isPresent() ? Optional.empty() : Optional.of("old client"));

        byte[] request = exchange.request(PLAYER, "Alex").orElseThrow();
        byte[] answer = exchange.answer(request).orElseThrow();

        assertEquals(Optional.empty(), exchange.check(PLAYER, "Alex", Optional.of(answer)));
        assertEquals(Optional.of("old client"), exchange.check(PLAYER, "Alex", Optional.empty()));
    }

    @Test
    void missingPartsSkipOrRefuse() {
        ConfigurationExchangeImpl<String> exchange = exchange();

        assertTrue(exchange.request(PLAYER, "Alex").isEmpty(), "no server request: skipped");
        assertTrue(exchange.answer(new byte[] {0}).isEmpty(), "no client answer: silent");
        assertTrue(
                exchange.check(PLAYER, "Alex", Optional.empty()).orElseThrow().contains("test_mod"));
        byte[] encoded = PayloadBytes.encode(PayloadCodec.string(), "hi", 100, exchange.id());
        assertEquals(Optional.empty(), exchange.check(PLAYER, "Alex", Optional.of(encoded)), "any answer passes");
    }

    @Test
    void failuresAreContained() {
        ConfigurationExchangeImpl<String> exchange = exchange();
        exchange.serverRequest((id, name) -> {
                    throw new IllegalStateException("boom");
                })
                .clientAnswer(request -> {
                    throw new IllegalStateException("boom");
                })
                .serverCheck((id, name, reply) -> {
                    throw new IllegalStateException("boom");
                });

        assertTrue(exchange.request(PLAYER, "Alex").isEmpty());
        byte[] request = PayloadBytes.encode(PayloadCodec.string(), "x", 100, exchange.id());
        assertTrue(exchange.answer(request).isEmpty());
        assertTrue(exchange.check(PLAYER, "Alex", Optional.of(request)).isPresent(), "a failing check refuses");
        assertTrue(exchange.check(PLAYER, "Alex", Optional.of(new byte[] {(byte) 0xFF}))
                .isPresent());
        assertArrayEquals(request, PayloadBytes.encode(PayloadCodec.string(), "x", 100, exchange.id()));
        assertThrows(IllegalArgumentException.class, () -> exchange.timeoutTicks(0));
    }
}
