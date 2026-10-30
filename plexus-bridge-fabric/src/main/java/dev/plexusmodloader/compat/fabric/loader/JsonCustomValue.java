package dev.plexusmodloader.compat.fabric.loader;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.metadata.CustomValue;

/**
 * A {@code custom} value of {@code fabric.mod.json} as Fabric Loader's {@link CustomValue}. As in Fabric, asking
 * for the wrong type throws {@link ClassCastException}.
 */
class JsonCustomValue implements CustomValue {

    private final JsonElement json;

    JsonCustomValue(JsonElement json) {
        this.json = json == null ? com.google.gson.JsonNull.INSTANCE : json;
    }

    static CustomValue of(JsonElement json) {
        if (json != null && json.isJsonObject()) {
            return new ObjectValue(json.getAsJsonObject());
        }
        if (json != null && json.isJsonArray()) {
            return new ArrayValue(json.getAsJsonArray());
        }
        return new JsonCustomValue(json);
    }

    @Override
    public CvType getType() {
        if (json.isJsonObject()) {
            return CvType.OBJECT;
        }
        if (json.isJsonArray()) {
            return CvType.ARRAY;
        }
        if (json.isJsonNull()) {
            return CvType.NULL;
        }
        if (json.getAsJsonPrimitive().isBoolean()) {
            return CvType.BOOLEAN;
        }
        return json.getAsJsonPrimitive().isNumber() ? CvType.NUMBER : CvType.STRING;
    }

    @Override
    public CvObject getAsObject() {
        throw new ClassCastException("can't convert " + getType() + " to OBJECT");
    }

    @Override
    public CvArray getAsArray() {
        throw new ClassCastException("can't convert " + getType() + " to ARRAY");
    }

    @Override
    public String getAsString() {
        expect(CvType.STRING);
        return json.getAsString();
    }

    @Override
    public Number getAsNumber() {
        expect(CvType.NUMBER);
        return json.getAsNumber();
    }

    @Override
    public boolean getAsBoolean() {
        expect(CvType.BOOLEAN);
        return json.getAsBoolean();
    }

    private void expect(CvType type) {
        if (getType() != type) {
            throw new ClassCastException("can't convert " + getType() + " to " + type);
        }
    }

    private static final class ObjectValue extends JsonCustomValue implements CvObject {

        private final Map<String, CustomValue> entries = new LinkedHashMap<>();

        ObjectValue(JsonObject json) {
            super(json);
            json.entrySet().forEach(entry -> entries.put(entry.getKey(), of(entry.getValue())));
        }

        @Override
        public CvObject getAsObject() {
            return this;
        }

        @Override
        public int size() {
            return entries.size();
        }

        @Override
        public boolean containsKey(String key) {
            return entries.containsKey(key);
        }

        @Override
        public CustomValue get(String key) {
            return entries.get(key);
        }

        @Override
        public Iterator<Map.Entry<String, CustomValue>> iterator() {
            List<Map.Entry<String, CustomValue>> copy = new ArrayList<>();
            entries.forEach((key, value) -> copy.add(new AbstractMap.SimpleImmutableEntry<>(key, value)));
            return copy.iterator();
        }
    }

    private static final class ArrayValue extends JsonCustomValue implements CvArray {

        private final List<CustomValue> elements = new ArrayList<>();

        ArrayValue(JsonArray json) {
            super(json);
            json.forEach(element -> elements.add(of(element)));
        }

        @Override
        public CvArray getAsArray() {
            return this;
        }

        @Override
        public int size() {
            return elements.size();
        }

        @Override
        public CustomValue get(int index) {
            return elements.get(index);
        }

        @Override
        public Iterator<CustomValue> iterator() {
            return List.copyOf(elements).iterator();
        }
    }
}
