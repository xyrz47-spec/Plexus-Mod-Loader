package dev.plexusmodloader.runtime.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.plexusmodloader.api.event.EventKey;
import dev.plexusmodloader.api.event.EventPriority;
import dev.plexusmodloader.api.event.EventRegistry;
import dev.plexusmodloader.api.event.EventResult;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class EventBusTest {

    interface Ping {
        void ping(String message);
    }

    interface Decide {
        EventResult decide(String action);
    }

    private static final EventKey<Ping> PING = EventKey.of("test:ping", Ping.class);
    private static final EventKey<Decide> DECIDE = EventKey.of("test:decide", Decide.class);

    @Test
    void listenersRunInRegistrationOrder() {
        EventBus bus = new EventBus();
        List<String> calls = new ArrayList<>();
        bus.registryFor("first").register(PING, message -> calls.add("first:" + message));
        bus.registryFor("second").register(PING, message -> calls.add("second:" + message));

        bus.publish(PING, listener -> listener.ping("hi"));

        assertEquals(List.of("first:hi", "second:hi"), calls);
    }

    @Test
    void aThrowingListenerIsDisabledWithoutAffectingOthers() {
        EventBus bus = new EventBus();
        List<String> calls = new ArrayList<>();
        bus.registryFor("broken").register(PING, message -> {
            throw new IllegalStateException("boom");
        });
        bus.registryFor("healthy").register(PING, calls::add);

        bus.publish(PING, listener -> listener.ping("one"));
        bus.publish(PING, listener -> listener.ping("two"));

        assertEquals(List.of("one", "two"), calls);
        assertEquals(1, bus.enabledListenerCount(PING));
    }

    @Test
    void reportsWhetherAnEventHasListeners() {
        EventBus bus = new EventBus();
        assertFalse(bus.hasListeners(PING));

        bus.registryFor("mod").register(PING, message -> {});

        assertTrue(bus.hasListeners(PING));
    }

    @Test
    void rejectsListenersOfTheWrongType() {
        EventBus bus = new EventBus();
        @SuppressWarnings({"unchecked", "rawtypes"})
        EventKey<Object> raw = (EventKey) PING;

        assertThrows(
                IllegalArgumentException.class, () -> bus.registryFor("mod").register(raw, new Object()));
    }

    @Test
    void listenersRunByPriorityThenRegistrationOrder() {
        EventBus bus = new EventBus();
        List<String> calls = new ArrayList<>();
        bus.registryFor("low").register(PING, EventPriority.LOW, message -> calls.add("low"));
        bus.registryFor("normal1").register(PING, message -> calls.add("normal1"));
        bus.registryFor("highest").register(PING, EventPriority.HIGHEST, message -> calls.add("highest"));
        bus.registryFor("normal2").register(PING, EventPriority.NORMAL, message -> calls.add("normal2"));
        bus.registryFor("lowest").register(PING, EventPriority.LOWEST, message -> calls.add("lowest"));
        bus.registryFor("high").register(PING, EventPriority.HIGH, message -> calls.add("high"));

        bus.publish(PING, listener -> listener.ping("x"));

        assertEquals(List.of("highest", "high", "normal1", "normal2", "low", "lowest"), calls);
    }

    @Test
    void theFirstDecidingListenerEndsADecisionEvent() {
        EventBus bus = new EventBus();
        List<String> calls = new ArrayList<>();
        bus.registryFor("observer").register(DECIDE, EventPriority.HIGH, action -> {
            calls.add("observer");
            return EventResult.PASS;
        });
        bus.registryFor("protection").register(DECIDE, action -> {
            calls.add("protection");
            return action.equals("break") ? EventResult.DENY : EventResult.PASS;
        });
        bus.registryFor("late").register(DECIDE, EventPriority.LOW, action -> {
            calls.add("late");
            return EventResult.ALLOW;
        });

        EventResult breakResult =
                bus.publishUntilDecided(DECIDE, listener -> listener.decide("break"), EventResult.PASS);
        assertEquals(EventResult.DENY, breakResult);
        assertEquals(List.of("observer", "protection"), calls);

        calls.clear();
        EventResult placeResult =
                bus.publishUntilDecided(DECIDE, listener -> listener.decide("place"), EventResult.PASS);
        assertEquals(EventResult.ALLOW, placeResult);
        assertEquals(List.of("observer", "protection", "late"), calls);
    }

    @Test
    void aDecisionEventWithoutListenersOrDecisionsIsUndecided() {
        EventBus bus = new EventBus();
        assertEquals(
                EventResult.PASS, bus.publishUntilDecided(DECIDE, listener -> listener.decide("x"), EventResult.PASS));

        bus.registryFor("mod").register(DECIDE, action -> EventResult.PASS);
        assertEquals(
                EventResult.PASS, bus.publishUntilDecided(DECIDE, listener -> listener.decide("x"), EventResult.PASS));
    }

    @Test
    void failingOrNullDecisionsCountAsUndecidedAndDisableTheListener() {
        EventBus bus = new EventBus();
        bus.registryFor("broken").register(DECIDE, EventPriority.HIGHEST, action -> {
            throw new IllegalStateException("boom");
        });
        bus.registryFor("null").register(DECIDE, EventPriority.HIGH, action -> null);
        bus.registryFor("healthy").register(DECIDE, action -> EventResult.DENY);

        assertEquals(
                EventResult.DENY, bus.publishUntilDecided(DECIDE, listener -> listener.decide("x"), EventResult.PASS));
        assertEquals(1, bus.enabledListenerCount(DECIDE));
    }

    @Test
    void rejectsNullArguments() {
        EventRegistry registry = new EventBus().registryFor("mod");
        Ping listener = message -> {};

        assertThrows(NullPointerException.class, () -> registry.register(PING, null, listener));
        assertThrows(NullPointerException.class, () -> registry.register(PING, EventPriority.NORMAL, null));
    }
}
