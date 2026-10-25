package dev.polymodloader.compat.quilt;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import dev.polymodloader.compat.bridge.CompatibilityFinding;
import dev.polymodloader.compat.fabric.mixin.MixinConfigEntry;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.quiltmc.loader.api.ModLicense;

/**
 * Reads {@code quilt.mod.json} with the checks of Quilt Loader 0.31.0-beta.4's {@code ModMetadataReader} and {@code
 * V1ModMetadataReader}, in the same order and on the same conditions (RFC 0002, ADR-0027 decision 6). Like Quilt Loader
 * it stops at the first value it refuses.
 *
 * <p>Findings:
 *
 * <ul>
 *   <li>{@code POLY-COMPAT-300} (blocking): the file is one Quilt Loader refuses; nothing else is reported
 *   <li>{@code POLY-COMPAT-301} (blocking): a loader plugin ({@code experimental_quilt_loader_plugin})
 *   <li>{@code POLY-COMPAT-302} (blocking): a language adapter ({@code language_adapters}, or an entrypoint whose
 *       {@code adapter} is not {@code default})
 *   <li>{@code POLY-COMPAT-303} (not blocking): QSL entrypoints ({@code init}, {@code client_init}, {@code
 *       server_init}), which nothing runs without QSL
 *   <li>{@code POLY-COMPAT-304} (not blocking): any other entrypoint key Poly Mod Loader does not run
 *   <li>{@code POLY-COMPAT-306} (blocking): {@code required_features} asks for the modifiable file system overlay
 *   <li>{@code POLY-COMPAT-308} (not blocking): a required QSL module ({@link QuiltDependencyReader})
 *   <li>{@code POLY-COMPAT-309} (not blocking): values Quilt Loader reads that have no effect in this launch
 * </ul>
 *
 * <p>Stateless; used while assessing a mod and, later, by the Quilt Loader API runtime. Never throws for the file's
 * contents.
 */
public final class QuiltModJsonReader {

    /** The file name of the metadata. */
    public static final String FILE_NAME = "quilt.mod.json";

    /** The JSON5 form, which Quilt Loader refuses outside a development environment. */
    public static final String JSON5_FILE_NAME = "quilt.mod.json5";

    private static final Pattern VALID_MOD_ID = Pattern.compile("^[a-z][a-z0-9-_]{1,63}$");
    private static final Pattern VALID_MAVEN_GROUP = Pattern.compile("^[a-zA-Z0-9-_.]+$");
    private static final Pattern VALID_INTERMEDIATE = Pattern.compile("^[a-zA-Z0-9-_.]+:[a-zA-Z0-9-_.]+$");

    /** The keys of {@code quilt_loader} Quilt Loader knows (its {@code QLKeys}). */
    private static final Set<String> QUILT_LOADER_KEYS = Set.of(
            "id",
            "group",
            "version",
            "entrypoints",
            "jars",
            "language_adapters",
            "depends",
            "breaks",
            "repositories",
            "load_type",
            "provides",
            "intermediate_mappings",
            "metadata",
            "required_features");

    /** Entrypoint keys Poly Mod Loader runs, as Quilt Loader itself does (ADR-0027 background 7). */
    static final Set<String> RUN_KEYS = Set.of("main", "client", "server", "pre_launch", "preLaunch");

    /** Entrypoint keys QSL runs; without QSL nothing does (ADR-0027 decision 5). */
    static final Set<String> QSL_KEYS = Set.of("init", "client_init", "server_init");

    /**
     * The outcome of reading one file.
     *
     * @param description the file as Quilt reads it; empty when Quilt Loader would refuse it ({@code 300})
     * @param id the mod ID, or {@code ?} when it cannot be read
     * @param version the mod version, or {@code ?}
     * @param findings what was noticed, in reading order
     */
    public record Result(
            Optional<QuiltModDescription> description, String id, String version, List<CompatibilityFinding> findings) {

        public Result {
            findings = List.copyOf(findings);
        }
    }

    private QuiltModJsonReader() {}

    /**
     * Reads {@code text}, the contents of {@code quilt.mod.json}.
     *
     * @param json5Present whether the jar also has {@code quilt.mod.json5}
     */
    public static Result read(String text, boolean json5Present) {
        List<CompatibilityFinding> findings = new ArrayList<>();
        JsonObject root = null;
        try {
            root = parse(text);
            if (json5Present) {
                throw new QuiltMetadataException(
                        "the jar has quilt.mod.json5 besides quilt.mod.json; Quilt Loader reads quilt.mod.json5 only in a"
                                + " development environment and refuses it otherwise");
            }
            QuiltModDescription description = readRoot(root, findings);
            return new Result(Optional.of(description), description.id(), description.version(), findings);
        } catch (QuiltMetadataException e) {
            // Like Quilt Loader, the first refused value is the whole verdict.
            return new Result(
                    Optional.empty(),
                    rawText(root, "id"),
                    rawText(root, "version"),
                    List.of(new CompatibilityFinding(
                            "POLY-COMPAT-300",
                            true,
                            "metadata",
                            e.getMessage() + "; Quilt Loader refuses this quilt.mod.json too")));
        } catch (RuntimeException e) {
            // A value of a form this reader did not expect; Quilt Loader refuses what it cannot read.
            return new Result(
                    Optional.empty(),
                    rawText(root, "id"),
                    rawText(root, "version"),
                    List.of(new CompatibilityFinding(
                            "POLY-COMPAT-300",
                            true,
                            "metadata",
                            "quilt.mod.json cannot be read: " + e + "; Quilt Loader refuses this quilt.mod.json too")));
        }
    }

    /** Strict JSON with one object at the root and nothing after it, as Quilt Loader's JSON reader wants. */
    private static JsonObject parse(String text) throws QuiltMetadataException {
        try (JsonReader reader = new JsonReader(new StringReader(text))) {
            reader.setStrictness(Strictness.STRICT);
            if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                throw new QuiltMetadataException("A quilt.mod.json must have an object at the root");
            }
            JsonElement parsed = JsonParser.parseReader(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new QuiltMetadataException("Encountered additional data at end of document");
            }
            return parsed.getAsJsonObject();
        } catch (IOException | JsonParseException | IllegalStateException e) {
            throw new QuiltMetadataException("quilt.mod.json is not valid JSON: " + e.getMessage(), e);
        }
    }

    private static QuiltModDescription readRoot(JsonObject root, List<CompatibilityFinding> findings)
            throws QuiltMetadataException {
        JsonElement schemaVersion = root.get("schema_version");
        if (schemaVersion == null) {
            throw new QuiltMetadataException("No schema_version field was found, this is probably not a quilt mod");
        }
        if (!(schemaVersion.isJsonPrimitive()
                && schemaVersion.getAsJsonPrimitive().isNumber())) {
            throw new QuiltMetadataException("schema_version must be a number");
        }
        int version = schemaVersion.getAsNumber().intValue();
        if (version < 0) {
            throw new QuiltMetadataException("schema_version must not be negative");
        } else if (version == 0) {
            throw new QuiltMetadataException("schema_version cannot be 0");
        } else if (version != 1) {
            throw new QuiltMetadataException(
                    "encountered unsupported schema_version " + version + ", you may need to update loader?");
        }
        JsonElement quiltLoaderValue = root.get("quilt_loader");
        if (quiltLoaderValue == null) {
            throw new QuiltMetadataException("quilt_loader field is required");
        }
        if (!(quiltLoaderValue instanceof JsonObject quiltLoader)) {
            throw new QuiltMetadataException("quilt_loader field must be an object");
        }
        Set<String> unknownKeys = new TreeSet<>(quiltLoader.keySet());
        unknownKeys.removeAll(QUILT_LOADER_KEYS);
        if (!unknownKeys.isEmpty()) {
            findings.add(new CompatibilityFinding(
                    "POLY-COMPAT-309",
                    false,
                    "quilt_loader",
                    "keys Quilt Loader does not know are ignored, as Quilt Loader ignores them outside a development"
                            + " environment: " + unknownKeys));
        }

        String id = requiredString(quiltLoader, "id");
        if (!VALID_MOD_ID.matcher(id).matches()) {
            throw new QuiltMetadataException("Invalid mod id '" + id + "': the allowed characters are a-z 0-9 _ -, it"
                    + " starts with a letter, and it has 2 to 64 characters");
        }
        String group = requiredString(quiltLoader, "group");
        if (!VALID_MAVEN_GROUP.matcher(group).matches()) {
            throw new QuiltMetadataException(
                    "Invalid mod maven group '" + group + "'; the allowed characters are a-z A-Z 0-9 - _ and .");
        }
        if (quiltLoader.get("version") == null) {
            throw new QuiltMetadataException("version is a required field");
        }
        String modVersion = requiredString(quiltLoader, "version");

        Map<String, List<QuiltModDescription.Entrypoint>> entrypoints = new LinkedHashMap<>();
        JsonElement entrypointsValue = quiltLoader.get("entrypoints");
        if (entrypointsValue != null) {
            if (!(entrypointsValue instanceof JsonObject declared)) {
                throw new QuiltMetadataException("entrypoints must be an object");
            }
            readEntrypoints(declared, entrypoints);
        }
        List<String> jars = new ArrayList<>();
        JsonElement jarsValue = quiltLoader.get("jars");
        if (jarsValue != null) {
            if (!(jarsValue instanceof JsonArray array)) {
                throw new QuiltMetadataException("jars must be an array");
            }
            readStringList(array, "jars", jars);
        }
        Map<String, String> languageAdapters = new LinkedHashMap<>();
        JsonElement adaptersValue = quiltLoader.get("language_adapters");
        if (adaptersValue != null) {
            if (!(adaptersValue instanceof JsonObject adapters)) {
                throw new QuiltMetadataException("language_adapters must be an object");
            }
            readStringMap(adapters, "language_adapters", languageAdapters);
        }
        List<QuiltDependencyReader.Read> depends = new ArrayList<>();
        JsonArray dependsValue = array(quiltLoader, "depends");
        if (dependsValue != null) {
            for (JsonElement element : dependsValue) {
                depends.add(QuiltDependencyReader.depends(element));
            }
        }
        List<QuiltDependencyReader.Read> breaks = new ArrayList<>();
        JsonArray breaksValue = array(quiltLoader, "breaks");
        if (breaksValue != null) {
            for (JsonElement element : breaksValue) {
                breaks.add(QuiltDependencyReader.breaks(element));
            }
        }
        JsonElement repositoriesValue = quiltLoader.get("repositories");
        if (repositoriesValue != null) {
            if (!(repositoriesValue instanceof JsonArray repositories)) {
                throw new QuiltMetadataException("repositories must be an array");
            }
            List<String> read = new ArrayList<>();
            readStringList(repositories, "repositories", read);
            findings.add(new CompatibilityFinding(
                    "POLY-COMPAT-309",
                    false,
                    "repositories",
                    "repositories " + read + " are read and ignored; Quilt Loader does not download from them"
                            + " either"));
        }
        String loadType = "if_required";
        JsonElement loadTypeValue = quiltLoader.get("load_type");
        if (loadTypeValue != null) {
            if (!isString(loadTypeValue)) {
                throw new QuiltMetadataException("load_type must be a string");
            }
            loadType = loadTypeValue.getAsString();
            if (!Set.of("always", "if_possible", "if_required").contains(loadType)) {
                throw new QuiltMetadataException(
                        "load_type must be either 'always', 'if_possible', or 'if_required', but got '" + loadType
                                + "'");
            }
        }
        List<QuiltModDescription.Provided> provides = new ArrayList<>();
        JsonElement providesValue = quiltLoader.get("provides");
        if (providesValue != null) {
            if (!(providesValue instanceof JsonArray array)) {
                throw new QuiltMetadataException("provides must be an array");
            }
            for (JsonElement provided : array) {
                provides.add(provided(provided, group, modVersion));
            }
        }
        String mappings = intermediateMappings(quiltLoader.get("intermediate_mappings"), findings);
        requiredFeatures(quiltLoader.get("required_features"), findings);

        String name = "";
        String description = "";
        Map<String, List<String>> contributors = new LinkedHashMap<>();
        Map<String, String> contact = new LinkedHashMap<>();
        List<ModLicense> licenses = new ArrayList<>();
        JsonElement icon = null;
        JsonElement metadataValue = quiltLoader.get("metadata");
        if (metadataValue != null) {
            if (!(metadataValue instanceof JsonObject metadata)) {
                throw new QuiltMetadataException("metadata must be an object");
            }
            name = optionalString(metadata, "name");
            description = optionalString(metadata, "description");
            JsonElement contributorsValue = metadata.get("contributors");
            if (contributorsValue != null) {
                if (!(contributorsValue instanceof JsonObject people)) {
                    throw new QuiltMetadataException("contributors must be an object");
                }
                readContributors(people, contributors);
            }
            JsonElement contactValue = metadata.get("contact");
            if (contactValue != null) {
                if (!(contactValue instanceof JsonObject contactObject)) {
                    throw new QuiltMetadataException("contact must be an object");
                }
                readStringMap(contactObject, "contact", contact);
            }
            readLicenses(metadata.get("license"), licenses);
            icon = icon(metadata.get("icon"));
        }

        MixinConfigEntry.Parsed mixins = MixinConfigEntry.parseQuilt(root.get("mixin"));
        if (!mixins.problems().isEmpty()) {
            throw new QuiltMetadataException(mixins.problems().getFirst());
        }
        String environment = environment(root.get("minecraft"));
        List<String> accessWideners = new ArrayList<>();
        JsonElement accessWidenerValue = root.get("access_widener");
        if (accessWidenerValue != null) {
            if (accessWidenerValue instanceof JsonArray array) {
                readStringList(array, "access_widener", accessWideners);
            } else if (isString(accessWidenerValue)) {
                accessWideners.add(accessWidenerValue.getAsString());
            } else {
                throw new QuiltMetadataException("access_widener must be an array of strings or a string");
            }
        }

        notes(root, entrypoints, languageAdapters, depends, findings);
        return new QuiltModDescription(
                id,
                group,
                modVersion,
                name,
                description,
                contributors,
                contact,
                licenses,
                icon,
                provides,
                entrypoints,
                usableJars(jars, findings),
                depends,
                breaks,
                loadType,
                mixins.entries(),
                accessWideners,
                environment,
                mappings,
                languageAdapters,
                root);
    }

    /** The checks that do not stop reading: plugins, adapters, entrypoint keys, QSL, top-level environment. */
    private static void notes(
            JsonObject root,
            Map<String, List<QuiltModDescription.Entrypoint>> entrypoints,
            Map<String, String> languageAdapters,
            List<QuiltDependencyReader.Read> depends,
            List<CompatibilityFinding> findings) {
        if (root.has("experimental_quilt_loader_plugin")) {
            findings.add(new CompatibilityFinding(
                    "POLY-COMPAT-301",
                    true,
                    "loader plugin",
                    "declares a Quilt Loader plugin (experimental_quilt_loader_plugin); Quilt Loader runs it inside"
                            + " the loader, and Poly Mod Loader does not provide the Quilt Loader plugin API"));
        }
        Set<String> adapters = new TreeSet<>();
        entrypoints
                .values()
                .forEach(list -> list.stream()
                        .map(QuiltModDescription.Entrypoint::adapter)
                        .filter(adapter -> !adapter.equals("default"))
                        .forEach(adapters::add));
        if (!languageAdapters.isEmpty() || !adapters.isEmpty()) {
            findings.add(new CompatibilityFinding(
                    "POLY-COMPAT-302",
                    true,
                    "language adapter",
                    (languageAdapters.isEmpty() ? "" : "declares language adapters " + languageAdapters.keySet())
                            + (!languageAdapters.isEmpty() && !adapters.isEmpty() ? " and " : "")
                            + (adapters.isEmpty() ? "" : "has entrypoints that use the language adapter(s) " + adapters)
                            + "; Quilt language adapters (such as Quilt Kotlin Libraries) have no release for"
                            + " Minecraft 26.x and Poly Mod Loader runs Quilt entrypoints with the default adapter"
                            + " only"));
        }
        List<String> qslKeys =
                entrypoints.keySet().stream().filter(QSL_KEYS::contains).toList();
        if (!qslKeys.isEmpty()) {
            boolean othersRun = entrypoints.keySet().stream().anyMatch(RUN_KEYS::contains);
            findings.add(new CompatibilityFinding(
                    "POLY-COMPAT-303",
                    false,
                    "entrypoints " + qslKeys,
                    "QSL runs these entrypoints, and QSL has no release for Minecraft 26.x, so Quilt Loader does not"
                            + " run them either; Poly Mod Loader does not run them, and applies the mod's mixins and"
                            + " resources"
                            + (othersRun
                                    ? ""
                                    : ". This mod's initialization code does not run (under Quilt Loader it does not"
                                            + " without QSL either)")));
        }
        for (String key : entrypoints.keySet()) {
            if (!RUN_KEYS.contains(key) && !QSL_KEYS.contains(key)) {
                findings.add(new CompatibilityFinding(
                        "POLY-COMPAT-304",
                        false,
                        "entrypoint '" + key + "'",
                        "not run by Poly Mod Loader; other mods can get it through QuiltLoader.getEntrypoints and"
                                + " FabricLoader.getEntrypoints"));
            }
        }
        depends.forEach(read -> findings.addAll(read.findings()));
        if (root.has("environment")) {
            findings.add(new CompatibilityFinding(
                    "POLY-COMPAT-309",
                    false,
                    "environment",
                    "a top-level \"environment\" is not a quilt.mod.json field and is ignored, as by Quilt Loader;"
                            + " the side is minecraft.environment"));
        }
    }

    /** The {@code jars} entries Quilt Loader opens: one with a {@code ..} part is skipped. */
    private static List<String> usableJars(List<String> jars, List<CompatibilityFinding> findings) {
        List<String> usable = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (String jar : jars) {
            if (List.of(jar.split("/")).contains("..")) {
                skipped.add(jar);
            } else {
                usable.add(jar);
            }
        }
        if (!skipped.isEmpty()) {
            findings.add(new CompatibilityFinding(
                    "POLY-COMPAT-309",
                    false,
                    "jars",
                    "entries that leave the jar ('..') are skipped, as Quilt Loader skips them: " + skipped));
        }
        return usable;
    }

    private static void readEntrypoints(
            JsonObject declared, Map<String, List<QuiltModDescription.Entrypoint>> entrypoints)
            throws QuiltMetadataException {
        for (Map.Entry<String, JsonElement> entry : declared.entrySet()) {
            List<QuiltModDescription.Entrypoint> list =
                    entrypoints.computeIfAbsent(entry.getKey(), key -> new ArrayList<>());
            if (entry.getValue() instanceof JsonArray array) {
                for (JsonElement element : array) {
                    list.add(entrypoint(element));
                }
            } else {
                list.add(entrypoint(entry.getValue()));
            }
        }
    }

    private static QuiltModDescription.Entrypoint entrypoint(JsonElement element) throws QuiltMetadataException {
        if (isString(element)) {
            return new QuiltModDescription.Entrypoint("default", element.getAsString());
        }
        if (element instanceof JsonObject object) {
            JsonElement adapter = object.get("adapter");
            JsonElement value = object.get("value");
            if (adapter == null) {
                throw new QuiltMetadataException(
                        "entry inside \"entrypoints\" in object form is missing the \"adapter\" field: " + object);
            }
            if (value == null) {
                throw new QuiltMetadataException(
                        "entry inside \"entrypoints\" in object form is missing the \"value\" field: " + object);
            }
            if (!isString(adapter)) {
                throw new QuiltMetadataException("adapter field inside \"entrypoints\" must be a string: " + object);
            }
            if (!isString(value)) {
                throw new QuiltMetadataException("value field inside \"entrypoints\" must be a string: " + object);
            }
            return new QuiltModDescription.Entrypoint(adapter.getAsString(), value.getAsString());
        }
        throw new QuiltMetadataException("value inside \"entrypoints\" must be a string or object: " + element);
    }

    private static QuiltModDescription.Provided provided(JsonElement provided, String group, String modVersion)
            throws QuiltMetadataException {
        String providedId;
        String version = modVersion;
        if (isString(provided)) {
            providedId = provided.getAsString();
        } else if (provided instanceof JsonObject object) {
            providedId = requiredString(object, "id");
            if (object.has("version")) {
                version = requiredString(object, "version");
            }
        } else {
            throw new QuiltMetadataException(
                    "provides must be an array containing only objects and/or strings: " + provided);
        }
        String providedGroup = group;
        int colon = providedId.indexOf(':');
        if (colon > 0) {
            providedGroup = providedId.substring(0, colon);
            providedId = providedId.substring(colon + 1);
        }
        return new QuiltModDescription.Provided(providedGroup, providedId, version);
    }

    /**
     * Quilt Loader supports {@code net.fabricmc:intermediary} only here: hashed mappings (the default) not at all, and
     * {@code org.quiltmc.experimental:mojang} needs a {@code mojang} namespace that 26.x does not have.
     */
    private static String intermediateMappings(JsonElement value, List<CompatibilityFinding> findings)
            throws QuiltMetadataException {
        String mappings = "org.quiltmc:hashed";
        if (value != null) {
            if (!isString(value)) {
                throw new QuiltMetadataException("intermediate_mappings must be a string");
            }
            mappings = value.getAsString();
            if (!VALID_INTERMEDIATE.matcher(mappings).matches()) {
                throw new QuiltMetadataException(
                        "intermediate_mappings must be a valid maven coordinate: '" + mappings + "'");
            }
            if (!List.of("org.quiltmc:hashed", "net.fabricmc:intermediary", "org.quiltmc.experimental:mojang")
                    .contains(mappings)) {
                throw new QuiltMetadataException("unknown intermediate mappings '" + mappings + "'");
            }
        }
        if (mappings.equals("org.quiltmc:hashed")) {
            throw new QuiltMetadataException("intermediate_mappings is " + (value == null ? "absent (so " : "")
                    + "org.quiltmc:hashed" + (value == null ? ")" : "")
                    + ", which Quilt Loader does not support (\"Oh no! This version of Quilt Loader doesn't support"
                    + " hashed mappings\")");
        }
        if (mappings.equals("org.quiltmc.experimental:mojang")) {
            throw new QuiltMetadataException("intermediate_mappings is org.quiltmc.experimental:mojang, which needs a"
                    + " 'mojang' mapping namespace; Minecraft 26.x runs with the 'official' names only");
        }
        findings.add(new CompatibilityFinding(
                "POLY-COMPAT-309",
                false,
                "intermediate_mappings",
                mappings + " has no effect: Minecraft 26.x is not obfuscated and nothing is remapped"));
        return mappings;
    }

    private static void requiredFeatures(JsonElement value, List<CompatibilityFinding> findings)
            throws QuiltMetadataException {
        if (value == null) {
            return;
        }
        if (!(value instanceof JsonArray array)) {
            throw new QuiltMetadataException("required_features must be an array: Quilt Loader supports no feature"
                    + " other than 'modifiable_filesystem_overlay', which has no options");
        }
        boolean overlay = false;
        for (JsonElement feature : array) {
            if (!isString(feature)) {
                throw new QuiltMetadataException("required_features must either be an array of strings or an object!");
            }
            switch (feature.getAsString()) {
                case "quilt_loader:modifiable_filesystem_overlay", "modifiable_filesystem_overlay" -> overlay = true;
                default ->
                    throw new QuiltMetadataException("required feature '" + feature.getAsString()
                            + "' is unknown; Quilt Loader supports only 'modifiable_filesystem_overlay'");
            }
        }
        if (overlay) {
            findings.add(new CompatibilityFinding(
                    "POLY-COMPAT-306",
                    true,
                    "required_features",
                    "needs Quilt's modifiable file system overlay (modifiable_filesystem_overlay); Poly Mod Loader"
                            + " does not provide it"));
        }
    }

    private static void readContributors(JsonObject people, Map<String, List<String>> contributors)
            throws QuiltMetadataException {
        for (Map.Entry<String, JsonElement> entry : people.entrySet()) {
            List<String> roles = new ArrayList<>();
            if (isString(entry.getValue())) {
                roles.add(entry.getValue().getAsString());
            } else if (entry.getValue() instanceof JsonArray array) {
                readStringList(array, entry.getKey(), roles);
            } else {
                throw new QuiltMetadataException("entry with key " + entry.getKey()
                        + " inside \"contributors\" must be a string or array of strings");
            }
            contributors.put(entry.getKey(), roles);
        }
    }

    private static void readLicenses(JsonElement value, List<ModLicense> licenses) throws QuiltMetadataException {
        if (value == null) {
            return;
        }
        if (value instanceof JsonArray array) {
            for (JsonElement license : array) {
                licenses.add(license(license));
            }
        } else if (value instanceof JsonObject || isString(value)) {
            licenses.add(license(value));
        } else {
            throw new QuiltMetadataException("license field must be a string, an array or an object");
        }
    }

    private static ModLicense license(JsonElement value) throws QuiltMetadataException {
        if (value instanceof JsonObject object) {
            return new QuiltModDescription.License(
                    requiredString(object, "name"),
                    requiredString(object, "id"),
                    requiredString(object, "url"),
                    optionalString(object, "description"));
        }
        if (isString(value)) {
            ModLicense license = ModLicense.fromIdentifier(value.getAsString());
            if (license == null) {
                throw new QuiltMetadataException("A license declared as a string id must be a valid SPDX identifier: '"
                        + value.getAsString() + "'");
            }
            return license;
        }
        throw new QuiltMetadataException("License entry must be an object or string: " + value);
    }

    /** A text, or an object whose keys are integers and values texts; anything else is ignored, as by Quilt Loader. */
    private static JsonElement icon(JsonElement value) throws QuiltMetadataException {
        if (isString(value)) {
            return value;
        }
        if (value instanceof JsonObject object) {
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                try {
                    Integer.parseInt(entry.getKey());
                } catch (NumberFormatException e) {
                    throw new QuiltMetadataException("Key " + entry.getKey() + " of icon must be an integer");
                }
                if (!isString(entry.getValue())) {
                    throw new QuiltMetadataException(
                            "entry with key " + entry.getKey() + " inside \"icon\" must be a string");
                }
            }
            return object;
        }
        return null;
    }

    private static String environment(JsonElement value) throws QuiltMetadataException {
        if (value == null) {
            return "*";
        }
        if (!(value instanceof JsonObject minecraft)) {
            throw new QuiltMetadataException("minecraft must be an object");
        }
        String environment = optionalString(minecraft, "environment");
        return switch (environment) {
            case "client", "dedicated_server" -> environment;
            case "", "*" -> "*";
            default ->
                throw new QuiltMetadataException(environment
                        + " is not a valid environment. Valid options are \"*\", \"client\", or \"dedicated_server\"");
        };
    }

    private static JsonArray array(JsonObject object, String field) throws QuiltMetadataException {
        JsonElement value = object.get(field);
        if (value == null) {
            return null;
        }
        if (!(value instanceof JsonArray array)) {
            throw new QuiltMetadataException(field + " must be of type ARRAY");
        }
        return array;
    }

    private static String requiredString(JsonObject object, String field) throws QuiltMetadataException {
        JsonElement value = object.get(field);
        if (value == null) {
            throw new QuiltMetadataException(field + " is a required field");
        }
        if (!isString(value)) {
            throw new QuiltMetadataException(field + " must be a string");
        }
        return value.getAsString();
    }

    private static String optionalString(JsonObject object, String field) throws QuiltMetadataException {
        JsonElement value = object.get(field);
        if (value == null) {
            return "";
        }
        if (!isString(value)) {
            throw new QuiltMetadataException(field + " must be a string");
        }
        return value.getAsString();
    }

    private static void readStringList(JsonArray array, String inside, List<String> destination)
            throws QuiltMetadataException {
        for (JsonElement value : array) {
            if (!isString(value)) {
                throw new QuiltMetadataException("Entry inside " + inside + " must be a string: " + value);
            }
            destination.add(value.getAsString());
        }
    }

    private static void readStringMap(JsonObject object, String inside, Map<String, String> destination)
            throws QuiltMetadataException {
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (!isString(entry.getValue())) {
                throw new QuiltMetadataException(
                        "entry with key " + entry.getKey() + " inside \"" + inside + "\" must be a string");
            }
            destination.put(entry.getKey(), entry.getValue().getAsString());
        }
    }

    /** {@code quilt_loader.<field>} as a text if the file got that far, for naming a refused mod. */
    private static String rawText(JsonObject root, String field) {
        if (root != null
                && root.get("quilt_loader") instanceof JsonObject quiltLoader
                && isString(quiltLoader.get(field))) {
            return quiltLoader.get(field).getAsString();
        }
        return "?";
    }

    private static boolean isString(JsonElement element) {
        return element != null
                && element.isJsonPrimitive()
                && element.getAsJsonPrimitive().isString();
    }
}
