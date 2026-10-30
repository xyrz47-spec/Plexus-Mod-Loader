package dev.polymodloader.compat.quilt;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.quiltmc.loader.api.LoaderValue;

/**
 * A JSON value of a mod's metadata as Quilt Loader's {@link LoaderValue} ({@code ModMetadata.values()}), read from gson
 * (ADR-0027 decision 4). Like Quilt Loader's own values, objects and arrays are read-only {@link LObject} maps and
 * {@link LArray} lists, asking for the wrong type throws {@link ClassCastException}, and each value knows where in the
 * file it is ({@link #location()}, such as {@code quilt.mod.json:/my_mod:settings/tags/1}).
 *
 * <p>Thread: immutable.
 */
public abstract sealed class QuiltLoaderValue implements LoaderValue
        permits QuiltLoaderValue.Scalar, QuiltLoaderValue.ObjectValue, QuiltLoaderValue.ArrayValue {

    private final String location;

    private QuiltLoaderValue(String location) {
        this.location = location;
    }

    /**
     * The value of {@code json}.
     *
     * @param location where it is, such as {@code quilt.mod.json:/key}
     */
    public static LoaderValue of(JsonElement json, String location) {
        if (json instanceof JsonObject object) {
            return new ObjectValue(object, location);
        }
        if (json instanceof JsonArray array) {
            return new ArrayValue(array, location);
        }
        return new Scalar(json == null ? JsonNull.INSTANCE : json, location);
    }

    /** The values of a JSON object's entries, in file order, located under {@code location}. */
    public static Map<String, LoaderValue> entries(JsonObject object, String location) {
        return new ObjectValue(object, location);
    }

    /** The JSON this value was read from. */
    abstract JsonElement json();

    @Override
    public String location() {
        return location;
    }

    @Override
    public LObject asObject() {
        throw new ClassCastException("Cannot coerce loader value to an Object: " + location);
    }

    @Override
    public LArray asArray() {
        throw new ClassCastException("Cannot coerce loader value to an Array: " + location);
    }

    @Override
    public String asString() {
        throw new ClassCastException("Cannot coerce loader value to a String: " + location);
    }

    @Override
    public Number asNumber() {
        throw new ClassCastException("Cannot coerce loader value to a Number: " + location);
    }

    @Override
    public boolean asBoolean() {
        throw new ClassCastException("Cannot coerce loader value to a Boolean: " + location);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof QuiltLoaderValue that && json().equals(that.json());
    }

    @Override
    public int hashCode() {
        return json().hashCode();
    }

    @Override
    public String toString() {
        return json().toString();
    }

    private static String child(String location, String key) {
        return location + "/" + key;
    }

    /** A text, number, boolean or {@code null}. */
    static final class Scalar extends QuiltLoaderValue {

        private final JsonElement json;

        Scalar(JsonElement json, String location) {
            super(location);
            this.json = json;
        }

        @Override
        JsonElement json() {
            return json;
        }

        @Override
        public LType type() {
            if (json.isJsonNull()) {
                return LType.NULL;
            }
            JsonPrimitive primitive = json.getAsJsonPrimitive();
            if (primitive.isBoolean()) {
                return LType.BOOLEAN;
            }
            return primitive.isNumber() ? LType.NUMBER : LType.STRING;
        }

        @Override
        public String asString() {
            return type() == LType.STRING ? json.getAsString() : super.asString();
        }

        @Override
        public Number asNumber() {
            return type() == LType.NUMBER ? json.getAsNumber() : super.asNumber();
        }

        @Override
        public boolean asBoolean() {
            return type() == LType.BOOLEAN ? json.getAsBoolean() : super.asBoolean();
        }
    }

    /** A JSON object: a read-only map of its entries in file order. */
    static final class ObjectValue extends QuiltLoaderValue implements LObject {

        private final JsonObject json;
        private final Map<String, LoaderValue> entries;

        ObjectValue(JsonObject json, String location) {
            super(location);
            this.json = json.deepCopy();
            Map<String, LoaderValue> read = new LinkedHashMap<>();
            this.json
                    .entrySet()
                    .forEach(entry -> read.put(
                            entry.getKey(), QuiltLoaderValue.of(entry.getValue(), child(location, entry.getKey()))));
            this.entries = Collections.unmodifiableMap(read);
        }

        @Override
        JsonElement json() {
            return json;
        }

        @Override
        public LType type() {
            return LType.OBJECT;
        }

        @Override
        public LObject asObject() {
            return this;
        }

        @Override
        public LoaderValue get(Object key) {
            return entries.get(key);
        }

        @Override
        public int size() {
            return entries.size();
        }

        @Override
        public boolean isEmpty() {
            return entries.isEmpty();
        }

        @Override
        public boolean containsKey(Object key) {
            return entries.containsKey(key);
        }

        @Override
        public boolean containsValue(Object value) {
            return entries.containsValue(value);
        }

        @Override
        public LoaderValue put(String key, LoaderValue value) {
            throw new UnsupportedOperationException("Loader values are read-only");
        }

        @Override
        public LoaderValue remove(Object key) {
            throw new UnsupportedOperationException("Loader values are read-only");
        }

        @Override
        public void putAll(Map<? extends String, ? extends LoaderValue> map) {
            throw new UnsupportedOperationException("Loader values are read-only");
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException("Loader values are read-only");
        }

        @Override
        public Set<String> keySet() {
            return entries.keySet();
        }

        @Override
        public java.util.Collection<LoaderValue> values() {
            return entries.values();
        }

        @Override
        public Set<Map.Entry<String, LoaderValue>> entrySet() {
            return entries.entrySet();
        }

        /** Values compare by their JSON, not as maps. */
        @Override
        public boolean equals(Object other) {
            return super.equals(other);
        }

        @Override
        public int hashCode() {
            return super.hashCode();
        }
    }

    /** A JSON array: a read-only list of its elements. */
    static final class ArrayValue extends QuiltLoaderValue implements LArray {

        private final JsonArray json;
        private final List<LoaderValue> elements;

        ArrayValue(JsonArray json, String location) {
            super(location);
            this.json = json.deepCopy();
            List<LoaderValue> read = new ArrayList<>();
            for (int i = 0; i < this.json.size(); i++) {
                read.add(QuiltLoaderValue.of(this.json.get(i), child(location, String.valueOf(i))));
            }
            this.elements = Collections.unmodifiableList(read);
        }

        @Override
        JsonElement json() {
            return json;
        }

        @Override
        public LType type() {
            return LType.ARRAY;
        }

        @Override
        public LArray asArray() {
            return this;
        }

        @Override
        public LoaderValue get(int index) {
            return elements.get(index);
        }

        @Override
        public int size() {
            return elements.size();
        }

        @Override
        public boolean isEmpty() {
            return elements.isEmpty();
        }

        @Override
        public boolean contains(Object value) {
            return elements.contains(value);
        }

        @Override
        public java.util.Iterator<LoaderValue> iterator() {
            return elements.iterator();
        }

        @Override
        public Object[] toArray() {
            return elements.toArray();
        }

        @Override
        public <T> T[] toArray(T[] array) {
            return elements.toArray(array);
        }

        @Override
        public boolean add(LoaderValue value) {
            throw new UnsupportedOperationException("Loader values are read-only");
        }

        @Override
        public boolean remove(Object value) {
            throw new UnsupportedOperationException("Loader values are read-only");
        }

        @Override
        public boolean containsAll(java.util.Collection<?> values) {
            return elements.containsAll(values);
        }

        @Override
        public boolean addAll(java.util.Collection<? extends LoaderValue> values) {
            throw new UnsupportedOperationException("Loader values are read-only");
        }

        @Override
        public boolean addAll(int index, java.util.Collection<? extends LoaderValue> values) {
            throw new UnsupportedOperationException("Loader values are read-only");
        }

        @Override
        public boolean removeAll(java.util.Collection<?> values) {
            throw new UnsupportedOperationException("Loader values are read-only");
        }

        @Override
        public boolean retainAll(java.util.Collection<?> values) {
            throw new UnsupportedOperationException("Loader values are read-only");
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException("Loader values are read-only");
        }

        @Override
        public LoaderValue set(int index, LoaderValue value) {
            throw new UnsupportedOperationException("Loader values are read-only");
        }

        @Override
        public void add(int index, LoaderValue value) {
            throw new UnsupportedOperationException("Loader values are read-only");
        }

        @Override
        public LoaderValue remove(int index) {
            throw new UnsupportedOperationException("Loader values are read-only");
        }

        @Override
        public int indexOf(Object value) {
            return elements.indexOf(value);
        }

        @Override
        public int lastIndexOf(Object value) {
            return elements.lastIndexOf(value);
        }

        @Override
        public java.util.ListIterator<LoaderValue> listIterator() {
            return elements.listIterator();
        }

        @Override
        public java.util.ListIterator<LoaderValue> listIterator(int index) {
            return elements.listIterator(index);
        }

        @Override
        public List<LoaderValue> subList(int from, int to) {
            return elements.subList(from, to);
        }

        @Override
        public boolean equals(Object other) {
            return super.equals(other);
        }

        @Override
        public int hashCode() {
            return super.hashCode();
        }
    }
}
