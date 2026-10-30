package dev.plexusmodloader.compat.fabric.mixin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.plexusmodloader.api.runtime.GameSide;
import java.util.ArrayList;
import java.util.List;

/**
 * One entry of {@code mixins} in {@code fabric.mod.json}: a Mixin configuration file in the mod jar, for both sides
 * or one ({@code {"config": "x.mixins.json", "environment": "client"}}), as Fabric Loader reads it.
 *
 * @param config path of the configuration inside the mod jar
 * @param environment {@code *}, {@code client} or {@code server}
 */
public record MixinConfigEntry(String config, String environment) {

    /** The entries of a {@code mixins} value and the entries that could not be read. */
    public record Parsed(List<MixinConfigEntry> entries, List<String> problems) {

        public Parsed {
            entries = List.copyOf(entries);
            problems = List.copyOf(problems);
        }
    }

    /** Whether this configuration applies to {@code side}, as Fabric Loader decides it. */
    public boolean appliesTo(GameSide side) {
        return switch (environment) {
            case "client" -> side == GameSide.CLIENT;
            case "server" -> side == GameSide.DEDICATED_SERVER;
            default -> true;
        };
    }

    /**
     * Reads a {@code mixins} value: an array of configuration paths or {@code {config, environment}} objects.
     * {@code null} means the mod has none.
     */
    public static Parsed parse(JsonElement mixins) {
        List<MixinConfigEntry> entries = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        if (mixins == null || mixins.isJsonNull()) {
            return new Parsed(entries, problems);
        }
        if (!(mixins instanceof JsonArray array)) {
            problems.add("\"mixins\" is not an array: " + mixins);
            return new Parsed(entries, problems);
        }
        for (JsonElement element : array) {
            if (isString(element)) {
                entries.add(new MixinConfigEntry(element.getAsString(), "*"));
            } else if (element instanceof JsonObject object && isString(object.get("config"))) {
                String environment = isString(object.get("environment"))
                        ? object.get("environment").getAsString()
                        : "*";
                if (!environment.equals("*") && !environment.equals("client") && !environment.equals("server")) {
                    problems.add("\"mixins\" entry " + element + " has environment '" + environment
                            + "'; Fabric Loader accepts *, client or server");
                    continue;
                }
                entries.add(new MixinConfigEntry(object.get("config").getAsString(), environment));
            } else {
                problems.add("\"mixins\" entry " + element + " is neither a path nor an object with a \"config\" path");
            }
        }
        return new Parsed(entries, problems);
    }

    /**
     * Reads a Quilt mod's top-level {@code mixin} value as Quilt Loader does ({@code V1ModMetadataReader.readMixins}): a
     * configuration path, a {@code {config, environment}} object ({@code environment} {@code client}, {@code
     * dedicated_server} or {@code *}), or an array of either (ADR-0027). {@code dedicated_server} becomes {@code server}.
     * {@code null} means the mod has none. Quilt Loader refuses a mod with any problem listed.
     */
    public static Parsed parseQuilt(JsonElement mixin) {
        List<MixinConfigEntry> entries = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        if (mixin == null) {
            return new Parsed(entries, problems);
        }
        if (mixin instanceof JsonArray array) {
            for (JsonElement element : array) {
                if (isString(element) || element instanceof JsonObject) {
                    readQuilt(element, entries, problems);
                } else {
                    problems.add("Entry inside mixin must be a string or object: " + element);
                }
            }
        } else if (isString(mixin) || mixin instanceof JsonObject) {
            readQuilt(mixin, entries, problems);
        } else {
            problems.add("mixin value must be a string, a mixin entry, or a mixed array of either: " + mixin);
        }
        return new Parsed(entries, problems);
    }

    private static void readQuilt(JsonElement element, List<MixinConfigEntry> entries, List<String> problems) {
        if (isString(element)) {
            entries.add(new MixinConfigEntry(element.getAsString(), "*"));
            return;
        }
        JsonObject object = (JsonObject) element;
        JsonElement config = object.get("config");
        if (config == null) {
            problems.add("Mixin entry inside must have a config value: " + object);
            return;
        }
        if (!isString(config)) {
            problems.add("Mixin entry config must be a string: " + object);
            return;
        }
        JsonElement environment = object.get("environment");
        if (environment == null) {
            entries.add(new MixinConfigEntry(config.getAsString(), "*"));
            return;
        }
        if (!isString(environment)) {
            problems.add("Mixin entry environment must be a string: " + object);
            return;
        }
        switch (environment.getAsString()) {
            case "client" -> entries.add(new MixinConfigEntry(config.getAsString(), "client"));
            case "dedicated_server" -> entries.add(new MixinConfigEntry(config.getAsString(), "server"));
            case "*" -> entries.add(new MixinConfigEntry(config.getAsString(), "*"));
            default ->
                problems.add("Mixin entry environment must be one of 'client', 'dedicated_server', or '*': " + object);
        }
    }

    private static boolean isString(JsonElement element) {
        return element != null
                && element.isJsonPrimitive()
                && element.getAsJsonPrimitive().isString();
    }
}
