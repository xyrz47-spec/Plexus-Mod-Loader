package dev.polymodloader.compat.fabric.loader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import net.fabricmc.loader.api.ObjectShare;

/**
 * Fabric Loader's {@link ObjectShare}: values shared between mods under {@code modid:subkey} keys. Keys are
 * checked as Fabric checks them; a listener registered with {@link #whenAvailable} runs once, when the key gets
 * a value.
 */
final class FabricObjectShare implements ObjectShare {

    private final Map<String, Object> values = new HashMap<>();
    private final Map<String, List<BiConsumer<String, Object>>> pending = new HashMap<>();

    private static void validateKey(String key) {
        Objects.requireNonNull(key, "null key");
        int colon = key.indexOf(':');
        if (colon <= 0 || colon >= key.length() - 1) {
            throw new IllegalArgumentException("invalid key, must be modid:subkey");
        }
    }

    @Override
    public synchronized Object get(String key) {
        validateKey(key);
        return values.get(key);
    }

    @Override
    public void whenAvailable(String key, BiConsumer<String, Object> consumer) {
        validateKey(key);
        Object value;
        synchronized (this) {
            value = values.get(key);
            if (value == null) {
                pending.computeIfAbsent(key, ignored -> new ArrayList<>()).add(consumer);
                return;
            }
        }
        consumer.accept(key, value);
    }

    @Override
    public Object put(String key, Object value) {
        validateKey(key);
        Objects.requireNonNull(value, "null value");
        Object previous;
        List<BiConsumer<String, Object>> listeners;
        synchronized (this) {
            previous = values.put(key, value);
            listeners = pending.remove(key);
        }
        if (listeners != null) {
            listeners.forEach(listener -> listener.accept(key, value));
        }
        return previous;
    }

    @Override
    public Object putIfAbsent(String key, Object value) {
        validateKey(key);
        Objects.requireNonNull(value, "null value");
        synchronized (this) {
            Object existing = values.get(key);
            if (existing != null) {
                return existing;
            }
        }
        put(key, value);
        return null;
    }

    @Override
    public synchronized Object remove(String key) {
        validateKey(key);
        return values.remove(key);
    }
}
