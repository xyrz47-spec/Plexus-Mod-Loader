package dev.polymodloader.devtools.gradle.mod;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Finds mistakes in a mod's {@code assets/} and {@code data/} files that the game would only show as a
 * missing (purple and black) texture or a line in the log, without starting the game.
 *
 * <ul>
 *   <li>{@code POLY-RESOURCE-004}: a path Minecraft does not load (uppercase letters, spaces)
 *   <li>{@code POLY-RESOURCE-005}: a JSON file that does not parse
 *   <li>{@code POLY-RESOURCE-006}: an item definition, block state, or model parent naming a model the mod
 *       does not have
 *   <li>{@code POLY-RESOURCE-007}: a model naming a texture the mod does not have
 *   <li>{@code POLY-RESOURCE-008}: a language file that is not an object of text values
 * </ul>
 *
 * <p>References are only checked inside the namespaces this mod has an {@code assets/<namespace>} folder
 * for, other than {@code minecraft}: the game's own files and other mods' are not on hand. A reference
 * without a namespace means {@code minecraft:}; when the mod has that path in its own namespace instead,
 * the missing namespace is reported, because that is almost always the mistake.
 */
public final class ModResourceChecker {

    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern PATH = Pattern.compile("[a-z0-9_.\\-/]+");
    private static final Gson GSON = new Gson();

    private ModResourceChecker() {}

    /** The problems in the resource directory {@code root} (a jar's root layout), sorted by path. */
    public static List<ModResourceProblem> check(Path root) {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        List<ModResourceProblem> problems = new ArrayList<>();
        Map<String, JsonElement> json = new TreeMap<>();
        Set<String> files = new TreeSet<>();
        for (Path file : regularFiles(root)) {
            String path = root.relativize(file).toString().replace('\\', '/');
            String[] parts = path.split("/", 3);
            if (parts.length < 3 || !(parts[0].equals("assets") || parts[0].equals("data"))) {
                continue;
            }
            if (!NAMESPACE.matcher(parts[1]).matches() || !PATH.matcher(parts[2]).matches()) {
                problems.add(new ModResourceProblem(
                        "POLY-RESOURCE-004",
                        path,
                        "Minecraft only loads resource paths made of lowercase a-z, 0-9, _ . - and /, and skips"
                                + " this file; rename it to " + suggestedPath(path)));
                continue;
            }
            files.add(path);
            if (path.endsWith(".json") || path.endsWith(".mcmeta")) {
                try {
                    json.put(path, parse(Files.readString(file, StandardCharsets.UTF_8)));
                } catch (JsonParseException | IllegalStateException e) {
                    problems.add(new ModResourceProblem("POLY-RESOURCE-005", path, readableJsonError(e)));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }
        References references = new References(files);
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            String path = entry.getKey();
            String[] parts = path.split("/", 4);
            if (!parts[0].equals("assets") || parts.length < 4 || !references.ownNamespaces.contains(parts[1])) {
                continue;
            }
            switch (parts[2]) {
                case "items" -> itemModels(entry.getValue(), path, references, problems);
                case "blockstates" -> blockStateModels(entry.getValue(), path, references, problems);
                case "models" -> modelReferences(entry.getValue(), path, references, problems);
                case "lang" -> langValues(entry.getValue(), path, problems);
                default -> {}
            }
        }
        problems.sort(Comparator.comparing(ModResourceProblem::path).thenComparing(ModResourceProblem::code));
        return problems;
    }

    /** Parses like the game does: strict JSON, one value, nothing after it. */
    static JsonElement parse(String text) {
        JsonReader reader = new JsonReader(new StringReader(text));
        reader.setStrictness(Strictness.LEGACY_STRICT);
        try {
            JsonElement element = GSON.getAdapter(JsonElement.class).read(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new JsonParseException("more content after the JSON value at " + reader.getPath());
            }
            return element;
        } catch (IOException e) {
            // A string has no I/O failures: these are Gson's syntax errors (MalformedJsonException, EOFException).
            throw new JsonParseException(e.getMessage(), e);
        }
    }

    private static String readableJsonError(RuntimeException e) {
        String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        int see = message.indexOf("\nSee ");
        if (see >= 0) {
            message = message.substring(0, see);
        }
        message = message.replaceFirst("Use JsonReader\\.setStrictness\\(Strictness\\.LENIENT\\) to accept malformed JSON ", "");
        return "not valid JSON: " + message.strip();
    }

    /** Item definitions: {@code "model"} of every {@code minecraft:model} entry and {@code "base"} of special ones. */
    private static void itemModels(
            JsonElement json, String path, References references, List<ModResourceProblem> problems) {
        forEachObject(json, object -> {
            String type = string(object, "type");
            if (type == null) {
                return;
            }
            String bare = type.startsWith("minecraft:") ? type.substring("minecraft:".length()) : type;
            if (bare.equals("model") && string(object, "model") != null) {
                references.model(string(object, "model"), path, problems);
            } else if (bare.equals("special") && string(object, "base") != null) {
                references.model(string(object, "base"), path, problems);
            }
        });
    }

    /** Block states: every {@code "model"}, in variants and in multipart cases. */
    private static void blockStateModels(
            JsonElement json, String path, References references, List<ModResourceProblem> problems) {
        forEachObject(json, object -> {
            String model = string(object, "model");
            if (model != null) {
                references.model(model, path, problems);
            }
        });
    }

    /** Models: the parent and every texture that is not a {@code #variable}. */
    private static void modelReferences(
            JsonElement json, String path, References references, List<ModResourceProblem> problems) {
        if (!json.isJsonObject()) {
            return;
        }
        JsonObject model = json.getAsJsonObject();
        String parent = string(model, "parent");
        if (parent != null && !parent.startsWith("builtin/") && !parent.startsWith("minecraft:builtin/")) {
            references.model(parent, path, problems);
        }
        if (model.has("textures") && model.get("textures").isJsonObject()) {
            for (Map.Entry<String, JsonElement> texture :
                    model.getAsJsonObject("textures").entrySet()) {
                if (texture.getValue().isJsonPrimitive()
                        && texture.getValue().getAsJsonPrimitive().isString()
                        && !texture.getValue().getAsString().startsWith("#")) {
                    references.texture(texture.getValue().getAsString(), path, problems);
                }
            }
        }
    }

    private static void langValues(JsonElement json, String path, List<ModResourceProblem> problems) {
        if (!json.isJsonObject()) {
            problems.add(new ModResourceProblem(
                    "POLY-RESOURCE-008", path, "a language file must be one JSON object of \"key\": \"text\""));
            return;
        }
        for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject().entrySet()) {
            JsonElement value = entry.getValue();
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                problems.add(new ModResourceProblem(
                        "POLY-RESOURCE-008",
                        path,
                        "the value of \"" + entry.getKey()
                                + "\" is not text; the game rejects the whole file. Write it in quotes"));
            }
        }
    }

    private static void forEachObject(JsonElement json, java.util.function.Consumer<JsonObject> action) {
        if (json.isJsonObject()) {
            action.accept(json.getAsJsonObject());
            for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject().entrySet()) {
                forEachObject(entry.getValue(), action);
            }
        } else if (json.isJsonArray()) {
            for (JsonElement element : json.getAsJsonArray()) {
                forEachObject(element, action);
            }
        }
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString()
                : null;
    }

    private static String suggestedPath(String path) {
        return path.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.\\-/]", "_");
    }

    private static List<Path> regularFiles(Path root) {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Resolves model and texture IDs against the files of this mod. */
    private static final class References {

        private final Set<String> files;
        private final Set<String> ownNamespaces = new TreeSet<>();

        References(Set<String> files) {
            this.files = files;
            for (String file : files) {
                String[] parts = file.split("/", 3);
                if (parts[0].equals("assets") && !parts[1].equals("minecraft")) {
                    ownNamespaces.add(parts[1]);
                }
            }
        }

        void model(String id, String from, List<ModResourceProblem> problems) {
            resolve(id, "models/", ".json", "POLY-RESOURCE-006", "model", from, problems);
        }

        void texture(String id, String from, List<ModResourceProblem> problems) {
            resolve(id, "textures/", ".png", "POLY-RESOURCE-007", "texture", from, problems);
        }

        private void resolve(
                String id,
                String folder,
                String extension,
                String code,
                String kind,
                String from,
                List<ModResourceProblem> problems) {
            int colon = id.indexOf(':');
            String namespace = colon < 0 ? "minecraft" : id.substring(0, colon);
            String path = id.substring(colon + 1);
            if (namespace.equals("minecraft")) {
                if (colon >= 0 || files.contains("assets/minecraft/" + folder + path + extension)) {
                    return;
                }
                for (String own : ownNamespaces) {
                    if (files.contains("assets/" + own + "/" + folder + path + extension)) {
                        problems.add(new ModResourceProblem(
                                code,
                                from,
                                kind + " \"" + id + "\" has no namespace, so the game looks for minecraft:" + path
                                        + "; write \"" + own + ":" + path + "\" for this mod's file"));
                        return;
                    }
                }
                return;
            }
            if (!ownNamespaces.contains(namespace)) {
                return;
            }
            String expected = "assets/" + namespace + "/" + folder + path + extension;
            if (!files.contains(expected)) {
                problems.add(new ModResourceProblem(
                        code, from, kind + " \"" + id + "\" does not exist; the game looks for " + expected));
            }
        }
    }
}
