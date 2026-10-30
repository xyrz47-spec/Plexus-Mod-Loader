package dev.polymodloader.devtools.gradle.mod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Writes the standard resource files of simple blocks and items, the same JSON a modder would write by
 * hand. Mod developers use it through {@code polyResources} in the build script (docs site: {@code
 * getting-started/mod-dev-tools}).
 *
 * <ul>
 *   <li>item: {@code items/<name>.json} and a flat {@code models/item/<name>.json}
 *   <li>block: {@code blockstates/<name>.json}, a {@code cube_all} block model, the block item's {@code
 *       items/<name>.json}, and a loot table that drops the block
 *   <li>both: the English name in {@code lang/en_us.json}; blocks with a tool join {@code
 *       minecraft:mineable/<tool>}
 * </ul>
 *
 * <p>A hand-written file always wins: a path that exists in the mod's own resources is not generated.
 * The two files that collect entries, {@code lang/en_us.json} and the mineable tags, are merged instead:
 * the generated copy holds every hand-written entry plus the missing ones, and the hand-written value of
 * a key is kept. A merged copy replaces the hand-written file in the jar.
 */
public final class ModResourceGenerator {

    private static final Gson GSON =
            new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private ModResourceGenerator() {}

    /**
     * The files to generate, as resource path ({@code assets/ruby_mod/items/ruby.json}) to file text.
     *
     * @param modId the mod's ID from {@code poly.mod.json}; it is the namespace of every generated file
     * @param definitions the declared blocks and items
     * @param handWritten the mod's own resource directory ({@code src/main/resources}); it need not exist
     * @throws IllegalArgumentException with {@code PLEXUS-RESOURCE-009} if a name is declared twice, or
     *     {@code PLEXUS-RESOURCE-005} if a hand-written file that has to be merged is not valid JSON
     */
    public static Map<String, String> generate(
            String modId, List<ContentResourceDefinition> definitions, Path handWritten) {
        Map<String, String> files = new LinkedHashMap<>();
        Set<String> names = new HashSet<>();
        Map<String, String> englishNames = new LinkedHashMap<>();
        Map<String, List<String>> toolTags = new LinkedHashMap<>();
        for (ContentResourceDefinition definition : definitions) {
            String name = definition.name();
            if (!names.add(name)) {
                throw new IllegalArgumentException("PLEXUS-RESOURCE-009 " + name
                        + " is declared twice in polyResources; a block already has an item of the same name");
            }
            String id = modId + ":" + name;
            String assets = "assets/" + modId + "/";
            String texture = definition.textureId(modId);
            if (definition.kind() == ContentResourceDefinition.Kind.ITEM) {
                put(files, handWritten, assets + "items/" + name + ".json", itemDefinition(modId + ":item/" + name));
                put(files, handWritten, assets + "models/item/" + name + ".json", flatItemModel(texture));
                englishNames.put("item." + modId + "." + name, definition.englishName());
            } else {
                String model = modId + ":block/" + name;
                put(files, handWritten, assets + "blockstates/" + name + ".json", singleVariant(model));
                put(files, handWritten, assets + "models/block/" + name + ".json", cubeAllModel(texture));
                put(files, handWritten, assets + "items/" + name + ".json", itemDefinition(model));
                if (definition.dropsSelf()) {
                    put(
                            files,
                            handWritten,
                            "data/" + modId + "/loot_table/blocks/" + name + ".json",
                            selfDrop(id, modId + ":blocks/" + name));
                }
                englishNames.put("block." + modId + "." + name, definition.englishName());
                if (definition.minedWith() != null) {
                    toolTags.computeIfAbsent(definition.minedWith(), tool -> new ArrayList<>())
                            .add(id);
                }
            }
        }
        if (!englishNames.isEmpty()) {
            mergeLang(files, handWritten, "assets/" + modId + "/lang/en_us.json", englishNames);
        }
        for (Map.Entry<String, List<String>> tag : toolTags.entrySet()) {
            mergeTag(files, handWritten, "data/minecraft/tags/block/mineable/" + tag.getKey() + ".json", tag.getValue());
        }
        return files;
    }

    private static void put(Map<String, String> files, Path handWritten, String path, JsonElement json) {
        if (!Files.exists(handWritten.resolve(path))) {
            files.put(path, text(json));
        }
    }

    private static JsonObject itemDefinition(String model) {
        JsonObject inner = new JsonObject();
        inner.addProperty("type", "minecraft:model");
        inner.addProperty("model", model);
        JsonObject json = new JsonObject();
        json.add("model", inner);
        return json;
    }

    private static JsonObject flatItemModel(String texture) {
        JsonObject textures = new JsonObject();
        textures.addProperty("layer0", texture);
        JsonObject json = new JsonObject();
        json.addProperty("parent", "minecraft:item/generated");
        json.add("textures", textures);
        return json;
    }

    private static JsonObject cubeAllModel(String texture) {
        JsonObject textures = new JsonObject();
        textures.addProperty("all", texture);
        JsonObject json = new JsonObject();
        json.addProperty("parent", "minecraft:block/cube_all");
        json.add("textures", textures);
        return json;
    }

    private static JsonObject singleVariant(String model) {
        JsonObject variant = new JsonObject();
        variant.addProperty("model", model);
        JsonObject variants = new JsonObject();
        variants.add("", variant);
        JsonObject json = new JsonObject();
        json.add("variants", variants);
        return json;
    }

    /** The loot table vanilla uses for a block that drops itself. */
    private static JsonObject selfDrop(String item, String randomSequence) {
        JsonObject condition = new JsonObject();
        condition.addProperty("condition", "minecraft:survives_explosion");
        JsonArray conditions = new JsonArray();
        conditions.add(condition);
        JsonObject entry = new JsonObject();
        entry.addProperty("type", "minecraft:item");
        entry.addProperty("name", item);
        JsonArray entries = new JsonArray();
        entries.add(entry);
        JsonObject pool = new JsonObject();
        pool.add("conditions", conditions);
        pool.add("entries", entries);
        pool.addProperty("rolls", 1.0);
        JsonArray pools = new JsonArray();
        pools.add(pool);
        JsonObject json = new JsonObject();
        json.addProperty("type", "minecraft:block");
        json.add("pools", pools);
        json.addProperty("random_sequence", randomSequence);
        return json;
    }

    private static void mergeLang(
            Map<String, String> files, Path handWritten, String path, Map<String, String> englishNames) {
        JsonObject lang = readObject(handWritten, path);
        boolean added = false;
        for (Map.Entry<String, String> name : englishNames.entrySet()) {
            if (!lang.has(name.getKey())) {
                lang.addProperty(name.getKey(), name.getValue());
                added = true;
            }
        }
        if (added) {
            files.put(path, text(lang));
        }
    }

    private static void mergeTag(Map<String, String> files, Path handWritten, String path, List<String> ids) {
        JsonObject tag = readObject(handWritten, path);
        if (!tag.has("replace")) {
            // false adds to the tag other packs and the game define instead of replacing it.
            tag.addProperty("replace", false);
        }
        JsonArray values = tag.has("values") && tag.get("values").isJsonArray()
                ? tag.getAsJsonArray("values")
                : new JsonArray();
        boolean added = false;
        for (String id : ids) {
            if (!values.contains(new JsonPrimitive(id))) {
                values.add(id);
                added = true;
            }
        }
        tag.add("values", values);
        if (added) {
            files.put(path, text(tag));
        }
    }

    /** The hand-written JSON object at {@code path}, or an empty object if there is none. */
    private static JsonObject readObject(Path handWritten, String path) {
        Path file = handWritten.resolve(path);
        if (!Files.exists(file)) {
            return new JsonObject();
        }
        try {
            JsonElement json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            if (!json.isJsonObject()) {
                throw new IllegalArgumentException("PLEXUS-RESOURCE-005 " + path + " must be a JSON object");
            }
            return json.getAsJsonObject();
        } catch (JsonParseException e) {
            throw new IllegalArgumentException("PLEXUS-RESOURCE-005 " + path + " is not valid JSON: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String text(JsonElement json) {
        return GSON.toJson(json) + "\n";
    }
}
