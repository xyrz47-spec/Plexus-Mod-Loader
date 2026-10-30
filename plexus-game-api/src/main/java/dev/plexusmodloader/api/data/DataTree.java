package dev.plexusmodloader.api.data;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Typed key-value data that Poly Mod Loader saves with the world: block entity data and world storage use it.
 * Values are {@code int}, {@code long}, {@code double}, {@code boolean}, {@link String},
 * {@code byte[]}, or nested trees. Reading a missing key or a key of another type returns the
 * fallback.
 *
 * <p><b>Thread:</b> not thread-safe; use it on the thread that owns the world.
 */
@ApiStability(StabilityLevel.PREVIEW)
public final class DataTree {

    private final Map<String, Object> values = new LinkedHashMap<>();

    /** An empty tree. */
    public static DataTree create() {
        return new DataTree();
    }

    /** The integer under {@code key}, or {@code fallback} if there is none. */
    public int getInt(String key, int fallback) {
        return values.get(key) instanceof Integer value ? value : fallback;
    }

    /** Stores an integer under {@code key}; returns this tree. */
    public DataTree putInt(String key, int value) {
        return put(key, value);
    }

    /** The long under {@code key}, or {@code fallback} if there is none. */
    public long getLong(String key, long fallback) {
        return values.get(key) instanceof Long value ? value : fallback;
    }

    /** Stores a long under {@code key}; returns this tree. */
    public DataTree putLong(String key, long value) {
        return put(key, value);
    }

    /** The double under {@code key}, or {@code fallback} if there is none. */
    public double getDouble(String key, double fallback) {
        return values.get(key) instanceof Double value ? value : fallback;
    }

    /** Stores a double under {@code key}; returns this tree. */
    public DataTree putDouble(String key, double value) {
        return put(key, value);
    }

    /** The boolean under {@code key}, or {@code fallback} if there is none. */
    public boolean getBoolean(String key, boolean fallback) {
        return values.get(key) instanceof Boolean value ? value : fallback;
    }

    /** Stores a boolean under {@code key}; returns this tree. */
    public DataTree putBoolean(String key, boolean value) {
        return put(key, value);
    }

    /** The string under {@code key}, or {@code fallback} if there is none. */
    public String getString(String key, String fallback) {
        return values.get(key) instanceof String value ? value : fallback;
    }

    /** Stores a string under {@code key}; returns this tree. */
    public DataTree putString(String key, String value) {
        return put(key, Objects.requireNonNull(value, "value"));
    }

    /** A copy of the stored bytes. */
    public Optional<byte[]> getBytes(String key) {
        return values.get(key) instanceof byte[] value ? Optional.of(value.clone()) : Optional.empty();
    }

    /** Stores a copy of {@code value} under {@code key}; returns this tree. */
    public DataTree putBytes(String key, byte[] value) {
        return put(key, value.clone());
    }

    /** The child tree under {@code key}, creating it (and replacing a non-tree value) if needed. */
    public DataTree child(String key) {
        if (values.get(key) instanceof DataTree child) {
            return child;
        }
        DataTree child = new DataTree();
        put(key, child);
        return child;
    }

    /** The child tree under {@code key}, without creating it. */
    public Optional<DataTree> findChild(String key) {
        return values.get(key) instanceof DataTree child ? Optional.of(child) : Optional.empty();
    }

    /** Whether any value is stored under {@code key}. */
    public boolean has(String key) {
        return values.containsKey(key);
    }

    /** Removes the value under {@code key}, if any. */
    public void remove(String key) {
        values.remove(key);
    }

    /** Keys in insertion order. */
    public Set<String> keys() {
        return Collections.unmodifiableSet(values.keySet());
    }

    /**
     * The raw value under {@code key}: {@link Integer}, {@link Long}, {@link Double}, {@link Boolean},
     * {@link String}, {@code byte[]} (shared, do not modify), or {@link DataTree}; empty when absent.
     */
    public Optional<Object> raw(String key) {
        return Optional.ofNullable(values.get(key));
    }

    /** Removes every entry. */
    public void clear() {
        values.clear();
    }

    /** A deep copy. */
    public DataTree copy() {
        DataTree copy = new DataTree();
        values.forEach((key, value) -> copy.values.put(
                key,
                switch (value) {
                    case DataTree tree -> tree.copy();
                    case byte[] bytes -> bytes.clone();
                    default -> value;
                }));
        return copy;
    }

    private DataTree put(String key, Object value) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("Data keys must be non-empty");
        }
        values.put(key, value);
        return this;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof DataTree tree) || !tree.values.keySet().equals(values.keySet())) {
            return false;
        }
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            Object mine = entry.getValue();
            Object theirs = tree.values.get(entry.getKey());
            boolean same =
                    mine instanceof byte[] a && theirs instanceof byte[] b ? Arrays.equals(a, b) : mine.equals(theirs);
            if (!same) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        return values.keySet().hashCode();
    }

    @Override
    public String toString() {
        return values.toString();
    }
}
