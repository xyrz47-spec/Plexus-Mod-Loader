package dev.plexusmodloader.runtime.event;

import dev.plexusmodloader.api.event.EventKey;
import dev.plexusmodloader.api.event.EventPriority;
import dev.plexusmodloader.api.event.EventRegistry;
import dev.plexusmodloader.game.version.EventPublisher;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holds event listeners per mod and dispatches events from version adapter hooks. Listeners are kept
 * sorted by {@link EventPriority} (stable, so equal priorities keep registration order); firing
 * iterates a copy-on-write snapshot without locking. A listener that throws is disabled after
 * reporting {@code PLEXUS-EVENT-001} with its mod ID, so one broken mod cannot crash the game or flood
 * the log every tick.
 */
public final class EventBus implements EventPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger("Plexus Mod Loader");

    private static final class Registration<L> {
        final String modId;
        final EventPriority priority;
        final L listener;
        volatile boolean enabled = true;

        Registration(String modId, EventPriority priority, L listener) {
            this.modId = modId;
            this.priority = priority;
            this.listener = listener;
        }
    }

    private final Map<EventKey<?>, List<Registration<?>>> listeners = new ConcurrentHashMap<>();

    /** A registry that records {@code modId} as the owner of every listener it registers. */
    public EventRegistry registryFor(String modId) {
        return new EventRegistry() {
            @Override
            public <L> void register(EventKey<L> event, EventPriority priority, L listener) {
                Objects.requireNonNull(event, "event");
                Objects.requireNonNull(priority, "priority");
                Objects.requireNonNull(listener, "listener");
                if (!event.listenerType().isInstance(listener)) {
                    throw new IllegalArgumentException("Listener for " + event + " must implement "
                            + event.listenerType().getName());
                }
                List<Registration<?>> registered =
                        listeners.computeIfAbsent(event, key -> new CopyOnWriteArrayList<>());
                synchronized (registered) {
                    // After every listener of the same or a higher priority: stable within a priority.
                    int index = registered.size();
                    while (index > 0 && registered.get(index - 1).priority.compareTo(priority) > 0) {
                        index--;
                    }
                    registered.add(index, new Registration<>(modId, priority, listener));
                }
            }
        };
    }

    @Override
    public boolean hasListeners(EventKey<?> event) {
        List<Registration<?>> registered = listeners.get(event);
        return registered != null && !registered.isEmpty();
    }

    @Override
    public <L> void publish(EventKey<L> event, Consumer<L> invocation) {
        List<Registration<?>> registered = listeners.get(event);
        if (registered == null) {
            return;
        }
        for (Registration<?> registration : registered) {
            if (!registration.enabled) {
                continue;
            }
            try {
                invocation.accept(event.listenerType().cast(registration.listener));
            } catch (RuntimeException | LinkageError e) {
                disable(event, registration, e);
            }
        }
    }

    @Override
    public <L, R> R publishUntilDecided(EventKey<L> event, Function<L, R> invocation, R undecided) {
        List<Registration<?>> registered = listeners.get(event);
        if (registered == null) {
            return undecided;
        }
        for (Registration<?> registration : registered) {
            if (!registration.enabled) {
                continue;
            }
            R result;
            try {
                result = invocation.apply(event.listenerType().cast(registration.listener));
            } catch (RuntimeException | LinkageError e) {
                disable(event, registration, e);
                continue;
            }
            if (result == null) {
                registration.enabled = false;
                LOGGER.error(
                        "[Plexus Mod Loader] PLEXUS-EVENT-003 mod={} event={} listener {} returned null instead of a result;"
                                + " the listener is disabled",
                        registration.modId,
                        event,
                        registration.listener.getClass().getName());
                continue;
            }
            if (!result.equals(undecided)) {
                return result;
            }
        }
        return undecided;
    }

    private static void disable(EventKey<?> event, Registration<?> registration, Throwable failure) {
        registration.enabled = false;
        LOGGER.error(
                "[Plexus Mod Loader] PLEXUS-EVENT-001 mod={} event={} listener {} threw {}; the listener is disabled",
                registration.modId,
                event,
                registration.listener.getClass().getName(),
                failure.toString(),
                failure);
    }

    /** Number of enabled listeners for {@code event}; for diagnostics and tests. */
    public int enabledListenerCount(EventKey<?> event) {
        List<Registration<?>> registered = listeners.get(event);
        return registered == null
                ? 0
                : (int) registered.stream()
                        .filter(registration -> registration.enabled)
                        .count();
    }
}
