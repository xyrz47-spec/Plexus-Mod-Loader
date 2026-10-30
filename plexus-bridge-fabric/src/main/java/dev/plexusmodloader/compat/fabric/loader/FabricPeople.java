package dev.plexusmodloader.compat.fabric.loader;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.loader.api.metadata.ContactInformation;
import net.fabricmc.loader.api.metadata.Person;

/** Authors, contributors and contact details of {@code fabric.mod.json} as Fabric Loader's types. */
final class FabricPeople {

    private FabricPeople() {}

    /** Contact details: an object of text values; anything else gives none. */
    static ContactInformation contact(JsonElement json) {
        Map<String, String> values = new LinkedHashMap<>();
        if (json instanceof JsonObject object) {
            object.entrySet().forEach(entry -> {
                if (entry.getValue().isJsonPrimitive()) {
                    values.put(entry.getKey(), entry.getValue().getAsString());
                }
            });
        }
        return values.isEmpty() ? ContactInformation.EMPTY : new Contact(Map.copyOf(values));
    }

    /** Contact details from a map of kind to address; an empty map gives none. */
    static ContactInformation contact(Map<String, String> values) {
        return values.isEmpty()
                ? ContactInformation.EMPTY
                : new Contact(Collections.unmodifiableMap(new LinkedHashMap<>(values)));
    }

    /** A person known by name only. */
    static Person person(String name) {
        return new NamedPerson(name, ContactInformation.EMPTY);
    }

    /** A list of people, each a name or {@code {"name": ..., "contact": {...}}}. */
    static List<Person> people(JsonElement json) {
        List<Person> people = new ArrayList<>();
        if (json instanceof JsonArray array) {
            for (JsonElement element : array) {
                if (element.isJsonPrimitive()) {
                    people.add(new NamedPerson(element.getAsString(), ContactInformation.EMPTY));
                } else if (element instanceof JsonObject object && object.has("name")) {
                    people.add(new NamedPerson(object.get("name").getAsString(), contact(object.get("contact"))));
                }
            }
        }
        return List.copyOf(people);
    }

    private record Contact(Map<String, String> values) implements ContactInformation {

        @Override
        public Optional<String> get(String key) {
            return Optional.ofNullable(values.get(key));
        }

        @Override
        public Map<String, String> asMap() {
            return values;
        }
    }

    private record NamedPerson(String name, ContactInformation contact) implements Person {

        @Override
        public String getName() {
            return name;
        }

        @Override
        public ContactInformation getContact() {
            return contact;
        }
    }
}
