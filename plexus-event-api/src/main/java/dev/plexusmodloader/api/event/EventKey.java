package dev.plexusmodloader.api.event;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;
import java.util.Objects;

/**
 * Identifies one event and the listener interface it calls. Keys are compared by identity; each
 * event has exactly one key constant, e.g. {@code ServerEvents.STARTED}.
 *
 * @param <L> listener interface
 */
@ApiStability(StabilityLevel.PREVIEW)
public final class EventKey<L> {

    private final String id;
    private final Class<L> listenerType;

    private EventKey(String id, Class<L> listenerType) {
        this.id = Objects.requireNonNull(id, "id");
        this.listenerType = Objects.requireNonNull(listenerType, "listenerType");
    }

    /** Creates a key; {@code id} is {@code namespace:path} and only used for diagnostics. */
    public static <L> EventKey<L> of(String id, Class<L> listenerType) {
        return new EventKey<>(id, listenerType);
    }

    /** Diagnostic ID, e.g. {@code plexus:server/started}. */
    public String id() {
        return id;
    }

    /** The listener interface. */
    public Class<L> listenerType() {
        return listenerType;
    }

    @Override
    public String toString() {
        return id;
    }
}
